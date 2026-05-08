package com.akrikun.saltstack

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

class SlsTemplateContextType : TemplateContextType("SaltStack SLS") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        return context.file.language == SlsLanguage
    }
}
