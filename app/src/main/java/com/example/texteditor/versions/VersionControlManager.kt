package com.example.texteditor.versions

import androidx.room.withTransaction
import com.example.texteditor.data.db.AppDatabase
import com.example.texteditor.data.db.FileVersion
import com.example.texteditor.data.db.TrackedFile
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

// Stores file versions as deltas (diffs) instead of full copies, to save space.
class VersionControlManager(private val db: AppDatabase) {

    private val dao = db.versionDao()

    data class SnapshotResult(val created: Boolean, val versionNumber: Int)
    data class DiffLine(val type: Type, val text: String) {
        enum class Type { CONTEXT, ADDED, REMOVED }
    }

    // Transaction stops two calls for the same new file from both trying to insert it at once.
    suspend fun getOrCreateFile(name: String): TrackedFile = db.withTransaction {
        dao.getFileByName(name)?.let { return@withTransaction it }
        dao.insertFile(TrackedFile(name = name))
        dao.getFileByName(name)!!
    }

    suspend fun setReadOnly(name: String, readOnly: Boolean) {
        val file = getOrCreateFile(name)
        dao.setReadOnly(file.id, readOnly)
    }

    suspend fun setEncoding(name: String, encoding: String) {
        val file = getOrCreateFile(name)
        dao.setEncoding(file.id, encoding)
    }

    suspend fun getVersions(fileName: String): List<FileVersion> {
        val file = dao.getFileByName(fileName) ?: return emptyList()
        return dao.getVersionsForFile(file.id)
    }

    // Transaction stops two quick taps on "Create snapshot" from both getting the same version number.
    suspend fun createSnapshot(fileName: String, label: String, currentText: String): SnapshotResult = db.withTransaction {
        val file = getOrCreateFile(fileName)
        val versions = dao.getVersionsForFile(file.id)

        if (versions.isEmpty()) {
            dao.insertVersion(FileVersion(
                fileId = file.id, versionNumber = 1, label = label,
                createdAt = System.currentTimeMillis(), baseContent = currentText, patchText = null
            ))
            return@withTransaction SnapshotResult(true, 1)
        }

        val previousText = rebuildText(versions, versions.size)
        if (previousText == currentText) return@withTransaction SnapshotResult(false, versions.size)

        val newNumber = versions.size + 1
        dao.insertVersion(FileVersion(
            fileId = file.id, versionNumber = newNumber, label = label,
            createdAt = System.currentTimeMillis(), baseContent = null,
            patchText = createPatchText(previousText, currentText)
        ))
        SnapshotResult(true, newNumber)
    }

    suspend fun buildVersionText(fileName: String, versionNumber: Int): String {
        val file = dao.getFileByName(fileName) ?: throw IllegalStateException("Unknown file")
        val versions = dao.getVersionsForFile(file.id)
        return rebuildText(versions, versionNumber)
    }

    private fun rebuildText(versions: List<FileVersion>, targetVersion: Int): String {
        var text = versions.first().baseContent ?: ""
        for (i in 1 until targetVersion) {
            text = applyPatchText(text, versions[i].patchText ?: "")
        }
        return text
    }

    private fun createPatchText(oldText: String, newText: String): String {
        val oldLines = oldText.split("\n")
        val newLines = newText.split("\n")
        val patch = DiffUtils.diff(oldLines, newLines)
        return UnifiedDiffUtils.generateUnifiedDiff("prev", "curr", oldLines, patch, 3).joinToString("\n")
    }

    private fun applyPatchText(baseText: String, patchText: String): String {
        val patch = UnifiedDiffUtils.parseUnifiedDiff(patchText.split("\n"))
        return patch.applyTo(baseText.split("\n")).joinToString("\n")
    }

    fun buildDiffLines(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.split("\n")
        val newLines = newText.split("\n")
        val patch = DiffUtils.diff(oldLines, newLines)
        val result = mutableListOf<DiffLine>()
        var position = 0

        for (delta in patch.deltas) {
            while (position < delta.source.position) {
                result.add(DiffLine(DiffLine.Type.CONTEXT, oldLines[position++]))
            }
            delta.source.lines.forEach { result.add(DiffLine(DiffLine.Type.REMOVED, it)) }
            delta.target.lines.forEach { result.add(DiffLine(DiffLine.Type.ADDED, it)) }
            position += delta.source.lines.size
        }
        while (position < oldLines.size) {
            result.add(DiffLine(DiffLine.Type.CONTEXT, oldLines[position++]))
        }
        return result
    }
}
