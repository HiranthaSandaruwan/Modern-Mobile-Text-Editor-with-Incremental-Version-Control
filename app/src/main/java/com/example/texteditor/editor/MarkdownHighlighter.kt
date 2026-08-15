package com.example.texteditor.editor

import android.text.Editable
import androidx.core.graphics.toColorInt
import java.util.regex.Pattern

// Colors Markdown text: headings, bold, italic, links, code, quotes, and bullets.
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
        private val HEADING_COLOR = "#0033B3".toColorInt()
        private val BOLD_COLOR = "#212121".toColorInt()
        private val ITALIC_COLOR = "#424242".toColorInt()
        private val CODE_COLOR = "#00796B".toColorInt()
        private val LINK_COLOR = "#1565C0".toColorInt()
        private val BULLET_COLOR = "#E65100".toColorInt()
        private val QUOTE_COLOR = "#616161".toColorInt()
    }
}
