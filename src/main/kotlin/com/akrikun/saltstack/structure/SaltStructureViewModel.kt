package com.akrikun.saltstack.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.akrikun.saltstack.SaltIcons
import javax.swing.Icon

class SaltStructureViewModel(file: PsiFile, editor: Editor?) :
    StructureViewModelBase(file, editor, SaltStructureViewElement(file, NodeKind.FILE, file.name, 0)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(PsiFile::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = false
}

enum class NodeKind { FILE, STATE_ID, STATE_MODULE, INCLUDE, JINJA_IMPORT, JINJA_VAR }

class SaltStructureViewElement(
    private val file: PsiFile,
    private val kind: NodeKind,
    private val displayName: String,
    private val offset: Int,
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = file

    override fun navigate(requestFocus: Boolean) {
        val nav = file as? Navigatable ?: return
        if (offset > 0) {
            (file.findElementAt(offset) as? Navigatable)?.navigate(requestFocus)
        } else {
            nav.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean = file.canNavigate()
    override fun canNavigateToSource(): Boolean = file.canNavigateToSource()
    override fun getAlphaSortKey(): String = displayName

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = displayName
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon = when (kind) {
            NodeKind.STATE_ID -> SaltIcons.SLS_FILE
            NodeKind.STATE_MODULE -> SaltIcons.SLS_FILE
            NodeKind.INCLUDE -> SaltIcons.SLS_FILE
            NodeKind.JINJA_IMPORT -> SaltIcons.JINJA_FILE
            NodeKind.JINJA_VAR -> SaltIcons.JINJA_FILE
            NodeKind.FILE -> SaltIcons.SLS_FILE
        }
    }

    override fun getChildren(): Array<TreeElement> {
        if (kind != NodeKind.FILE) return emptyArray()
        return parseFile(file)
    }

    private fun parseFile(file: PsiFile): Array<TreeElement> {
        val text = file.text
        val children = mutableListOf<TreeElement>()
        val lines = text.split("\n")
        var off = 0

        val stateIdRe = Regex("^([a-zA-Z_][\\w.\\-/() ]*):$")
        val stateModuleRe = Regex("^(\\s+)([\\w]+\\.[\\w]+):\\s*$")
        val includeEntryRe = Regex("^\\s+-\\s+(\\.?[\\w./\\-]+)\\s*$")
        val jinjaImportRe = Regex("\\{%[-\\s]*from\\s+[\"']([^\"']+)[\"']\\s+import\\s+([\\w,\\s]+)\\s+with\\s+context")
        val jinjaSetRe = Regex("\\{%-?\\s*set\\s+([\\w]+)\\s*=")

        var inInclude = false
        var pendingState: SaltStructureViewElement? = null

        for (line in lines) {
            // Top-level state ID
            stateIdRe.matchEntire(line)?.let { m ->
                val id = m.groupValues[1]
                if (id == "include") {
                    inInclude = true
                } else {
                    inInclude = false
                    pendingState?.let { children.add(it) }
                    pendingState = SaltStructureViewElement(file, NodeKind.STATE_ID, id, off)
                }
            }
            if (inInclude) {
                includeEntryRe.matchEntire(line)?.let { m ->
                    children.add(SaltStructureViewElement(file, NodeKind.INCLUDE, m.groupValues[1], off))
                }
            }
            stateModuleRe.matchEntire(line)?.let { /* ignore for now, would be nested */ }

            // Jinja imports
            jinjaImportRe.find(line)?.let { m ->
                children.add(SaltStructureViewElement(
                    file, NodeKind.JINJA_IMPORT,
                    "${m.groupValues[1]} → ${m.groupValues[2].trim()}", off,
                ))
            }
            // Jinja set vars
            jinjaSetRe.find(line)?.let { m ->
                children.add(SaltStructureViewElement(file, NodeKind.JINJA_VAR, m.groupValues[1], off))
            }

            off += line.length + 1
        }
        pendingState?.let { children.add(it) }
        return children.toTypedArray()
    }
}
