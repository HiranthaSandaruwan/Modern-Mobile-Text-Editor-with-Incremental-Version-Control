package com.example.texteditor.versions

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.texteditor.R
import com.example.texteditor.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Shows what changed in one snapshot: green = added lines, red = removed lines.
class DiffActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diff)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.toolbar).updatePadding(top = systemBars.top)
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        val versionNumber = intent.getIntExtra(EXTRA_VERSION_NUMBER, 1)
        val label = intent.getStringExtra(EXTRA_VERSION_LABEL) ?: ""

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.diff_title_format, versionNumber)
        supportActionBar?.subtitle = label

        val diffText = findViewById<TextView>(R.id.diff_text)
        val versionManager = VersionControlManager(AppDatabase.get(this))

        lifecycleScope.launch {
            try {
                val spannable = withContext(Dispatchers.Default) {
                    val oldText =
                        if (versionNumber == 1) ""
                        else versionManager.buildVersionText(fileName, versionNumber - 1)
                    val newText = versionManager.buildVersionText(fileName, versionNumber)
                    buildColoredDiff(versionManager.buildDiffLines(oldText, newText))
                }
                diffText.text = spannable
            } catch (e: Exception) {
                diffText.text = getString(R.string.diff_load_failed)
            }
        }
    }

    // Turns the diff lines into one colored block of text.
    private fun buildColoredDiff(lines: List<VersionControlManager.DiffLine>): CharSequence {
        val builder = SpannableStringBuilder()
        for (line in lines) {
            val prefix = when (line.type) {
                VersionControlManager.DiffLine.Type.ADDED -> "+ "
                VersionControlManager.DiffLine.Type.REMOVED -> "- "
                VersionControlManager.DiffLine.Type.CONTEXT -> "  "
            }
            val start = builder.length
            builder.append(prefix).append(line.text).append("\n")

            val backgroundColor = when (line.type) {
                VersionControlManager.DiffLine.Type.ADDED -> ADDED_BACKGROUND
                VersionControlManager.DiffLine.Type.REMOVED -> REMOVED_BACKGROUND
                VersionControlManager.DiffLine.Type.CONTEXT -> null
            }
            if (backgroundColor != null) {
                builder.setSpan(
                    BackgroundColorSpan(backgroundColor),
                    start, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_VERSION_NUMBER = "extra_version_number"
        const val EXTRA_VERSION_LABEL = "extra_version_label"

        private val ADDED_BACKGROUND = Color.parseColor("#C8E6C9")   // light green
        private val REMOVED_BACKGROUND = Color.parseColor("#FFCDD2") // light red
    }
}
