package com.example.texteditor.editor

import android.content.Context
import android.graphics.Color
import android.text.Editable
import java.util.regex.Pattern

/**
 * Syntax highlighting for Kotlin source files (.kt).
 *
 * The keyword list is NOT hard-coded: it is loaded from the asset file
 * "assets/kotlin_keywords.txt" (one keyword per line), as suggested by the
 * assignment. The colors follow a typical light IDE color scheme.
 *
 * Order matters: strings and comments are applied LAST so that, for example,
 * the word "fun" inside a comment is shown in the comment color, because a
 * span added later wins over an earlier one for the same characters.
 */
class KotlinHighlighter(context: Context) : SyntaxHighlighter() {

    // Load keywords from the asset file, skipping blank lines and # comments.
    private val keywords: List<String> =
        context.assets.open("kotlin_keywords.txt").bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    // \b = word boundary, so "class" matches but "subclassed" does not.
    private val keywordPattern: Pattern =
        Pattern.compile("\\b(" + keywords.joinToString("|") + ")\\b")

    private val numberPattern: Pattern =
        Pattern.compile("\\b\\d+(\\.\\d+)?[fFL]?\\b")

    private val annotationPattern: Pattern =
        Pattern.compile("@\\w+")

    // Triple-quoted strings, normal "..." strings and '...' character literals.
    private val stringPattern: Pattern =
        Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(\\\\.|[^\"\\\\\\n])*\"|'(\\\\.|[^'\\\\\\n])*'")

    // Line comments (// ...) and block comments (/* ... */).
    private val commentPattern: Pattern =
        Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")

    override fun applyPatterns(text: Editable) {
        colorMatches(text, keywordPattern, KEYWORD_COLOR, bold = true)
        colorMatches(text, numberPattern, NUMBER_COLOR)
        colorMatches(text, annotationPattern, ANNOTATION_COLOR)
        colorMatches(text, stringPattern, STRING_COLOR)
        colorMatches(text, commentPattern, COMMENT_COLOR, italic = true)
    }

    companion object {
        private val KEYWORD_COLOR = Color.parseColor("#0033B3")    // blue
        private val NUMBER_COLOR = Color.parseColor("#1750EB")     // light blue
        private val ANNOTATION_COLOR = Color.parseColor("#9E880D") // mustard
        private val STRING_COLOR = Color.parseColor("#067D17")     // green
        private val COMMENT_COLOR = Color.parseColor("#8C8C8C")    // grey
    }
}
