package com.example.texteditor.editor

import android.text.Editable

/**
 * A simple undo/redo system based on two in-memory stacks.
 *
 * Every time the user edits the text, MainActivity records ONE
 * [EditOperation] describing the change:
 *   - position:     where in the text the change happened
 *   - removedText:  what was deleted at that position
 *   - insertedText: what was inserted at that position
 *
 * Undo  = replace insertedText back with removedText.
 * Redo  = replace removedText with insertedText again.
 */
class UndoRedoManager {

    data class EditOperation(
        val position: Int,
        val removedText: String,
        val insertedText: String
    )

    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()

    /**
     * True while we are applying an undo/redo ourselves. MainActivity checks
     * this flag so the resulting text change is not recorded again
     * (otherwise undo would create an endless loop of new operations).
     */
    var isPerformingUndoRedo = false
        private set

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** Called for every user edit. A new edit clears the redo stack. */
    fun record(operation: EditOperation) {
        undoStack.addLast(operation)
        redoStack.clear()
        if (undoStack.size > MAX_HISTORY) {
            undoStack.removeFirst()
        }
    }

    fun undo(text: Editable) {
        val op = undoStack.removeLastOrNull() ?: return
        isPerformingUndoRedo = true
        text.replace(op.position, op.position + op.insertedText.length, op.removedText)
        isPerformingUndoRedo = false
        redoStack.addLast(op)
    }

    fun redo(text: Editable) {
        val op = redoStack.removeLastOrNull() ?: return
        isPerformingUndoRedo = true
        text.replace(op.position, op.position + op.removedText.length, op.insertedText)
        isPerformingUndoRedo = false
        undoStack.addLast(op)
    }

    /** Called when a different file is opened - history belongs to one session/file. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        private const val MAX_HISTORY = 500
    }
}
