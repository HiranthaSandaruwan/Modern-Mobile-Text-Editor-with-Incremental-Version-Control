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
    private val stringPattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(\\\\.|[^\"\\\\\\n])*\"|'(\\\\.|[^'\\\\\\n])*'")
    private val commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")

    override fun applyPatterns(text: Editable) {
        colorMatches(text, keywordPattern, KEYWORD_COLOR, bold = true)
        colorMatches(text, numberPattern, NUMBER_COLOR)
        colorMatches(text, annotationPattern, ANNOTATION_COLOR)
        colorMatches(text, stringPattern, STRING_COLOR)
        colorMatches(text, commentPattern, COMMENT_COLOR, italic = true)
    }

    companion object {
        private val KEYWORD_COLOR = Color.parseColor("#0033B3")
        private val NUMBER_COLOR = Color.parseColor("#1750EB")
        private val ANNOTATION_COLOR = Color.parseColor("#9E880D")
        private val STRING_COLOR = Color.parseColor("#067D17")
        private val COMMENT_COLOR = Color.parseColor("#8C8C8C")
    }
}
