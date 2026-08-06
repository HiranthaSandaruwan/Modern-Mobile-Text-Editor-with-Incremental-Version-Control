package com.example.texteditor.editor

import android.graphics.Color
import android.text.Editable
import java.util.regex.Pattern

/**
 * Syntax highlighting for Markdown.
 */
class MarkdownHighlighter : SyntaxHighlighter() {

    private val headingPattern = Pattern.compile("^#{1,6}\\s.*$", Pattern.MULTILINE)
    private val boldPattern = Pattern.compile("\\*\\*[^*\\n]+\\*\\*")
    private val italicPattern = Pattern.compile("(?<!\\*)\\*[^*\\n]+\\*(?!\\*)")
    private val inlineCodePattern = Pattern.compile("`[^`\\n]+`")
    private val linkPattern = Pattern.compile("\\[[^\\]\\n]*\\]\\([^)\\n]*\\)")
    private val bulletPattern = Pattern.compile("^\\s*([-*+]|\\d+\\.)\\s", Pattern.MULTILINE)
    private val quotePattern = Pattern.compile("^>.*$", Pattern.MULTILINE)

    override fun applyPatterns(text: Editable) {
        colorMatches(text, bulletPattern, BULLET_COLOR, bold = true)
        colorMatches(text, boldPattern, BOLD_COLOR, bold = true)
        colorMatches(text, italicPattern, ITALIC_COLOR, italic = true)
        colorMatches(text, linkPattern, LINK_COLOR)
        colorMatches(text, inlineCodePattern, CODE_COLOR)
        colorMatches(text, quotePattern, QUOTE_COLOR, italic = true)
        colorMatches(text, headingPattern, HEADING_COLOR, bold = true)
    }

    companion object {
        private val HEADING_COLOR = Color.parseColor("#0033B3")
        private val BOLD_COLOR = Color.parseColor("#212121")
        private val ITALIC_COLOR = Color.parseColor("#424242")
        private val CODE_COLOR = Color.parseColor("#00796B")
        private val LINK_COLOR = Color.parseColor("#1565C0")
        private val BULLET_COLOR = Color.parseColor("#E65100")
        private val QUOTE_COLOR = Color.parseColor("#616161")
    }
}
