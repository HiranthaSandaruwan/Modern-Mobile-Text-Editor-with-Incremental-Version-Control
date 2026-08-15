package com.example.texteditor.editor

import android.text.Editable

// Keeps track of edits so they can be undone and redone.
class UndoRedoManager {

    data class EditOperation(
        val position: Int,
        val removedText: String,
        val insertedText: String
    )

    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()

    // True while undo/redo is running, so that change isn't recorded as a new edit.
    var isPerformingUndoRedo = false
        private set

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // Records one edit. A new edit clears the redo history.
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

    // Called when a different file is opened, so old history doesn't carry over.
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        private const val MAX_HISTORY = 500
    }
}
