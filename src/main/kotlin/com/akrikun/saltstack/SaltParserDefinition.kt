package com.akrikun.saltstack

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.akrikun.saltstack.lexer.SaltLexer
import com.akrikun.saltstack.lexer.SaltTokenTypes

/**
 * Base parser definition. Subclasses bind it to a specific language so that
 * the IFileElementType matches the language of the resulting PsiFile.
 */
abstract class SaltParserDefinitionBase(
    private val language: Language,
    private val fileNodeType: IFileElementType,
) : ParserDefinition {

    companion object {
        val WHITE_SPACES = TokenSet.create(SaltTokenTypes.WHITE_SPACE, SaltTokenTypes.NEWLINE)
        val COMMENTS = TokenSet.create(SaltTokenTypes.COMMENT, SaltTokenTypes.JINJA_COMMENT)
        val STRING_LITERALS = TokenSet.create(SaltTokenTypes.STRING)
    }

    override fun createLexer(project: Project?): Lexer = SaltLexer()
    override fun getFileNodeType(): IFileElementType = fileNodeType
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES
    override fun getStringLiteralElements(): TokenSet = STRING_LITERALS

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val rootMarker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        rootMarker.done(root)
        builder.treeBuilt
    }

    override fun createElement(node: ASTNode): PsiElement = throw UnsupportedOperationException()
    override fun createFile(viewProvider: FileViewProvider): PsiFile = SaltPsiFile(viewProvider, language)
}

class SlsParserDefinition : SaltParserDefinitionBase(SlsLanguage, SLS_FILE_TYPE) {
    companion object { val NAME = "SLS_FILE" }
}

class JinjaParserDefinition : SaltParserDefinitionBase(JinjaLanguage, JINJA_FILE_TYPE) {
    companion object { val NAME = "JINJA_FILE" }
}

val SLS_FILE_TYPE = IFileElementType("SLS_FILE", SlsLanguage)
val JINJA_FILE_TYPE = IFileElementType("JINJA_FILE", JinjaLanguage)
