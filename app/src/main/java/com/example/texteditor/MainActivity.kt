package com.example.texteditor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.KeyListener
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.texteditor.data.FileRepository
import com.example.texteditor.data.RecentFilesStore
import com.example.texteditor.data.db.AppDatabase
import com.example.texteditor.editor.*
import com.example.texteditor.versions.VersionControlManager
import com.example.texteditor.versions.VersionHistoryActivity
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * Main editor activity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var editor: EditText
    private lateinit var statusText: TextView
    private lateinit var plainSearchPanel: View
    private lateinit var plainSearchInput: EditText
    private lateinit var searchPanel: View
    private lateinit var searchInput: EditText
    private lateinit var replaceInput: EditText
    private lateinit var previewScroll: ScrollView
    private lateinit var previewText: TextView
    private lateinit var recentList: ListView

    private lateinit var repository: FileRepository
    private lateinit var recentFiles: RecentFilesStore
    private lateinit var versionManager: VersionControlManager
    private lateinit var autoSave: AutoSaveManager
    private lateinit var kotlinHighlighter: KotlinHighlighter
    private lateinit var markdownHighlighter: MarkdownHighlighter
    private lateinit var markwon: Markwon
    private val undoManager = UndoRedoManager()
    private val formatter = KotlinFormatter()

    private var currentFileName: String? = null
    private var currentEncoding: Charset = Charsets.UTF_8
    private var isModified = false
    private var isReadOnly = false
    private var wordWrapEnabled = true
    private var isPreviewVisible = false
    private var suppressTextWatcher = false
    private var editableKeyListener: KeyListener? = null

    private val highlightHandler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { applyHighlighting() }

    private val historyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val version = result.data?.getIntExtra(VersionHistoryActivity.EXTRA_RESTORED_VERSION, -1) ?: -1
                if (version > 0) restoreVersion(version)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupToolbarAndDrawer()
        setupEdgeToEdge()

        repository = FileRepository(this)
        recentFiles = RecentFilesStore(this)
        versionManager = VersionControlManager(AppDatabase.get(this))
        kotlinHighlighter = KotlinHighlighter(this)
        markdownHighlighter = MarkdownHighlighter()
        markwon = Markwon.create(this)
        autoSave = AutoSaveManager(this) { Pair(currentFileName, editor.text.toString()) }

        editableKeyListener = editor.keyListener
        editor.isHorizontalScrollBarEnabled = true

        setupTextWatcher()
        setupPlainSearchPanel()
        setupSearchPanel()
        setupBackHandler()

        val isFirstRun = repository.ensureSampleFiles()
        refreshRecentList()

        if (savedInstanceState == null) {
            when {
                autoSave.hasBackup() -> checkForCrashBackup()
                isFirstRun -> openFile("Welcome.md")
            }
        } else {
            currentFileName = savedInstanceState.getString("file")
            currentEncoding = Charset.forName(savedInstanceState.getString("enc", "UTF-8"))
            isReadOnly = savedInstanceState.getBoolean("ro", false)
            wordWrapEnabled = savedInstanceState.getBoolean("ww", true)
            applyReadOnlyState()
            editor.setHorizontallyScrolling(!wordWrapEnabled)
        }
        updateStatusBar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("file", currentFileName)
        outState.putString("enc", currentEncoding.name())
        outState.putBoolean("ro", isReadOnly)
        outState.putBoolean("ww", wordWrapEnabled)
        outState.putBoolean("mod", isModified)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        suppressTextWatcher = true
        super.onRestoreInstanceState(savedInstanceState)
        suppressTextWatcher = false
        isModified = savedInstanceState.getBoolean("mod", false)
        applyHighlighting()
        updateStatusBar()
    }

    override fun onResume() {
        super.onResume()
        autoSave.start()
    }

    override fun onPause() {
        super.onPause()
        autoSave.stop()
        autoSave.saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) autoSave.clearBackup()
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        editor = findViewById(R.id.editor)
        statusText = findViewById(R.id.status_text)
        plainSearchPanel = findViewById(R.id.plain_search_panel)
        plainSearchInput = findViewById(R.id.plain_search_input)
        searchPanel = findViewById(R.id.search_panel)
        searchInput = findViewById(R.id.search_input)
        replaceInput = findViewById(R.id.replace_input)
        previewScroll = findViewById(R.id.preview_scroll)
        previewText = findViewById(R.id.preview_text)
        recentList = findViewById(R.id.recent_list)
    }

    private fun setupToolbarAndDrawer() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        findViewById<View>(R.id.btn_new_file).setOnClickListener {
            drawerLayout.closeDrawers()
            confirmUnsavedChanges { showNewFileDialog() }
        }
        findViewById<View>(R.id.btn_open_file).setOnClickListener {
            drawerLayout.closeDrawers()
            confirmUnsavedChanges { showOpenFileDialog() }
        }
        recentList.setOnItemClickListener { _, _, position, _ ->
            val name = recentList.adapter.getItem(position) as String
            drawerLayout.closeDrawers()
            confirmUnsavedChanges { openFile(name) }
        }
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            findViewById<View>(R.id.toolbar).updatePadding(top = systemBars.top)
            findViewById<View>(R.id.drawer_header).updatePadding(top = systemBars.top)
            findViewById<View>(R.id.drawer_panel).updatePadding(bottom = systemBars.bottom)
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            drawerLayout.getChildAt(0).updatePadding(bottom = bottomPadding)
            insets
        }
    }

    private fun setupTextWatcher() {
        editor.addTextChangedListener(object : TextWatcher {
            private var removedText = ""
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (!suppressTextWatcher && !undoManager.isPerformingUndoRedo) {
                    removedText = s.subSequence(start, start + count).toString()
                }
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!suppressTextWatcher && !undoManager.isPerformingUndoRedo) {
                    val insertedText = s.subSequence(start, start + count).toString()
                    undoManager.record(UndoRedoManager.EditOperation(start, removedText, insertedText))
                }
            }
            override fun afterTextChanged(s: Editable) {
                if (!suppressTextWatcher) {
                    isModified = true
                    updateStatusBar()
                    scheduleHighlighting()
                }
            }
        })
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawerLayout.isDrawerOpen(findViewById(R.id.drawer_panel)) -> drawerLayout.closeDrawers()
                    plainSearchPanel.visibility == View.VISIBLE -> plainSearchPanel.visibility = View.GONE
                    searchPanel.visibility == View.VISIBLE -> searchPanel.visibility = View.GONE
                    isModified -> AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.unsaved_changes_title)
                        .setMessage(R.string.exit_without_saving)
                        .setPositiveButton(R.string.exit) { _, _ -> finish() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                    else -> finish()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_word_wrap).isChecked = wordWrapEnabled
        menu.findItem(R.id.action_read_only).isChecked = isReadOnly
        menu.findItem(R.id.action_format_code).isVisible = isKotlinFile()
        menu.findItem(R.id.action_undo).isEnabled = !isReadOnly
        menu.findItem(R.id.action_redo).isEnabled = !isReadOnly
        menu.findItem(R.id.action_snapshot).isEnabled = !isReadOnly
        val preview = menu.findItem(R.id.action_markdown_preview)
        preview.isEnabled = isMarkdownFile()
        preview.isChecked = isPreviewVisible
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
        when (item.itemId) {
            R.id.action_undo -> undo()
            R.id.action_redo -> redo()
            R.id.action_save -> saveFile()
            R.id.action_search -> togglePlainSearchPanel()
            R.id.action_find -> toggleSearchPanel()
            R.id.action_new -> confirmUnsavedChanges { showNewFileDialog() }
            R.id.action_open -> confirmUnsavedChanges { showOpenFileDialog() }
            R.id.action_save_as -> showSaveAsDialog()
            R.id.action_snapshot -> showSnapshotDialog()
            R.id.action_history -> openVersionHistory()
            R.id.action_format_code -> formatKotlinCode()
            R.id.action_word_wrap -> toggleWordWrap()
            R.id.action_read_only -> toggleReadOnly()
            R.id.action_markdown_preview -> toggleMarkdownPreview()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun openFile(name: String) {
        lifecycleScope.launch {
            try {
                val tracked = versionManager.getOrCreateFile(name)
                currentEncoding = Charset.forName(tracked.encoding)
                val text = withContext(Dispatchers.IO) { repository.readText(name, currentEncoding) }
                currentFileName = name
                isReadOnly = tracked.isReadOnly
                setEditorText(text)
                applyReadOnlyState()
                hidePreview()
                recentFiles.add(name)
                refreshRecentList()
                updateStatusBar()
                invalidateOptionsMenu()
            } catch (e: Exception) { toast(getString(R.string.error_opening_file, name)) }
        }
    }

    private fun saveFile() {
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        val name = currentFileName
        if (name == null) {
            showSaveAsDialog()
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repository.saveText(name, editor.text.toString(), currentEncoding) }
            isModified = false
            updateStatusBar()
            autoSave.clearBackup()
            toast(getString(R.string.file_saved, name))
        }
    }

    private fun showNewFileDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new_file, null)
        val input = view.findViewById<EditText>(R.id.input_file_name)
        AlertDialog.Builder(this).setTitle(R.string.new_file).setView(view).setPositiveButton(R.string.create) { _, _ ->
            val name = normalizeFileName(input.text.toString()) ?: return@setPositiveButton
            if (repository.exists(name)) toast(getString(R.string.file_already_exists, name))
            else lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.saveText(name, "") }
                openFile(name)
            }
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showOpenFileDialog() {
        val names = repository.listFileNames()
        if (names.isEmpty()) { toast(getString(R.string.no_files_yet)); return }
        AlertDialog.Builder(this).setTitle(R.string.open_file).setItems(names.toTypedArray()) { _, which -> openFile(names[which]) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showSaveAsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_save_as, null)
        val input = view.findViewById<EditText>(R.id.input_file_name)
        val spinner = view.findViewById<Spinner>(R.id.spinner_encoding)
        spinner.adapter = ArrayAdapter.createFromResource(this, R.array.encodings, android.R.layout.simple_spinner_dropdown_item)
        val encodings = resources.getStringArray(R.array.encodings)
        val currentIndex = encodings.indexOf(currentEncoding.name())
        if (currentIndex >= 0) spinner.setSelection(currentIndex)
        input.setText(currentFileName ?: "")

        AlertDialog.Builder(this).setTitle(R.string.save_as).setView(view).setPositiveButton(R.string.save) { _, _ ->
            val name = normalizeFileName(input.text.toString()) ?: return@setPositiveButton
            val enc = spinner.selectedItem.toString()
            lifecycleScope.launch {
                currentEncoding = Charset.forName(enc)
                withContext(Dispatchers.IO) { repository.saveText(name, editor.text.toString(), currentEncoding) }
                versionManager.setEncoding(name, enc)
                currentFileName = name
                isModified = false
                isReadOnly = false
                applyReadOnlyState()
                recentFiles.add(name)
                refreshRecentList()
                applyHighlighting()
                updateStatusBar()
                invalidateOptionsMenu()
                autoSave.clearBackup()
                toast(getString(R.string.file_saved, name))
            }
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun normalizeFileName(rawName: String): String? {
        val name = rawName.trim()
        if (name.isEmpty() || !name.matches(Regex("^[A-Za-z0-9._ -]+$"))) { toast(getString(R.string.invalid_file_name)); return null }
        return if (name.contains(".")) name else "$name.txt"
    }

    private fun confirmUnsavedChanges(action: () -> Unit) {
        if (!isModified) { action(); return }
        AlertDialog.Builder(this).setTitle(R.string.unsaved_changes_title).setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.discard) { _, _ -> action() }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun setEditorText(text: String) {
        suppressTextWatcher = true
        editor.setText(text)
        suppressTextWatcher = false
        undoManager.clear()
        isModified = false
        applyHighlighting()
    }

    private fun refreshRecentList() {
        recentList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, recentFiles.getAll())
    }

    private fun undo() {
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        if (!undoManager.canUndo()) toast(getString(R.string.nothing_to_undo)) else undoManager.undo(editor.text)
    }
    private fun redo() {
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        if (!undoManager.canRedo()) toast(getString(R.string.nothing_to_redo)) else undoManager.redo(editor.text)
    }

    private fun scheduleHighlighting() {
        highlightHandler.removeCallbacks(highlightRunnable)
        highlightHandler.postDelayed(highlightRunnable, 250L)
    }

    private fun applyHighlighting() {
        val name = currentFileName ?: ""
        when {
            isKotlinFile() -> kotlinHighlighter.highlight(editor.text)
            isMarkdownFile() -> markdownHighlighter.highlight(editor.text)
            else -> SyntaxHighlighter.clearSpans(editor.text)
        }
    }

    private fun isKotlinFile() = currentFileName?.let { it.endsWith(".kt") || it.endsWith(".kts") } ?: false
    private fun isMarkdownFile() = currentFileName?.let { it.endsWith(".md") || it.endsWith(".markdown") } ?: false

    /**
     * Standalone "Search" panel - lets the user search for a word or a whole sentence in the
     * file without the Find & Replace panel's replace controls getting in the way.
     */
    private fun setupPlainSearchPanel() {
        findViewById<View>(R.id.btn_plain_search_next).setOnClickListener { searchNext() }
        findViewById<View>(R.id.btn_close_plain_search).setOnClickListener { plainSearchPanel.visibility = View.GONE }
    }

    private fun togglePlainSearchPanel() {
        if (searchPanel.visibility == View.VISIBLE) searchPanel.visibility = View.GONE
        plainSearchPanel.visibility = if (plainSearchPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (plainSearchPanel.visibility == View.VISIBLE) plainSearchInput.requestFocus()
    }

    private fun searchNext() = performSearch(plainSearchInput.text.toString())

    private fun setupSearchPanel() {
        findViewById<View>(R.id.btn_find_next).setOnClickListener { findNext() }
        findViewById<View>(R.id.btn_replace).setOnClickListener { replaceCurrent() }
        findViewById<View>(R.id.btn_replace_all).setOnClickListener { replaceAll() }
        findViewById<View>(R.id.btn_close_search).setOnClickListener { searchPanel.visibility = View.GONE }
    }

    private fun toggleSearchPanel() {
        if (plainSearchPanel.visibility == View.VISIBLE) plainSearchPanel.visibility = View.GONE
        searchPanel.visibility = if (searchPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (searchPanel.visibility == View.VISIBLE) searchInput.requestFocus()
    }

    private fun findNext() = performSearch(searchInput.text.toString())

    /** Selects the next match of [query] in the editor, wrapping around to the start if needed. */
    private fun performSearch(query: String) {
        if (query.isEmpty()) return
        val content = editor.text.toString()
        var from = editor.selectionEnd
        if (from >= content.length) from = 0
        var idx = content.indexOf(query, from, true)
        if (idx == -1) idx = content.indexOf(query, 0, true)
        if (idx == -1) toast(getString(R.string.no_matches, query))
        else { editor.requestFocus(); editor.setSelection(idx, idx + query.length) }
    }

    private fun replaceCurrent() {
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        val query = searchInput.text.toString()
        if (query.isEmpty()) return
        val start = editor.selectionStart
        val end = editor.selectionEnd
        val hasMatchSelected = end > start && editor.text.subSequence(start, end).toString().equals(query, true)
        if (!hasMatchSelected) {
            // Nothing matching is selected yet (e.g. the user typed a query and hit
            // Replace without ever pressing Find next) - select the next match first.
            findNext()
            return
        }
        editor.text.replace(start, end, replaceInput.text.toString())
        findNext()
    }

    private fun replaceAll() {
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        val query = searchInput.text.toString()
        if (query.isEmpty()) return
        val content = editor.text.toString()
        val replaced = content.replace(query, replaceInput.text.toString(), true)
        if (content == replaced) { toast(getString(R.string.no_matches, query)); return }
        val count = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(content).count()
        editor.text.replace(0, editor.text.length, replaced)
        toast(getString(R.string.replaced_count, count))
    }

    private fun toggleWordWrap() {
        wordWrapEnabled = !wordWrapEnabled
        editor.setHorizontallyScrolling(!wordWrapEnabled)
        invalidateOptionsMenu()
    }

    private fun toggleReadOnly() {
        val name = currentFileName
        if (name == null) {
            toast(getString(R.string.save_before_read_only))
            return
        }
        isReadOnly = !isReadOnly
        applyReadOnlyState()
        updateStatusBar()
        invalidateOptionsMenu()
        lifecycleScope.launch { versionManager.setReadOnly(name, isReadOnly) }
    }

    private fun applyReadOnlyState() { editor.keyListener = if (isReadOnly) null else editableKeyListener }

    private fun toggleMarkdownPreview() {
        if (isPreviewVisible) hidePreview()
        else {
            markwon.setMarkdown(previewText, editor.text.toString())
            previewScroll.visibility = View.VISIBLE
            editor.visibility = View.GONE
            isPreviewVisible = true
        }
        invalidateOptionsMenu()
    }

    private fun hidePreview() {
        previewScroll.visibility = View.GONE
        editor.visibility = View.VISIBLE
        isPreviewVisible = false
    }

    private fun formatKotlinCode() {
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        val currentText = editor.text.toString()
        val formatted = formatter.format(currentText)
        if (currentText != formatted) {
            setEditorText(formatted)
            isModified = true
            updateStatusBar()
            toast(getString(R.string.formatting_done))
        }
    }

    private fun showSnapshotDialog() {
        val name = currentFileName
        if (name == null) {
            toast(getString(R.string.save_before_snapshot))
            return
        }
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        val view = layoutInflater.inflate(R.layout.dialog_snapshot, null)
        val input = view.findViewById<EditText>(R.id.input_snapshot_label)
        AlertDialog.Builder(this).setTitle(R.string.create_snapshot).setView(view).setPositiveButton(R.string.create) { _, _ ->
            val label = input.text.toString().trim().ifEmpty { getString(R.string.snapshot_default_label) }
            lifecycleScope.launch {
                val text = editor.text.toString()
                withContext(Dispatchers.IO) { repository.saveText(name, text, currentEncoding) }
                isModified = false
                updateStatusBar()
                autoSave.clearBackup()
                val result = versionManager.createSnapshot(name, label, text)
                toast(if (result.created) getString(R.string.snapshot_created, result.versionNumber) else getString(R.string.snapshot_no_changes))
            }
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun openVersionHistory() {
        val name = currentFileName
        if (name == null) {
            toast(getString(R.string.save_before_snapshot))
            return
        }
        historyLauncher.launch(Intent(this, VersionHistoryActivity::class.java).putExtra(VersionHistoryActivity.EXTRA_FILE_NAME, name))
    }

    private fun restoreVersion(versionNumber: Int) {
        val name = currentFileName ?: return
        if (isReadOnly) { toast(getString(R.string.file_is_read_only)); return }
        lifecycleScope.launch {
            try {
                val text = versionManager.buildVersionText(name, versionNumber)
                setEditorText(text)
                withContext(Dispatchers.IO) { repository.saveText(name, text, currentEncoding) }
                updateStatusBar()
                autoSave.clearBackup()
                toast(getString(R.string.restored_version, versionNumber))
            } catch (e: Exception) { toast(getString(R.string.restore_failed)) }
        }
    }

    private fun checkForCrashBackup() {
        val backup = autoSave.readBackup() ?: return
        AlertDialog.Builder(this).setTitle(R.string.recovery_title)
            .setMessage(getString(R.string.recovery_message, backup.fileName ?: getString(R.string.untitled)))
            .setCancelable(false).setPositiveButton(R.string.restore) { _, _ -> restoreBackup(backup) }
            .setNegativeButton(R.string.discard) { _, _ -> autoSave.clearBackup() }.show()
    }

    private fun restoreBackup(backup: AutoSaveManager.Backup) {
        lifecycleScope.launch {
            backup.fileName?.let { name ->
                if (repository.exists(name)) {
                    val tracked = versionManager.getOrCreateFile(name)
                    currentFileName = name
                    currentEncoding = Charset.forName(tracked.encoding)
                    isReadOnly = tracked.isReadOnly
                    applyReadOnlyState()
                    recentFiles.add(name)
                    refreshRecentList()
                }
            }
            setEditorText(backup.text)
            isModified = true
            autoSave.clearBackup()
            updateStatusBar()
            invalidateOptionsMenu()
            toast(getString(R.string.recovery_done))
        }
    }

    private fun updateStatusBar() {
        val name = currentFileName ?: getString(R.string.untitled)
        val mod = if (isModified) getString(R.string.status_modified) else getString(R.string.status_saved)
        val ro = if (isReadOnly) "  •  " + getString(R.string.status_read_only) else ""
        statusText.text = "$name  •  $mod$ro  •  ${currentEncoding.name()}"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
