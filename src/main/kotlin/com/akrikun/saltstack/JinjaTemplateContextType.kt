package com.akrikun.saltstack

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

class JinjaTemplateContextType : TemplateContextType("SaltStack Jinja") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        return context.file.language == JinjaLanguage || context.file.language == SlsLanguage
    }
}
