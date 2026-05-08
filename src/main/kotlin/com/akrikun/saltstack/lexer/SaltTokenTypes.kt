package com.akrikun.saltstack.lexer

import com.intellij.psi.tree.IElementType
import com.akrikun.saltstack.SlsLanguage

class SaltTokenType(debugName: String) : IElementType(debugName, SlsLanguage)

object SaltTokenTypes {
    val WHITE_SPACE = SaltTokenType("WHITE_SPACE")
    val NEWLINE = SaltTokenType("NEWLINE")
    val COMMENT = SaltTokenType("COMMENT")
    val JINJA_TAG_START = SaltTokenType("JINJA_TAG_START")
    val JINJA_TAG_END = SaltTokenType("JINJA_TAG_END")
    val JINJA_EXPR_START = SaltTokenType("JINJA_EXPR_START")
    val JINJA_EXPR_END = SaltTokenType("JINJA_EXPR_END")
    val JINJA_COMMENT_START = SaltTokenType("JINJA_COMMENT_START")
    val JINJA_COMMENT_END = SaltTokenType("JINJA_COMMENT_END")
    val JINJA_KEYWORD = SaltTokenType("JINJA_KEYWORD")
    val JINJA_CONTENT = SaltTokenType("JINJA_CONTENT")
    val JINJA_COMMENT = SaltTokenType("JINJA_COMMENT")
    val STRING = SaltTokenType("STRING")
    val NUMBER = SaltTokenType("NUMBER")
    val YAML_KEY = SaltTokenType("YAML_KEY")
    val COLON = SaltTokenType("COLON")
    val DASH = SaltTokenType("DASH")
    val PIPE = SaltTokenType("PIPE")
    val IDENTIFIER = SaltTokenType("IDENTIFIER")
    val TEXT = SaltTokenType("TEXT")
    val BAD_CHARACTER = SaltTokenType("BAD_CHARACTER")
}
