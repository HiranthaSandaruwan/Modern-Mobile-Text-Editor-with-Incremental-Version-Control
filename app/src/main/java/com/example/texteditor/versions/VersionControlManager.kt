package com.example.texteditor.versions

import com.example.texteditor.data.db.FileVersion
import com.example.texteditor.data.db.TrackedFile
import com.example.texteditor.data.db.VersionDao
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

/**
 * The delta-based version control system required by the assignment.
 *
 * STORAGE RULE (no duplication):
 *  - Snapshot 1 stores the full text ("base version").
 *  - Snapshot N (N > 1) stores ONLY a unified-diff patch that transforms
 *    the text of version N-1 into the text of version N.
 *
 * To rebuild the text of any version K we start with the base text and
 * apply the stored patches of versions 2..K one after another.
 *
 * The diffs are calculated with the open-source library "java-diff-utils"
 * (the assignment explicitly recommends it instead of writing our own
 * diff algorithm).
 */
class VersionControlManager(private val dao: VersionDao) {

    /** Result of trying to create a snapshot. */
    data class SnapshotResult(val created: Boolean, val versionNumber: Int)

    /** A single line of a diff, used by the diff viewer screen. */
    data class DiffLine(val type: Type, val text: String) {
        enum class Type { CONTEXT, ADDED, REMOVED }
    }

    // ---------------------------------------------------------------
    // Tracked file helpers (read-only flag, encoding)
    // ---------------------------------------------------------------

    /** Returns the database row for this file name, creating it if needed. */
    suspend fun getOrCreateFile(name: String): TrackedFile {
        dao.getFileByName(name)?.let { return it }
        dao.insertFile(TrackedFile(name = name))
        return dao.getFileByName(name)!!
    }

    suspend fun setReadOnly(name: String, readOnly: Boolean) {
        val file = getOrCreateFile(name)
        dao.setReadOnly(file.id, readOnly)
    }

    suspend fun setEncoding(name: String, encoding: String) {
        val file = getOrCreateFile(name)
        dao.setEncoding(file.id, encoding)
    }

    // ---------------------------------------------------------------
    // Snapshots
    // ---------------------------------------------------------------

    suspend fun getVersions(fileName: String): List<FileVersion> {
        val file = dao.getFileByName(fileName) ?: return emptyList()
        return dao.getVersionsForFile(file.id)
    }

    /**
     * Creates a new snapshot of [currentText].
     * Returns created=false when nothing changed since the last snapshot
     * (storing an empty patch would be pointless).
     */
    suspend fun createSnapshot(fileName: String, label: String, currentText: String): SnapshotResult {
        val file = getOrCreateFile(fileName)
        val versions = dao.getVersionsForFile(file.id)

        // First snapshot ever: store the full text (the "base" version).
        if (versions.isEmpty()) {
            dao.insertVersion(
                FileVersion(
                    fileId = file.id,
                    versionNumber = 1,
                    label = label,
                    createdAt = System.currentTimeMillis(),
                    baseContent = currentText,
                    patchText = null
                )
            )
            return SnapshotResult(created = true, versionNumber = 1)
        }

        // Later snapshots: store only the delta against the previous version.
        val previousText = rebuildText(versions, versions.size)
        if (previousText == currentText) {
            return SnapshotResult(created = false, versionNumber = versions.size)
        }

        val newNumber = versions.size + 1
        dao.insertVersion(
            FileVersion(
                fileId = file.id,
                versionNumber = newNumber,
                label = label,
                createdAt = System.currentTimeMillis(),
                baseContent = null,
                patchText = createPatchText(previousText, currentText)
            )
        )
        return SnapshotResult(created = true, versionNumber = newNumber)
    }

    /** Rebuilds the complete text of one specific version of a file. */
    suspend fun buildVersionText(fileName: String, versionNumber: Int): String {
        val file = dao.getFileByName(fileName)
            ?: throw IllegalStateException("Unknown file: $fileName")
        val versions = dao.getVersionsForFile(file.id)
        return rebuildText(versions, versionNumber)
    }

    // ---------------------------------------------------------------
    // Diff helpers (java-diff-utils)
    // ---------------------------------------------------------------

    /** base text + patch(2) + patch(3) + ... = text of [targetVersion]. */
    private fun rebuildText(versions: List<FileVersion>, targetVersion: Int): String {
        var text = versions.first().baseContent ?: ""
        for (i in 1 until targetVersion) {
            text = applyPatchText(text, versions[i].patchText ?: "")
        }
        return text
    }

    /** Computes a unified-diff patch string that turns [oldText] into [newText]. */
    private fun createPatchText(oldText: String, newText: String): String {
        val oldLines = oldText.split("\n")
        val newLines = newText.split("\n")
        val patch = DiffUtils.diff(oldLines, newLines)
        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "previous", "current", oldLines, patch, CONTEXT_LINES
        )
        return unifiedDiff.joinToString("\n")
    }

    /** Applies a stored unified-diff patch string to [baseText]. */
    private fun applyPatchText(baseText: String, patchText: String): String {
        val patch = UnifiedDiffUtils.parseUnifiedDiff(patchText.split("\n"))
        val resultLines = patch.applyTo(baseText.split("\n"))
        return resultLines.joinToString("\n")
    }

    /**
     * Produces a line-by-line comparison of two texts for the diff viewer:
     * unchanged lines are CONTEXT, removed lines are REMOVED (red),
     * new lines are ADDED (green).
     */
    fun buildDiffLines(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.split("\n")
        val newLines = newText.split("\n")
        val patch = DiffUtils.diff(oldLines, newLines)

        val result = mutableListOf<DiffLine>()
        var position = 0 // current index in oldLines

        // Deltas are returned in order. Everything between two deltas is unchanged.
        for (delta in patch.deltas) {
            while (position < delta.source.position) {
                result.add(DiffLine(DiffLine.Type.CONTEXT, oldLines[position]))
                position++
            }
            delta.source.lines.forEach { result.add(DiffLine(DiffLine.Type.REMOVED, it)) }
            delta.target.lines.forEach { result.add(DiffLine(DiffLine.Type.ADDED, it)) }
            position += delta.source.lines.size
        }
        // Remaining unchanged lines after the last delta.
        while (position < oldLines.size) {
            result.add(DiffLine(DiffLine.Type.CONTEXT, oldLines[position]))
            position++
        }
        return result
    }

    companion object {
        private const val CONTEXT_LINES = 3 // unchanged lines kept around each change in a patch
    }
}
