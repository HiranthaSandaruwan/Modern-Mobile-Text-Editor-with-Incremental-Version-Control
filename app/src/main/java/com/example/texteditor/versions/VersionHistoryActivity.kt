package com.example.texteditor.versions

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.texteditor.R
import com.example.texteditor.data.db.AppDatabase
import com.example.texteditor.data.db.FileVersion
import kotlinx.coroutines.launch

// Lists every saved snapshot of the current file, newest first.
// From here you can view a snapshot's diff or restore the file to it.
class VersionHistoryActivity : AppCompatActivity() {

    private lateinit var versionManager: VersionControlManager
    private lateinit var fileName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_version_history)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.toolbar).updatePadding(top = systemBars.top)
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        versionManager = VersionControlManager(AppDatabase.get(this))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_version_history)
        supportActionBar?.subtitle = fileName

        val emptyView = findViewById<TextView>(R.id.empty_view)
        val recycler = findViewById<RecyclerView>(R.id.version_list)
        recycler.layoutManager = LinearLayoutManager(this)

        val adapter = VersionAdapter(
            onViewDiff = { version -> openDiff(version) },
            onRestore = { version -> confirmRestore(version) }
        )
        recycler.adapter = adapter

        lifecycleScope.launch {
            val versions = versionManager.getVersions(fileName)
            adapter.submitList(versions.sortedByDescending { it.versionNumber })
            emptyView.isVisible = versions.isEmpty()
        }
    }

    private fun openDiff(version: FileVersion) {
        val intent = Intent(this, DiffActivity::class.java)
        intent.putExtra(DiffActivity.EXTRA_FILE_NAME, fileName)
        intent.putExtra(DiffActivity.EXTRA_VERSION_NUMBER, version.versionNumber)
        intent.putExtra(DiffActivity.EXTRA_VERSION_LABEL, version.label)
        startActivity(intent)
    }

    private fun confirmRestore(version: FileVersion) {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMessage(getString(R.string.restore_message, version.versionNumber, version.label))
            .setPositiveButton(R.string.restore) { _, _ ->
                // MainActivity does the actual restore, so just send the version number back.
                val data = Intent().putExtra(EXTRA_RESTORED_VERSION, version.versionNumber)
                setResult(RESULT_OK, data)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_RESTORED_VERSION = "extra_restored_version"
    }
}
