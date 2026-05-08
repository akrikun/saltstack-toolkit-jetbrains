package com.akrikun.saltstack

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object JinjaFileType : LanguageFileType(JinjaLanguage) {
    override fun getName(): String = "Jinja"
    override fun getDescription(): String = "Jinja2 template file"
    override fun getDefaultExtension(): String = "jinja"
    override fun getIcon(): Icon = SaltIcons.JINJA_FILE
}
