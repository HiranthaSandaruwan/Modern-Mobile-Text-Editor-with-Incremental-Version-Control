package com.example.texteditor.editor

import android.content.Context
import android.graphics.Color
import android.text.Editable
import java.util.regex.Pattern

/**
 * Syntax highlighting for Kotlin.
 * Keywords are loaded from assets/kotlin_keywords.txt.
 */
class KotlinHighlighter(context: Context) : SyntaxHighlighter() {

    private val keywords: List<String> =
        context.assets.open("kotlin_keywords.txt").bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private val keywordPattern = Pattern.compile("\\b(" + keywords.joinToString("|") + ")\\b")
    private val numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[fFL]?\\b")
    private val annotationPattern = Pattern.compile("@\\w+")

    // Strings and comments are matched together, as ALTERNATIVES of one pattern, so the regex
    // engine claims each character range only once. This stops a "//" that occurs inside a
    // string literal (e.g. a URL like "https://example.com") from later being re-matched and
    // re-colored as a comment by a separate, context-blind comment pattern.
    private val stringOrCommentPattern = Pattern.compile(
        "\"\"\"[\\s\\S]*?\"\"\"" +               // triple-quoted strings
            "|\"(\\\\.|[^\"\\\\\\n])*\"" +       // double-quoted strings
            "|'(\\\\.|[^'\\\\\\n])*'" +          // char literals
            "|//.*" +                             // line comments
            "|/\\*[\\s\\S]*?\\*/"                 // block comments
    )

    override fun applyPatterns(text: Editable) {
        colorMatches(text, keywordPattern, KEYWORD_COLOR, bold = true)
        colorMatches(text, numberPattern, NUMBER_COLOR)
        colorMatches(text, annotationPattern, ANNOTATION_COLOR)
        colorStringsAndComments(text)
    }

    /** Colors each match of [stringOrCommentPattern] as a comment or a string, depending on which it is. */
    private fun colorStringsAndComments(text: Editable) {
        val matcher = stringOrCommentPattern.matcher(text)
        while (matcher.find()) {
            val matched = matcher.group()
            val isComment = matched.startsWith("//") || matched.startsWith("/*")
            colorRange(text, matcher.start(), matcher.end(), if (isComment) COMMENT_COLOR else STRING_COLOR, italic = isComment)
        }
    }

    companion object {
        private val KEYWORD_COLOR = Color.parseColor("#0033B3")
        private val NUMBER_COLOR = Color.parseColor("#1750EB")
        private val ANNOTATION_COLOR = Color.parseColor("#9E880D")
        private val STRING_COLOR = Color.parseColor("#067D17")
        private val COMMENT_COLOR = Color.parseColor("#8C8C8C")
    }
}
