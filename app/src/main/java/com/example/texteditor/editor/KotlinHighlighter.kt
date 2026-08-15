package com.example.texteditor.editor

import android.content.Context
import android.text.Editable
import androidx.core.graphics.toColorInt
import java.util.regex.Pattern

// Colors Kotlin code: keywords, numbers, annotations, strings, and comments.
// Keywords are loaded from assets/kotlin_keywords.txt.
class KotlinHighlighter(context: Context) : SyntaxHighlighter() {

    private val keywords: List<String> =
        context.assets.open("kotlin_keywords.txt").bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private val keywordPattern = Pattern.compile("\\b(" + keywords.joinToString("|") + ")\\b")
    private val numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[fFL]?\\b")
    private val annotationPattern = Pattern.compile("@\\w+")

    // Strings and comments share one pattern so a "//" inside a string (like a URL) isn't
    // later mistaken for a comment.
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

    // Colors each match as a comment or a string, depending on which it is.
    private fun colorStringsAndComments(text: Editable) {
        val matcher = stringOrCommentPattern.matcher(text)
        while (matcher.find()) {
            val matched = matcher.group()
            val isComment = matched.startsWith("//") || matched.startsWith("/*")
            colorRange(text, matcher.start(), matcher.end(), if (isComment) COMMENT_COLOR else STRING_COLOR, italic = isComment)
        }
    }

    companion object {
        private val KEYWORD_COLOR = "#0033B3".toColorInt()
        private val NUMBER_COLOR = "#1750EB".toColorInt()
        private val ANNOTATION_COLOR = "#9E880D".toColorInt()
        private val STRING_COLOR = "#067D17".toColorInt()
        private val COMMENT_COLOR = "#8C8C8C".toColorInt()
    }
}
