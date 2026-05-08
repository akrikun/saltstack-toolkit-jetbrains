package com.akrikun.saltstack

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object SlsFileType : LanguageFileType(SlsLanguage) {
    override fun getName(): String = "SLS"
    override fun getDescription(): String = "SaltStack SLS file"
    override fun getDefaultExtension(): String = "sls"
    override fun getIcon(): Icon = SaltIcons.SLS_FILE
}
