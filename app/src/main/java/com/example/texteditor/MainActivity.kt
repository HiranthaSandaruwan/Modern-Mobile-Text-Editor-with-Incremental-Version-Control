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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import com.example.texteditor.editor.AutoSaveManager
import com.example.texteditor.editor.KotlinHighlighter
import com.example.texteditor.editor.MarkdownHighlighter
import com.example.texteditor.editor.SyntaxHighlighter
import com.example.texteditor.editor.UndoRedoManager
import com.example.texteditor.versions.VersionControlManager
import com.example.texteditor.versions.VersionHistoryActivity
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * The single editor screen of the app.
 *
 * Overview of the pieces working together here:
 *  - FileRepository          -> reads/writes documents in internal storage
 *  - RecentFilesStore        -> recent file names for the sidebar (drawer)
 *  - UndoRedoManager         -> undo/redo stacks fed by the TextWatcher
 *  - AutoSaveManager         -> 10s crash-recovery backup of the buffer
 *  - Kotlin/MarkdownHighlighter -> syntax coloring of the EditText
 *  - VersionControlManager   -> delta snapshots stored in the Room database
 *  - Markwon                 -> renders the Markdown preview panel
 */
class MainActivity : AppCompatActivity() {

    // ---- views ----
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var editor: EditText
    private lateinit var statusText: TextView
    private lateinit var searchPanel: View
    private lateinit var searchInput: EditText
    private lateinit var replaceInput: EditText
    private lateinit var previewScroll: ScrollView
    private lateinit var previewText: TextView
    private lateinit var recentList: ListView

    // ---- helpers ----
    private lateinit var repository: FileRepository
    private lateinit var recentFiles: RecentFilesStore
    private lateinit var versionManager: VersionControlManager
    private lateinit var autoSave: AutoSaveManager
    private lateinit var kotlinHighlighter: KotlinHighlighter
    private lateinit var markdownHighlighter: MarkdownHighlighter
    private lateinit var markwon: Markwon
    private val undoManager = UndoRedoManager()

    // ---- editor state ----
    private var currentFileName: String? = null   // null = new "Untitled" buffer
    private var currentEncoding: Charset = Charsets.UTF_8
    private var isModified = false
    private var isReadOnly = false
    private var wordWrapEnabled = true
    private var isPreviewVisible = false

    /** True while WE change the editor text from code (open file, undo, restore...).
     *  The TextWatcher checks it so those changes are not recorded as user edits. */
    private var suppressTextWatcher = false

    /** The EditText's normal key listener; set to null to make it read-only. */
    private var editableKeyListener: KeyListener? = null

    // Re-highlighting after every single keystroke would be wasteful, so we
    // wait 250 ms after the LAST edit before highlighting (called debouncing).
    private val highlightHandler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { applyHighlighting() }

    /** Receives the version number chosen on the Version History screen. */
    private val historyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val version = result.data
                    ?.getIntExtra(VersionHistoryActivity.EXTRA_RESTORED_VERSION, -1) ?: -1
                if (version > 0) restoreVersion(version)
            }
        }

    // =================================================================
    // Lifecycle
    // =================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupToolbarAndDrawer()
        setupEdgeToEdge()

        repository = FileRepository(this)
        recentFiles = RecentFilesStore(this)
        versionManager = VersionControlManager(AppDatabase.get(this).versionDao())
        kotlinHighlighter = KotlinHighlighter(this)
        markdownHighlighter = MarkdownHighlighter()
        markwon = Markwon.create(this)
        autoSave = AutoSaveManager(this) { Pair(currentFileName, editor.text.toString()) }

        editableKeyListener = editor.keyListener
        editor.isHorizontalScrollBarEnabled = true

        setupTextWatcher()
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
            // The activity was recreated (e.g. screen rotation): bring back our state.
            // The editor TEXT itself is restored automatically by Android later,
            // in onRestoreInstanceState().
            currentFileName = savedInstanceState.getString(STATE_FILE_NAME)
            currentEncoding = Charset.forName(savedInstanceState.getString(STATE_ENCODING) ?: "UTF-8")
            isReadOnly = savedInstanceState.getBoolean(STATE_READ_ONLY, false)
            wordWrapEnabled = savedInstanceState.getBoolean(STATE_WORD_WRAP, true)
            applyReadOnlyState()
            editor.setHorizontallyScrolling(!wordWrapEnabled)
        }
        updateStatusBar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FILE_NAME, currentFileName)
        outState.putString(STATE_ENCODING, currentEncoding.name())
        outState.putBoolean(STATE_READ_ONLY, isReadOnly)
        outState.putBoolean(STATE_WORD_WRAP, wordWrapEnabled)
        outState.putBoolean(STATE_MODIFIED, isModified)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // Android now puts the saved text back into the EditText. That must not
        // count as a user edit, so the watcher is suppressed while it happens.
        suppressTextWatcher = true
        super.onRestoreInstanceState(savedInstanceState)
        suppressTextWatcher = false
        isModified = savedInstanceState.getBoolean(STATE_MODIFIED, false)
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
        // Also back up right now: the app may get killed while in background.
        autoSave.saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // Clean exit -> no crash, so the recovery backup is not needed.
            autoSave.clearBackup()
        }
    }

    // =================================================================
    // View setup
    // =================================================================

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        editor = findViewById(R.id.editor)
        statusText = findViewById(R.id.status_text)
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

        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.drawer_open, R.string.drawer_close
        )
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
            
            // Toolbar handles top inset
            findViewById<View>(R.id.toolbar).updatePadding(top = systemBars.top)
            
            // Drawer header handles top inset so its background extends to the top
            findViewById<View>(R.id.drawer_header).updatePadding(top = systemBars.top)
            
            // Drawer panel bottom padding for navigation bar
            findViewById<View>(R.id.drawer_panel).updatePadding(bottom = systemBars.bottom)
            
            // Main content bottom padding for navigation bar or keyboard
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            drawerLayout.getChildAt(0).updatePadding(bottom = bottomPadding)

            insets
        }
    }

    /** Records every user edit for undo/redo and schedules re-highlighting. */
    private fun setupTextWatcher() {
        editor.addTextChangedListener(object : TextWatcher {
            private var removedText = ""

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (suppressTextWatcher || undoManager.isPerformingUndoRedo) return
                // The characters that are about to be replaced/deleted.
                removedText = s.subSequence(start, start + count).toString()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (suppressTextWatcher || undoManager.isPerformingUndoRedo) return
                val insertedText = s.subSequence(start, start + count).toString()
                undoManager.record(UndoRedoManager.EditOperation(start, removedText, insertedText))
            }

            override fun afterTextChanged(s: Editable) {
                if (suppressTextWatcher) return
                isModified = true
                updateStatusBar()
                scheduleHighlighting()
            }
        })
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawerLayout.isDrawerOpen(findViewById(R.id.drawer_panel)) ->
                        drawerLayout.closeDrawers()
                    searchPanel.visibility == View.VISIBLE ->
                        searchPanel.visibility = View.GONE
                    isModified -> AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.unsaved_changes_title)
                        .setMessage(R.string.exit_without_saving)
                        .setPositiveButton(R.string.exit) { _, _ -> finish() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    else -> finish()
                }
            }
        })
    }

    // =================================================================
    // Options menu (toolbar actions)
    // =================================================================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_word_wrap).isChecked = wordWrapEnabled
        menu.findItem(R.id.action_read_only).isChecked = isReadOnly
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
            R.id.action_find -> toggleSearchPanel()
            R.id.action_new -> confirmUnsavedChanges { showNewFileDialog() }
            R.id.action_open -> confirmUnsavedChanges { showOpenFileDialog() }
            R.id.action_save_as -> showSaveAsDialog()
            R.id.action_snapshot -> showSnapshotDialog()
            R.id.action_history -> openVersionHistory()
            R.id.action_word_wrap -> toggleWordWrap()
            R.id.action_read_only -> toggleReadOnly()
            R.id.action_markdown_preview -> toggleMarkdownPreview()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // =================================================================
    // File operations
    // =================================================================

    private fun openFile(name: String) {
        lifecycleScope.launch {
            try {
                // Per-file settings (read-only flag, encoding) come from the database.
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
            } catch (e: Exception) {
                toast(getString(R.string.error_opening_file, name))
            }
        }
    }

    private fun saveFile() {
        if (isReadOnly) {
            toast(getString(R.string.file_is_read_only))
            return
        }
        val name = currentFileName
        if (name == null) {
            // An unnamed buffer cannot be saved directly - ask for a name.
            showSaveAsDialog()
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveText(name, editor.text.toString(), currentEncoding)
            }
            isModified = false
            updateStatusBar()
            toast(getString(R.string.file_saved, name))
        }
    }

    private fun showNewFileDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new_file, null)
        val input = view.findViewById<EditText>(R.id.input_file_name)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_file)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = normalizeFileName(input.text.toString()) ?: return@setPositiveButton
                if (repository.exists(name)) {
                    toast(getString(R.string.file_already_exists, name))
                } else {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.saveText(name, "") }
                        openFile(name)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showOpenFileDialog() {
        val names = repository.listFileNames()
        if (names.isEmpty()) {
            toast(getString(R.string.no_files_yet))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.open_file)
            .setItems(names.toTypedArray()) { _, which -> openFile(names[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** "Save As" also offers the encoding choice required by the assignment. */
    private fun showSaveAsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_save_as, null)
        val input = view.findViewById<EditText>(R.id.input_file_name)
        val spinner = view.findViewById<Spinner>(R.id.spinner_encoding)
        spinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.encodings, android.R.layout.simple_spinner_dropdown_item
        )
        input.setText(currentFileName ?: "")

        AlertDialog.Builder(this)
            .setTitle(R.string.save_as)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = normalizeFileName(input.text.toString()) ?: return@setPositiveButton
                val encodingName = spinner.selectedItem.toString()
                lifecycleScope.launch {
                    currentEncoding = Charset.forName(encodingName)
                    withContext(Dispatchers.IO) {
                        repository.saveText(name, editor.text.toString(), currentEncoding)
                    }
                    versionManager.setEncoding(name, encodingName)
                    currentFileName = name
                    isModified = false
                    isReadOnly = false
                    applyReadOnlyState()
                    recentFiles.add(name)
                    refreshRecentList()
                    applyHighlighting() // the extension (and thus the language) may have changed
                    updateStatusBar()
                    invalidateOptionsMenu()
                    toast(getString(R.string.file_saved, name))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Cleans up a file name typed by the user. Returns null (after showing a
     * message) if the name is invalid. Adds ".txt" when no extension is given.
     */
    private fun normalizeFileName(rawName: String): String? {
        var name = rawName.trim()
        if (name.isEmpty() || !name.matches(Regex("^[A-Za-z0-9._ -]+$"))) {
            toast(getString(R.string.invalid_file_name))
            return null
        }
        if (!name.contains(".")) name += ".txt"
        return name
    }

    private fun confirmUnsavedChanges(action: () -> Unit) {
        if (!isModified) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.discard) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Replaces the editor content from code (not counted as a user edit). */
    private fun setEditorText(text: String) {
        suppressTextWatcher = true
        editor.setText(text)
        suppressTextWatcher = false
        undoManager.clear()
        isModified = false
        applyHighlighting()
    }

    private fun refreshRecentList() {
        recentList.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, recentFiles.getAll()
        )
    }

    // =================================================================
    // Undo / redo
    // =================================================================

    private fun undo() {
        if (!undoManager.canUndo()) {
            toast(getString(R.string.nothing_to_undo))
            return
        }
        undoManager.undo(editor.text)
    }

    private fun redo() {
        if (!undoManager.canRedo()) {
            toast(getString(R.string.nothing_to_redo))
            return
        }
        undoManager.redo(editor.text)
    }

    // =================================================================
    // Syntax highlighting
    // =================================================================

    private fun scheduleHighlighting() {
        highlightHandler.removeCallbacks(highlightRunnable)
        highlightHandler.postDelayed(highlightRunnable, HIGHLIGHT_DELAY_MS)
    }

    /** Picks the highlighter based on the file extension. */
    private fun applyHighlighting() {
        val name = currentFileName ?: ""
        when {
            name.endsWith(".kt") || name.endsWith(".kts") ->
                kotlinHighlighter.highlight(editor.text)
            isMarkdownFile() ->
                markdownHighlighter.highlight(editor.text)
            else ->
                SyntaxHighlighter.clearSpans(editor.text) // plain text: no coloring
        }
    }

    private fun isMarkdownFile(): Boolean {
        val name = currentFileName ?: return false
        return name.endsWith(".md") || name.endsWith(".markdown")
    }

    // =================================================================
    // Search and replace
    // =================================================================

    private fun setupSearchPanel() {
        findViewById<View>(R.id.btn_find_next).setOnClickListener { findNext() }
        findViewById<View>(R.id.btn_replace).setOnClickListener { replaceCurrent() }
        findViewById<View>(R.id.btn_replace_all).setOnClickListener { replaceAll() }
        findViewById<View>(R.id.btn_close_search).setOnClickListener {
            searchPanel.visibility = View.GONE
        }
    }

    private fun toggleSearchPanel() {
        searchPanel.visibility =
            if (searchPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (searchPanel.visibility == View.VISIBLE) searchInput.requestFocus()
    }

    /** Finds the next match after the cursor (wrapping around to the top). */
    private fun findNext() {
        val query = searchInput.text.toString()
        if (query.isEmpty()) return
        val content = editor.text.toString()

        var from = editor.selectionEnd
        if (from >= content.length) from = 0
        var index = content.indexOf(query, from, ignoreCase = true)
        if (index == -1) index = content.indexOf(query, 0, ignoreCase = true)

        if (index == -1) {
            toast(getString(R.string.no_matches, query))
        } else {
            editor.requestFocus()
            editor.setSelection(index, index + query.length)
        }
    }

    /** Replaces the currently selected match, then jumps to the next one. */
    private fun replaceCurrent() {
        if (isReadOnly) {
            toast(getString(R.string.file_is_read_only))
            return
        }
        val query = searchInput.text.toString()
        if (query.isEmpty()) return

        val start = editor.selectionStart
        val end = editor.selectionEnd
        val selected = editor.text.subSequence(start, end).toString()
        if (selected.equals(query, ignoreCase = true)) {
            editor.text.replace(start, end, replaceInput.text.toString())
        }
        findNext()
    }

    private fun replaceAll() {
        if (isReadOnly) {
            toast(getString(R.string.file_is_read_only))
            return
        }
        val query = searchInput.text.toString()
        if (query.isEmpty()) return
        val content = editor.text.toString()

        // Count the matches first, so we can report how many were replaced.
        var count = 0
        var index = content.indexOf(query, 0, ignoreCase = true)
        while (index != -1) {
            count++
            index = content.indexOf(query, index + query.length, ignoreCase = true)
        }
        if (count == 0) {
            toast(getString(R.string.no_matches, query))
            return
        }
        val replaced = content.replace(query, replaceInput.text.toString(), ignoreCase = true)
        // Done as ONE text replacement, so a single Undo brings everything back.
        editor.text.replace(0, editor.text.length, replaced)
        toast(getString(R.string.replaced_count, count))
    }

    // =================================================================
    // Word wrap, read-only, Markdown preview
    // =================================================================

    private fun toggleWordWrap() {
        wordWrapEnabled = !wordWrapEnabled
        // No wrapping = let lines run out of the screen and scroll horizontally.
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
        // Persist the flag so the lock survives app restarts.
        lifecycleScope.launch { versionManager.setReadOnly(name, isReadOnly) }
    }

    private fun applyReadOnlyState() {
        // Removing the key listener blocks all typing but still allows
        // scrolling and selecting/copying text.
        editor.keyListener = if (isReadOnly) null else editableKeyListener
    }

    private fun toggleMarkdownPreview() {
        if (isPreviewVisible) {
            hidePreview()
        } else {
            // Markwon converts the Markdown source into styled text.
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

    // =================================================================
    // Version control (snapshots, history, restore)
    // =================================================================

    private fun showSnapshotDialog() {
        val name = currentFileName
        if (name == null) {
            toast(getString(R.string.save_before_snapshot))
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_snapshot, null)
        val input = view.findViewById<EditText>(R.id.input_snapshot_label)
        AlertDialog.Builder(this)
            .setTitle(R.string.create_snapshot)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val label = input.text.toString().trim()
                    .ifEmpty { getString(R.string.snapshot_default_label) }
                lifecycleScope.launch {
                    // The snapshot must match what is on disk, so save first.
                    val text = editor.text.toString()
                    if (!isReadOnly) {
                        withContext(Dispatchers.IO) { repository.saveText(name, text, currentEncoding) }
                        isModified = false
                        updateStatusBar()
                    }
                    val result = versionManager.createSnapshot(name, label, text)
                    toast(
                        if (result.created) getString(R.string.snapshot_created, result.versionNumber)
                        else getString(R.string.snapshot_no_changes)
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openVersionHistory() {
        val name = currentFileName
        if (name == null) {
            toast(getString(R.string.save_before_snapshot))
            return
        }
        val intent = Intent(this, VersionHistoryActivity::class.java)
        intent.putExtra(VersionHistoryActivity.EXTRA_FILE_NAME, name)
        historyLauncher.launch(intent)
    }

    /** Rebuilds the chosen version from the deltas and writes it back to the file. */
    private fun restoreVersion(versionNumber: Int) {
        val name = currentFileName ?: return
        if (isReadOnly) {
            toast(getString(R.string.file_is_read_only))
            return
        }
        lifecycleScope.launch {
            try {
                val text = versionManager.buildVersionText(name, versionNumber)
                setEditorText(text)
                withContext(Dispatchers.IO) { repository.saveText(name, text, currentEncoding) }
                updateStatusBar()
                toast(getString(R.string.restored_version, versionNumber))
            } catch (e: Exception) {
                toast(getString(R.string.restore_failed))
            }
        }
    }

    // =================================================================
    // Crash recovery
    // =================================================================

    /**
     * Called on a fresh start when a backup file exists. That means the app
     * did NOT exit cleanly last time (crash or killed by the system), so we
     * offer to bring the unsaved text back.
     */
    private fun checkForCrashBackup() {
        val backup = autoSave.readBackup() ?: return

        // Skip pointless backups: empty untitled buffer, or identical to the file on disk.
        val isUseful = if (backup.fileName != null && repository.exists(backup.fileName)) {
            try {
                repository.readText(backup.fileName) != backup.text
            } catch (e: Exception) {
                false
            }
        } else {
            backup.text.isNotBlank()
        }
        if (!isUseful) {
            autoSave.clearBackup()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.recovery_title)
            .setMessage(
                getString(
                    R.string.recovery_message,
                    backup.fileName ?: getString(R.string.untitled)
                )
            )
            .setCancelable(false)
            .setPositiveButton(R.string.restore) { _, _ -> restoreBackup(backup) }
            .setNegativeButton(R.string.discard) { _, _ -> autoSave.clearBackup() }
            .show()
    }

    private fun restoreBackup(backup: AutoSaveManager.Backup) {
        lifecycleScope.launch {
            val name = backup.fileName
            if (name != null && repository.exists(name)) {
                val tracked = versionManager.getOrCreateFile(name)
                currentFileName = name
                currentEncoding = Charset.forName(tracked.encoding)
                isReadOnly = tracked.isReadOnly
                applyReadOnlyState()
                recentFiles.add(name)
                refreshRecentList()
            }
            setEditorText(backup.text)
            isModified = true // the recovered text is not saved to the file yet
            autoSave.clearBackup()
            updateStatusBar()
            invalidateOptionsMenu()
            toast(getString(R.string.recovery_done))
        }
    }

    // =================================================================
    // Small helpers
    // =================================================================

    /** Status bar under the toolbar: file name, modified state, lock, encoding. */
    private fun updateStatusBar() {
        val name = currentFileName ?: getString(R.string.untitled)
        val parts = mutableListOf(name)
        parts.add(
            if (isModified) getString(R.string.status_modified)
            else getString(R.string.status_saved)
        )
        if (isReadOnly) parts.add(getString(R.string.status_read_only))
        parts.add(currentEncoding.name())
        statusText.text = parts.joinToString("  •  ")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val HIGHLIGHT_DELAY_MS = 250L
        private const val STATE_FILE_NAME = "state_file_name"
        private const val STATE_ENCODING = "state_encoding"
        private const val STATE_READ_ONLY = "state_read_only"
        private const val STATE_WORD_WRAP = "state_word_wrap"
        private const val STATE_MODIFIED = "state_modified"
    }
}
