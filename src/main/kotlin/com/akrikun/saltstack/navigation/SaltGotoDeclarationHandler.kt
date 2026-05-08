package com.akrikun.saltstack.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.akrikun.saltstack.JinjaLanguage
import com.akrikun.saltstack.SlsLanguage
import java.io.File

/**
 * Handles Go-to-Definition (Ctrl/Cmd+Click) for:
 *  - Jinja imports: {% from "formula/map.jinja" import ... %}
 *  - {% include "..." %}
 *  - salt:// source references
 *  - SLS includes (- formula.substate)
 *  - Pillar includes
 *  - Requisite references (- file: state_id  →  state_id within current file)
 */
class SaltGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null || editor == null) return null
        val file = sourceElement.containingFile ?: return null
        val lang = file.language
        if (lang != SlsLanguage && lang != JinjaLanguage) return null

        val project = sourceElement.project
        val doc = editor.document
        val lineNum = doc.getLineNumber(offset)
        val lineText = doc.getText(TextRange(doc.getLineStartOffset(lineNum), doc.getLineEndOffset(lineNum)))
        val colInLine = offset - doc.getLineStartOffset(lineNum)

        // Try each pattern in turn
        resolveJinjaFromImport(lineText, colInLine, project, file)?.let { return arrayOf(it) }
        resolveJinjaInclude(lineText, colInLine, project, file)?.let { return arrayOf(it) }
        resolveSaltSource(lineText, colInLine, project, file)?.let { return arrayOf(it) }
        resolveSlsInclude(lineText, colInLine, project, file)?.let { return arrayOf(it) }
        resolvePillarInclude(lineText, colInLine, project, file)?.let { return arrayOf(it) }
        resolveRequisiteRef(lineText, colInLine, doc, file, project)?.let { return arrayOf(it) }

        // In pillar files: clicking on a key — find usages in state files (path-aware, with aliases & imports)
        if (com.akrikun.saltstack.PillarContext.isPillarFile(file)) {
            val keyPath = com.akrikun.saltstack.PillarContext.getPillarKeyPath(doc, offset)
            if (keyPath != null && keyPath.isNotEmpty()) {
                val cache = PillarUsageCache.getInstance(project)
                val results = cache.findUsages(keyPath)
                if (results.isNotEmpty()) {
                    val matchedText = keyPath.last()
                    val targets = mutableListOf<PsiElement>()
                    for ((f, off) in results) {
                        val vf = LocalFileSystem.getInstance().findFileByIoFile(f) ?: continue
                        val psi = vf.toPsi(project) as? com.intellij.psi.PsiFile ?: continue
                        targets.add(PillarUsageTarget(psi, off, matchedText))
                    }
                    if (targets.isNotEmpty()) return targets.toTypedArray()
                }
            }
        }

        return null
    }

    // ===== resolvers =====

    private fun resolveJinjaFromImport(line: String, col: Int, project: Project, currentFile: com.intellij.psi.PsiFile): PsiElement? {
        // {% from "path/to/file.jinja" import name %}
        val m = Regex("\\{%[-\\s]*from\\s+[\"']([^\"']+)[\"']").find(line) ?: return null
        val pathRange = m.groups[1]?.range ?: return null
        if (col !in pathRange.first..pathRange.last + 1) return null
        return findInRoots(m.groupValues[1], project, currentFile, "salt") { _, _ -> true }
    }

    private fun resolveJinjaInclude(line: String, col: Int, project: Project, currentFile: com.intellij.psi.PsiFile): PsiElement? {
        // {% include "path/to/file" %}
        val m = Regex("\\{%[-\\s]*include\\s+[\"']([^\"']+)[\"']").find(line) ?: return null
        val pathRange = m.groups[1]?.range ?: return null
        if (col !in pathRange.first..pathRange.last + 1) return null
        return findInRoots(m.groupValues[1], project, currentFile, "salt-or-pillar") { _, _ -> true }
    }

    private fun resolveSaltSource(line: String, col: Int, project: Project, currentFile: com.intellij.psi.PsiFile): PsiElement? {
        val target = extractSaltUri(line, col) ?: return null
        return findInRoots(target, project, currentFile, "salt") { _, _ -> true }
    }

    private fun resolveSlsInclude(line: String, col: Int, project: Project, currentFile: com.intellij.psi.PsiFile): PsiElement? {
        // - .substate or - formula.substate (in include: block)
        val m = Regex("^\\s+-\\s+(\\.?[\\w.]+)\\s*$").matchEntire(line) ?: return null
        val ref = m.groupValues[1]
        val refRange = m.groups[1]?.range ?: return null
        if (col !in refRange.first..refRange.last + 1) return null

        // Resolve relative to current file dir
        val currentDir = currentFile.virtualFile?.parent ?: return null
        val target = if (ref.startsWith(".")) {
            // ".substate" -> current_dir/substate.sls
            val name = ref.substring(1).replace(".", "/") + ".sls"
            currentDir.findFileByRelativePath(name)
                ?: currentDir.findFileByRelativePath(ref.substring(1).replace(".", "/") + "/init.sls")
        } else {
            // "formula.substate" -> resolve from salt root
            findInRoots(ref.replace(".", "/") + ".sls", project, currentFile, "salt") { _, _ -> true }
                ?.let { return it }
            findInRoots(ref.replace(".", "/") + "/init.sls", project, currentFile, "salt") { _, _ -> true }
        }
        return (target as? PsiElement) ?: target?.let { (it as? VirtualFile)?.toPsi(project) }
    }

    private fun resolvePillarInclude(line: String, col: Int, project: Project, currentFile: com.intellij.psi.PsiFile): PsiElement? {
        // pillar include block: similar to SLS includes but resolved from pillar roots
        // Heuristic: only fire if file path contains /pillar/
        val path = currentFile.virtualFile?.path ?: return null
        if (!path.contains("/pillar/") && !path.contains("/pillars/")) return null

        val m = Regex("^\\s+-\\s+([\\w./\\-]+)\\s*$").matchEntire(line) ?: return null
        val ref = m.groupValues[1]
        val refRange = m.groups[1]?.range ?: return null
        if (col !in refRange.first..refRange.last + 1) return null

        return findInRoots("$ref.sls", project, currentFile, "pillar") { _, _ -> true }
            ?: findInRoots("$ref/init.sls", project, currentFile, "pillar") { _, _ -> true }
    }

    private fun resolveRequisiteRef(line: String, col: Int, doc: com.intellij.openapi.editor.Document, currentFile: com.intellij.psi.PsiFile, project: Project): PsiElement? {
        // Inside requisite block: "- file: state_id" or just "- state_id"
        val m1 = Regex("^\\s+-\\s+(?:[\\w]+:\\s+)?([\\w][\\w.\\-/]*)\\s*$").matchEntire(line) ?: return null
        val refRange = m1.groups[1]?.range ?: return null
        if (col !in refRange.first..refRange.last + 1) return null

        val ref = m1.groupValues[1]
        // Search current file for matching state ID
        val text = doc.text
        val idRe = Regex("(?m)^([a-zA-Z_][\\w.\\-/() ]*):$")
        for (match in idRe.findAll(text)) {
            if (match.groupValues[1] == ref) {
                return currentFile.findElementAt(match.range.first)
            }
        }
        return null
    }

    // ===== helpers =====

    private fun findInRoots(
        relPath: String,
        project: Project,
        currentFile: com.intellij.psi.PsiFile,
        rootKind: String, // "salt", "pillar", "salt-or-pillar"
        @Suppress("UNUSED_PARAMETER") matcher: (VirtualFile, String) -> Boolean,
    ): PsiElement? {
        val settings = com.akrikun.saltstack.SaltSettings.getInstance()
        val roots = mutableListOf<String>()
        when (rootKind) {
            "salt" -> roots += settings.stateRoots
            "pillar" -> roots += settings.pillarRoots
            "salt-or-pillar" -> {
                roots += settings.stateRoots
                roots += settings.pillarRoots
            }
        }

        // Iterate over project base directories (workspace folders)
        val basePaths = mutableSetOf<String>()
        ProjectFileIndex.getInstance(project).iterateContent { vf ->
            if (vf.isDirectory && vf.parent == null) basePaths.add(vf.path)
            true
        }
        // Fallback: project base dir
        project.basePath?.let { basePaths.add(it) }

        // For absolute roots: probe directly. For relative roots: probe under each base.
        for (root in roots) {
            if (File(root).isAbsolute) {
                val candidate = File(root, relPath)
                if (candidate.isFile) {
                    val vf = LocalFileSystem.getInstance().findFileByIoFile(candidate) ?: continue
                    return vf.toPsi(project)
                }
            } else {
                for (base in basePaths) {
                    val candidate = File(File(base, root), relPath)
                    if (candidate.isFile) {
                        val vf = LocalFileSystem.getInstance().findFileByIoFile(candidate) ?: continue
                        return vf.toPsi(project)
                    }
                }
            }
        }

        // Fallback: try relative to current file's directory
        val currentDir = currentFile.virtualFile?.parent?.path
        if (currentDir != null) {
            val candidate = File(currentDir, relPath)
            if (candidate.isFile) {
                val vf = LocalFileSystem.getInstance().findFileByIoFile(candidate)
                if (vf != null) return vf.toPsi(project)
            }
        }
        return null
    }

    private fun VirtualFile.toPsi(project: Project): PsiElement? = PsiManager.getInstance(project).findFile(this)

    override fun getActionText(context: DataContext): String? = null
}
