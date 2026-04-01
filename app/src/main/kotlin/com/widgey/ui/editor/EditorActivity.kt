package com.widgey.ui.editor

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.graphics.Typeface
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.data.entity.WidgetConfigEntity
import com.widgey.data.repository.NodeRepository
import com.widgey.databinding.ActivityEditorBinding
import com.widgey.sync.SyncManager
import com.widgey.util.HtmlFormatter
import com.widgey.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NODE_ID = "node_id"
        private const val SAVE_DEBOUNCE_MS = 500L
    }

    private lateinit var binding: ActivityEditorBinding
    private val app: WidgeyApp by lazy { application as WidgeyApp }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var nodeId: String? = null
    private var isLoading = true
    private var pendingSave = false

    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var observeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        nodeId = intent.getStringExtra(EXTRA_NODE_ID)

        if (nodeId == null) {
            finish()
            return
        }

        setupUI()
        loadNode()
        observeSyncStatus()
    }

    private fun setupUI() {
        binding.noteInput.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        binding.noteInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isLoading) scheduleSave()
            }
        })

        binding.backButton.setOnClickListener { onBackPressed() }
        binding.syncStatus.setOnClickListener { syncFromRemote() }

        binding.formatBold.setOnClickListener { toggleSpan<StyleSpan> { StyleSpan(Typeface.BOLD) } }
        binding.formatItalic.setOnClickListener { toggleSpan<StyleSpan> { StyleSpan(Typeface.ITALIC) } }
        binding.formatStrikethrough.setOnClickListener { toggleSpan<StrikethroughSpan> { StrikethroughSpan() } }
        binding.formatCode.setOnClickListener { toggleSpan<TypefaceSpan> { TypefaceSpan("monospace") } }
        binding.formatLink.setOnClickListener { showLinkDialog() }
    }

    // Toggle a span over the current selection. Uses the reified type T to find and remove
    // existing spans of the same type, or adds a new one if none exist.
    private inline fun <reified T : Any> toggleSpan(crossinline create: () -> T) {
        val start = binding.noteInput.selectionStart.coerceAtLeast(0)
        val end = binding.noteInput.selectionEnd.coerceAtLeast(0)
        if (start == end) return

        val text = binding.noteInput.editableText
        val existing = text.getSpans(start, end, T::class.java)
        if (existing.isNotEmpty()) {
            existing.forEach { text.removeSpan(it) }
        } else {
            text.setSpan(create(), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        }
        scheduleSave()
    }

    private fun showLinkDialog() {
        val start = binding.noteInput.selectionStart.coerceAtLeast(0)
        val end = binding.noteInput.selectionEnd.coerceAtLeast(0)
        if (start == end) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_insert_link, null)
        val urlInput = dialogView.findViewById<TextInputEditText>(R.id.link_url_input)

        // Pre-fill if there's an existing URLSpan on the selection
        val text = binding.noteInput.editableText
        text.getSpans(start, end, URLSpan::class.java).firstOrNull()?.let {
            urlInput.setText(it.url)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.format_link_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = urlInput.text?.toString()?.trim() ?: return@setPositiveButton
                // Remove any existing URLSpans in range
                text.getSpans(start, end, URLSpan::class.java).forEach { text.removeSpan(it) }
                if (url.isNotEmpty()) {
                    text.setSpan(URLSpan(url), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                }
                scheduleSave()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadNode() {
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.noteInput.isEnabled = false

        lifecycleScope.launch {
            val node = app.database.nodeDao().getById(nodeId!!)

            if (node != null) {
                binding.nodeTitle.text = HtmlFormatter.stripHtml(node.name).ifEmpty { getString(R.string.editor_title) }
                binding.noteInput.setText(HtmlFormatter.toSpanned(node.note), android.widget.TextView.BufferType.EDITABLE)
                binding.noteInput.isEnabled = true
                binding.progressBar.visibility = View.GONE
                isLoading = false
                binding.noteInput.setSelection(0)
                syncFromRemote()
            } else {
                when (val result = app.nodeRepository.fetchNode(nodeId!!)) {
                    is NodeRepository.FetchResult.Success -> {
                        val fetchedNode = app.database.nodeDao().getById(nodeId!!)
                        if (fetchedNode != null) {
                            binding.nodeTitle.text = HtmlFormatter.stripHtml(fetchedNode.name).ifEmpty { getString(R.string.editor_title) }
                            binding.noteInput.setText(HtmlFormatter.toSpanned(fetchedNode.note), android.widget.TextView.BufferType.EDITABLE)
                            binding.noteInput.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            isLoading = false
                            binding.noteInput.setSelection(0)
                        }
                    }
                    is NodeRepository.FetchResult.NotFound -> showNodeDeletedDialog()
                    else -> {
                        binding.progressBar.visibility = View.GONE
                        binding.noteInput.isEnabled = true
                        isLoading = false
                        updateSyncStatus(SyncStatus.ERROR)
                    }
                }
            }
        }
    }

    private fun syncFromRemote() {
        val currentNodeId = nodeId ?: return
        updateSyncStatus(SyncStatus.SYNCING)
        lifecycleScope.launch {
            when (val result = app.nodeRepository.fetchNode(currentNodeId)) {
                is NodeRepository.FetchResult.Success -> {
                    // Re-read from DB; if remote was newer and we have no local edits,
                    // refresh the editor content without triggering another save
                    val updated = app.database.nodeDao().getById(currentNodeId)
                    if (updated != null) {
                        binding.nodeTitle.text = HtmlFormatter.stripHtml(updated.name).ifEmpty { getString(R.string.editor_title) }
                        if (!updated.isDirty) {
                            val currentText = HtmlFormatter.toHtml(binding.noteInput.editableText)
                            if (updated.note != currentText) {
                                isLoading = true
                                binding.noteInput.setText(
                                    HtmlFormatter.toSpanned(updated.note),
                                    android.widget.TextView.BufferType.EDITABLE
                                )
                                binding.noteInput.setSelection(0)
                                isLoading = false
                                WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)
                            }
                        }
                    }
                    updateSyncStatus(SyncStatus.SYNCED)
                }
                is NodeRepository.FetchResult.NotFound -> showNodeDeletedDialog()
                else -> updateSyncStatus(SyncStatus.ERROR)
            }
        }
    }

    private fun showNodeDeletedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.node_deleted_title)
            .setMessage(R.string.node_deleted_message)
            .setPositiveButton(R.string.node_deleted_recreate) { _, _ -> recreateNode() }
            .setNegativeButton(R.string.node_deleted_discard) { _, _ -> showDiscardConfirmation() }
            .setCancelable(false)
            .show()
    }

    private fun showDiscardConfirmation() {
        AlertDialog.Builder(this)
            .setMessage(R.string.node_deleted_discard_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> discardWidget() }
            .setNegativeButton(R.string.cancel) { _, _ -> showNodeDeletedDialog() }
            .show()
    }

    private fun recreateNode() {
        binding.progressBar.visibility = View.VISIBLE
        binding.noteInput.isEnabled = false

        lifecycleScope.launch {
            val cachedNode = app.database.nodeDao().getById(nodeId!!)
            val name = cachedNode?.name ?: "Untitled"
            val note = HtmlFormatter.toHtml(binding.noteInput.editableText)

            when (val result = app.nodeRepository.createTopLevelNode(name, note)) {
                is NodeRepository.CreateResult.Success -> {
                    val newNodeId = result.nodeId
                    app.database.widgetConfigDao().updateNodeId(appWidgetId, newNodeId)
                    nodeId = newNodeId
                    WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)
                    binding.progressBar.visibility = View.GONE
                    binding.noteInput.isEnabled = true
                    isLoading = false
                    updateSyncStatus(SyncStatus.SYNCED)
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    updateSyncStatus(SyncStatus.ERROR)
                    showNodeDeletedDialog()
                }
            }
        }
    }

    private fun discardWidget() {
        lifecycleScope.launch {
            app.database.widgetConfigDao().insert(
                WidgetConfigEntity(widgetId = appWidgetId, nodeId = null)
            )
            WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)
            finish()
        }
    }

    private fun scheduleSave() {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
        updateSyncStatus(SyncStatus.PENDING)
        pendingSave = true
        saveRunnable = Runnable { saveNote() }
        saveHandler.postDelayed(saveRunnable!!, SAVE_DEBOUNCE_MS)
    }

    private fun saveNote() {
        val note = HtmlFormatter.toHtml(binding.noteInput.editableText)
        val currentNodeId = nodeId ?: return

        pendingSave = false
        updateSyncStatus(SyncStatus.SYNCING)

        lifecycleScope.launch {
            app.nodeRepository.updateNoteLocally(currentNodeId, note)
            WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)
            SyncManager(this@EditorActivity).triggerPushSync()
        }
    }

    private fun observeSyncStatus() {
        observeJob = lifecycleScope.launch {
            app.database.syncQueueDao().observeCount().collectLatest { count ->
                if (!pendingSave) {
                    val node = app.database.nodeDao().getById(nodeId ?: return@collectLatest)
                    when {
                        node?.isDirty == true -> updateSyncStatus(SyncStatus.PENDING)
                        count > 0 -> updateSyncStatus(SyncStatus.PENDING)
                        else -> updateSyncStatus(SyncStatus.SYNCED)
                    }
                }
            }
        }
    }

    private fun updateSyncStatus(status: SyncStatus) {
        val (text, color) = when (status) {
            SyncStatus.SYNCED -> getString(R.string.editor_sync_status_synced) to R.color.sync_success
            SyncStatus.SYNCING -> getString(R.string.editor_sync_status_syncing) to R.color.sync_pending
            SyncStatus.PENDING -> getString(R.string.editor_sync_status_pending) to R.color.sync_pending
            SyncStatus.ERROR -> getString(R.string.editor_sync_status_error) to R.color.sync_error
        }
        binding.syncStatus.text = text
        binding.syncStatus.setTextColor(getColor(color))
    }

    override fun onPause() {
        super.onPause()
        if (pendingSave) {
            saveRunnable?.let { saveHandler.removeCallbacks(it) }
            saveNote()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
    }

    private enum class SyncStatus { SYNCED, SYNCING, PENDING, ERROR }
}
