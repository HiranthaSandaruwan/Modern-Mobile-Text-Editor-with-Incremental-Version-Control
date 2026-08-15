package com.example.texteditor.editor

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.regex.Pattern

// Base class for syntax highlighters. Clears old colors, then colors each regex match found.
abstract class SyntaxHighlighter {

    // Re-highlights the whole text. Called a short delay after each edit.
    fun highlight(text: Editable) {
        clearSpans(text)
        if (text.isEmpty()) return
        applyPatterns(text)
    }

    // Each highlighter defines its own color rules here.
    protected abstract fun applyPatterns(text: Editable)

    // Colors every match of a pattern, optionally bold/italic too.
    protected fun colorMatches(
        text: Editable,
        pattern: Pattern,
        color: Int,
        bold: Boolean = false,
        italic: Boolean = false
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            colorRange(text, matcher.start(), matcher.end(), color, bold, italic)
        }
    }

    // Colors one range of text, optionally bold/italic too.
    protected fun colorRange(
        text: Editable,
        start: Int,
        end: Int,
        color: Int,
        bold: Boolean = false,
        italic: Boolean = false
    ) {
        text.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) {
            text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (italic) {
            text.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        // Removes all colors so highlighting can be re-applied cleanly.
        fun clearSpans(text: Editable) {
            text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                .forEach { text.removeSpan(it) }
            text.getSpans(0, text.length, StyleSpan::class.java)
                .forEach { text.removeSpan(it) }
        }
    }
}
