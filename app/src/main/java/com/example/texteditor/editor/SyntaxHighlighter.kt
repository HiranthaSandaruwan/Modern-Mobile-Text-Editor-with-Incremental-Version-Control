package com.example.texteditor.editor

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.regex.Pattern

/**
 * Base class for all syntax highlighters.
 *
 * How highlighting works on Android:
 * The EditText's text is an "Editable" (a Spannable string). We can attach
 * "spans" to ranges of characters - for example a ForegroundColorSpan makes
 * a range of text colored. Highlighting is therefore:
 *   1. remove all spans we added before,
 *   2. run regular expressions over the text,
 *   3. attach a color/style span to every match.
 */
abstract class SyntaxHighlighter {

    /** Re-highlights the whole text. Called (with a small delay) after each edit. */
    fun highlight(text: Editable) {
        clearSpans(text)
        if (text.isEmpty()) return
        applyPatterns(text)
    }

    /** Each concrete highlighter defines its own patterns here. */
    protected abstract fun applyPatterns(text: Editable)

    /** Colors every match of [pattern], optionally also making it bold/italic. */
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

    /** Colors one already-known [start, end) range, optionally also making it bold/italic. */
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
        /** Removes all color and style spans (used before re-highlighting). */
        fun clearSpans(text: Editable) {
            text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                .forEach { text.removeSpan(it) }
            text.getSpans(0, text.length, StyleSpan::class.java)
                .forEach { text.removeSpan(it) }
        }
    }
}
