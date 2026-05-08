package com.akrikun.saltstack.formatting

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.akrikun.saltstack.JinjaLanguage
import com.akrikun.saltstack.SaltSettings
import com.akrikun.saltstack.SlsLanguage

class SaltSaveListener : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        val settings = SaltSettings.getInstance()
        if (!settings.formatOnSave) return

        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val ext = virtualFile.extension?.lowercase() ?: return
        if (ext != "sls" && ext != "jinja" && ext != "j2" && ext != "jinja2") return

        // Quick check by filename — verify language via PsiFile if project available
        val project = ProjectManager.getInstance().openProjects.firstOrNull { p ->
            PsiDocumentManager.getInstance(p).getPsiFile(document) != null
        } ?: return

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
        if (psiFile.language != SlsLanguage && psiFile.language != JinjaLanguage) return

        val original = document.text
        val formatted = SaltFormatter.format(original, settings.enforceDashTags)
        if (original == formatted) return

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(formatted)
            }
        }
    }
}
