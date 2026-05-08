package com.akrikun.saltstack.formatting

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.akrikun.saltstack.JinjaLanguage
import com.akrikun.saltstack.SaltSettings
import com.akrikun.saltstack.SlsLanguage

class FormatSaltAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        if (psiFile.language != SlsLanguage && psiFile.language != JinjaLanguage) return

        val doc = editor.document
        val original = doc.text
        val formatted = SaltFormatter.format(original, SaltSettings.getInstance().enforceDashTags)
        if (original == formatted) return

        WriteCommandAction.runWriteCommandAction(project) {
            doc.setText(formatted)
        }
    }

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = psiFile?.language == SlsLanguage || psiFile?.language == JinjaLanguage
    }
}
