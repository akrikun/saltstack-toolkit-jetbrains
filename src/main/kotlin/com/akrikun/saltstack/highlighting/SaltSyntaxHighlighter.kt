package com.akrikun.saltstack.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.akrikun.saltstack.lexer.SaltLexer
import com.akrikun.saltstack.lexer.SaltTokenTypes

class SaltSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        val COMMENT = TextAttributesKey.createTextAttributesKey(
            "SALT_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT,
        )
        val STRING = TextAttributesKey.createTextAttributesKey(
            "SALT_STRING", DefaultLanguageHighlighterColors.STRING,
        )
        val NUMBER = TextAttributesKey.createTextAttributesKey(
            "SALT_NUMBER", DefaultLanguageHighlighterColors.NUMBER,
        )
        val YAML_KEY = TextAttributesKey.createTextAttributesKey(
            "SALT_YAML_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD,
        )
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "SALT_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER,
        )
        val JINJA_TAG = TextAttributesKey.createTextAttributesKey(
            "SALT_JINJA_TAG", DefaultLanguageHighlighterColors.METADATA,
        )
        val JINJA_EXPR = TextAttributesKey.createTextAttributesKey(
            "SALT_JINJA_EXPR", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR,
        )
        val JINJA_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "SALT_JINJA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD,
        )
        val JINJA_COMMENT = TextAttributesKey.createTextAttributesKey(
            "SALT_JINJA_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT,
        )
        val PUNCTUATION = TextAttributesKey.createTextAttributesKey(
            "SALT_PUNCTUATION", DefaultLanguageHighlighterColors.OPERATION_SIGN,
        )
        val BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
            "SALT_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER,
        )

        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val YAML_KEY_KEYS = arrayOf(YAML_KEY)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val JINJA_TAG_KEYS = arrayOf(JINJA_TAG)
        private val JINJA_EXPR_KEYS = arrayOf(JINJA_EXPR)
        private val JINJA_KEYWORD_KEYS = arrayOf(JINJA_KEYWORD)
        private val JINJA_COMMENT_KEYS = arrayOf(JINJA_COMMENT)
        private val PUNCTUATION_KEYS = arrayOf(PUNCTUATION)
        private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = SaltLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        SaltTokenTypes.COMMENT -> COMMENT_KEYS
        SaltTokenTypes.STRING -> STRING_KEYS
        SaltTokenTypes.NUMBER -> NUMBER_KEYS
        SaltTokenTypes.YAML_KEY -> YAML_KEY_KEYS
        SaltTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
        SaltTokenTypes.JINJA_TAG_START,
        SaltTokenTypes.JINJA_TAG_END -> JINJA_TAG_KEYS
        SaltTokenTypes.JINJA_EXPR_START,
        SaltTokenTypes.JINJA_EXPR_END -> JINJA_EXPR_KEYS
        SaltTokenTypes.JINJA_COMMENT_START,
        SaltTokenTypes.JINJA_COMMENT_END,
        SaltTokenTypes.JINJA_COMMENT -> JINJA_COMMENT_KEYS
        SaltTokenTypes.JINJA_KEYWORD -> JINJA_KEYWORD_KEYS
        SaltTokenTypes.COLON,
        SaltTokenTypes.DASH,
        SaltTokenTypes.PIPE -> PUNCTUATION_KEYS
        SaltTokenTypes.BAD_CHARACTER -> BAD_CHAR_KEYS
        else -> EMPTY_KEYS
    }
}
