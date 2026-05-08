package com.akrikun.saltstack

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class SaltPsiFile(viewProvider: FileViewProvider, language: Language) :
    PsiFileBase(viewProvider, language) {

    override fun getFileType(): FileType {
        return when (language) {
            JinjaLanguage -> JinjaFileType
            else -> SlsFileType
        }
    }

    override fun toString(): String = "SaltStack File"
}
