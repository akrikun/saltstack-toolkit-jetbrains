package com.akrikun.saltstack

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.io.File

object PillarContext {

    fun isPillarFile(file: PsiFile): Boolean {
        val path = file.virtualFile?.path ?: return false
        val pillarRoots = SaltSettings.getInstance().pillarRoots
        return pillarRoots.any { root ->
            if (File(root).isAbsolute) {
                path.startsWith(root + File.separator)
            } else {
                path.contains("/$root/") || path.contains("\\$root\\")
            }
        } || path.contains("/pillar/") || path.contains("/pillars/")
    }

    /**
     * Build YAML key path for the cursor position by walking up indentation.
     * E.g. for cursor on "site:" inside "netbox: > data: > site:" returns ["netbox", "data", "site"].
     * Returns null if cursor is not on a key.
     */
    fun getPillarKeyPath(document: Document, offset: Int): List<String>? {
        val lineNum = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNum)
        val lineEnd = document.getLineEndOffset(lineNum)
        val line = document.getText(TextRange(lineStart, lineEnd))
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length

        val keyMatch = Regex("^([\\w][\\w.\\-]*):").find(trimmed) ?: return null
        val keyName = keyMatch.groupValues[1]
        val keyStart = lineStart + indent
        val keyEnd = keyStart + keyName.length
        if (offset < keyStart || offset > keyEnd) return null

        val path = mutableListOf(keyName)
        var targetIndent = indent
        for (i in (lineNum - 1) downTo 0) {
            val lStart = document.getLineStartOffset(i)
            val lEnd = document.getLineEndOffset(i)
            val l = document.getText(TextRange(lStart, lEnd))
            if (l.isBlank()) continue
            val lTrim = l.trimStart()
            if (lTrim.startsWith("#") || lTrim.startsWith("{%") || lTrim.startsWith("{#")) continue
            val lIndent = l.length - lTrim.length
            if (lIndent >= targetIndent) continue
            val m = Regex("^([\\w][\\w.\\-]*):").find(lTrim) ?: continue
            path.add(0, m.groupValues[1])
            targetIndent = lIndent
            if (lIndent == 0) break
        }
        return path
    }
}
