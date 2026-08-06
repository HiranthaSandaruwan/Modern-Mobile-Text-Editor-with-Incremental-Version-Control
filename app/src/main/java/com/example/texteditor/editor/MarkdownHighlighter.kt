package com.example.texteditor.editor

import android.graphics.Color
import android.text.Editable
import java.util.regex.Pattern

/**
 * Syntax highlighting for Markdown files (.md) inside the editor.
 * (The rendered preview is a separate feature, handled by the Markwon
 * library in MainActivity.)
 *
 * Pattern.MULTILINE makes "^" match the start of every LINE,
 * not only the start of the whole text.
 */
class MarkdownHighlighter : SyntaxHighlighter() {

    // Headings: "# Title", "## Subtitle", ... up to 6 hashes.
    private val headingPattern: Pattern =
        Pattern.compile("^#{1,6}\\s.*$", Pattern.MULTILINE)

    // Bold: **text**
    private val boldPattern: Pattern =
        Pattern.compile("\\*\\*[^*\\n]+\\*\\*")

    // Italic: *text*  (but not part of a ** bold ** marker)
    private val italicPattern: Pattern =
        Pattern.compile("(?<!\\*)\\*[^*\\n]+\\*(?!\\*)")

    // Inline code: `code`
    private val inlineCodePattern: Pattern =
        Pattern.compile("`[^`\\n]+`")

    // Links: [text](url)
    private val linkPattern: Pattern =
        Pattern.compile("\\[[^\\]\\n]*\\]\\([^)\\n]*\\)")

    // List bullets: "- item", "* item", "+ item", "1. item"
    private val bulletPattern: Pattern =
        Pattern.compile("^\\s*([-*+]|\\d+\\.)\\s", Pattern.MULTILINE)

    // Quotes: "> quoted text"
    private val quotePattern: Pattern =
        Pattern.compile("^>.*$", Pattern.MULTILINE)

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
        private val HEADING_COLOR = Color.parseColor("#0033B3") // blue
        private val BOLD_COLOR = Color.parseColor("#212121")    // near black
        private val ITALIC_COLOR = Color.parseColor("#424242")  // dark grey
        private val CODE_COLOR = Color.parseColor("#00796B")    // teal
        private val LINK_COLOR = Color.parseColor("#1565C0")    // link blue
        private val BULLET_COLOR = Color.parseColor("#E65100")  // orange
        private val QUOTE_COLOR = Color.parseColor("#616161")   // grey
    }
}
