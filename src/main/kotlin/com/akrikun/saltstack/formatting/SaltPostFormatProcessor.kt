package com.akrikun.saltstack.formatting

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.akrikun.saltstack.JinjaLanguage
import com.akrikun.saltstack.SaltSettings
import com.akrikun.saltstack.SlsLanguage

class SaltPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        val lang = source.language
        if (lang != SlsLanguage && lang != JinjaLanguage) return rangeToReformat

        val saltSettings = SaltSettings.getInstance()
        val text = source.text
        val formatted = SaltFormatter.format(text, saltSettings.enforceDashTags)

        if (formatted != text) {
            val doc = source.viewProvider.document ?: return rangeToReformat
            doc.setText(formatted)
            return TextRange(0, formatted.length)
        }
        return rangeToReformat
    }
}
