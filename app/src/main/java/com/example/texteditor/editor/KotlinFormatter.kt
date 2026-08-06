package com.example.texteditor.editor

/**
 * A simple code formatter for Kotlin.
 * Handles basic indentation based on curly braces.
 */
class KotlinFormatter {

    fun format(code: String): String {
        val lines = code.split("\n")
        val result = StringBuilder()
        var indentLevel = 0
        val indentString = "    " // 4 spaces

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                result.append("\n")
                continue
            }

            // Decrease indent if line starts with closing brace
            if (trimmedLine.startsWith("}")) {
                indentLevel--
            }

            // Apply current indentation
            repeat(indentLevel.coerceAtLeast(0)) {
                result.append(indentString)
            }
            result.append(trimmedLine).append("\n")

            // Increase indent if line ends with opening brace
            if (trimmedLine.endsWith("{")) {
                indentLevel++
            }
        }

        return result.toString().trimEnd()
    }
}
