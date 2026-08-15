package com.example.texteditor.files

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.texteditor.R
import com.example.texteditor.data.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Shows every saved file. Tap one to open it in the editor.
class FilesActivity : AppCompatActivity() {

    private lateinit var repository: FileRepository
    private lateinit var emptyView: TextView
    private lateinit var fileList: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.toolbar).updatePadding(top = systemBars.top)
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        repository = FileRepository(this)
        emptyView = findViewById(R.id.empty_view)
        fileList = findViewById(R.id.file_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fileList.setOnItemClickListener { _, _, position, _ ->
            val name = fileList.adapter.getItem(position) as String
            setResult(RESULT_OK, Intent().putExtra(EXTRA_OPENED_FILE, name))
            finish()
        }

        loadFiles()
    }

    private fun loadFiles() {
        lifecycleScope.launch {
            try {
                val names = withContext(Dispatchers.IO) { repository.listFileNames() }
                fileList.adapter = ArrayAdapter(this@FilesActivity, android.R.layout.simple_list_item_1, names)
                emptyView.isVisible = names.isEmpty()
            } catch (e: Exception) {
                toast(getString(R.string.error_loading_files))
                emptyView.isVisible = true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_files, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_open_in_file_manager) {
            openInFileManager()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openInFileManager() {
        val intent = repository.buildFolderViewIntent()
        if (intent == null) { toast(getString(R.string.no_folder_chosen)); return }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(getString(R.string.could_not_open_file_manager))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_OPENED_FILE = "extra_opened_file"
    }
}
