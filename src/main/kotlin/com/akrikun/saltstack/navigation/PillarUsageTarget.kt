package com.akrikun.saltstack.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * Wraps a navigation target with proper presentation for IDE's "Choose Declaration" popup.
 * Shows: matched text + line number, with file path as location string.
 */
class PillarUsageTarget(
    private val file: PsiFile,
    private val offset: Int,
    private val matchedText: String,
) : FakePsiElement() {

    override fun getProject(): Project = file.project
    override fun getContainingFile(): PsiFile = file
    override fun getParent() = file
    override fun getName(): String = matchedText
    override fun getTextOffset(): Int = offset

    override fun navigate(requestFocus: Boolean) {
        val vf = file.virtualFile ?: return
        OpenFileDescriptor(file.project, vf, offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.virtualFile != null
    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String {
            val doc = file.viewProvider.document ?: return matchedText
            val lineNum = doc.getLineNumber(offset)
            val lineStart = doc.getLineStartOffset(lineNum)
            val lineEnd = doc.getLineEndOffset(lineNum)
            val lineText = doc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd)).trim()
            return if (lineText.length > 120) lineText.substring(0, 117) + "..." else lineText
        }

        override fun getLocationString(): String {
            val vf = file.virtualFile ?: return ""
            val basePath = file.project.basePath
            val relPath = if (basePath != null && vf.path.startsWith("$basePath/")) {
                vf.path.removePrefix("$basePath/")
            } else {
                vf.path
            }
            val doc = file.viewProvider.document
            val lineNum = if (doc != null) doc.getLineNumber(offset) + 1 else 0
            return "$relPath:$lineNum"
        }

        override fun getIcon(unused: Boolean): Icon? = file.fileType.icon
    }
}
