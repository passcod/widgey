package com.widgey.ui.editor

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.data.entity.WidgetConfigEntity
import com.widgey.data.repository.NodeRepository
import com.widgey.databinding.ActivityEditorBinding
import com.widgey.sync.SyncManager
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
        binding.noteInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isLoading) {
                    scheduleSave()
                }
            }
        })

        binding.backButton.setOnClickListener {
            onBackPressed()
        }
    }

    private fun loadNode() {
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.noteInput.isEnabled = false

        lifecycleScope.launch {
            val node = app.database.nodeDao().getById(nodeId!!)

            if (node != null) {
                binding.nodeTitle.text = node.name.ifEmpty { getString(R.string.editor_title) }
                binding.noteInput.setText(node.note ?: "")
                binding.noteInput.isEnabled = true
                binding.progressBar.visibility = View.GONE
                isLoading = false

                // Move cursor to end
                binding.noteInput.setSelection(binding.noteInput.text?.length ?: 0)

                // Check if node exists remotely
                checkNodeExistsRemotely()
            } else {
                // Try to fetch from API
                when (val result = app.nodeRepository.fetchNode(nodeId!!)) {
                    is NodeRepository.FetchResult.Success -> {
                        val fetchedNode = app.database.nodeDao().getById(nodeId!!)
                        if (fetchedNode != null) {
                            binding.nodeTitle.text = fetchedNode.name.ifEmpty { getString(R.string.editor_title) }
                            binding.noteInput.setText(fetchedNode.note ?: "")
                            binding.noteInput.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            isLoading = false
                            binding.noteInput.setSelection(binding.noteInput.text?.length ?: 0)
                        }
                    }

                    is NodeRepository.FetchResult.NotFound -> {
                        showNodeDeletedDialog()
                    }

                    else -> {
                        // Show error but allow editing if we have cached content
                        binding.progressBar.visibility = View.GONE
                        binding.noteInput.isEnabled = true
                        isLoading = false
                        updateSyncStatus(SyncStatus.ERROR)
                    }
                }
            }
        }
    }

    private fun checkNodeExistsRemotely() {
        lifecycleScope.launch {
            val exists = app.nodeRepository.nodeExistsRemotely(nodeId!!)
            if (!exists) {
                // Node was deleted on server
                showNodeDeletedDialog()
            }
        }
    }

    private fun showNodeDeletedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.node_deleted_title)
            .setMessage(R.string.node_deleted_message)
            .setPositiveButton(R.string.node_deleted_recreate) { _, _ ->
                recreateNode()
            }
            .setNegativeButton(R.string.node_deleted_discard) { _, _ ->
                showDiscardConfirmation()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDiscardConfirmation() {
        AlertDialog.Builder(this)
            .setMessage(R.string.node_deleted_discard_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                discardWidget()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                showNodeDeletedDialog()
            }
            .show()
    }

    private fun recreateNode() {
        binding.progressBar.visibility = View.VISIBLE
        binding.noteInput.isEnabled = false

        lifecycleScope.launch {
            val cachedNode = app.database.nodeDao().getById(nodeId!!)
            val name = cachedNode?.name ?: "Untitled"
            val note = binding.noteInput.text?.toString() ?: cachedNode?.note

            when (val result = app.nodeRepository.createTopLevelNode(name, note)) {
                is NodeRepository.CreateResult.Success -> {
                    // Update widget config with new node ID
                    val newNodeId = result.nodeId
                    app.database.widgetConfigDao().updateNodeId(appWidgetId, newNodeId)

                    // Update our reference
                    nodeId = newNodeId

                    // Update widget
                    WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)

                    binding.progressBar.visibility = View.GONE
                    binding.noteInput.isEnabled = true
                    isLoading = false

                    updateSyncStatus(SyncStatus.SYNCED)
                }

                else -> {
                    // Failed to recreate - show error
                    binding.progressBar.visibility = View.GONE
                    updateSyncStatus(SyncStatus.ERROR)
                    showNodeDeletedDialog()
                }
            }
        }
    }

    private fun discardWidget() {
        lifecycleScope.launch {
            // Clear the node association but keep the widget
            app.database.widgetConfigDao().insert(
                WidgetConfigEntity(widgetId = appWidgetId, nodeId = null)
            )

            // Update widget to show empty state
            WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)

            finish()
        }
    }

    private fun scheduleSave() {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }

        updateSyncStatus(SyncStatus.PENDING)
        pendingSave = true

        saveRunnable = Runnable {
            saveNote()
        }
        saveHandler.postDelayed(saveRunnable!!, SAVE_DEBOUNCE_MS)
    }

    private fun saveNote() {
        val note = binding.noteInput.text?.toString()
        val currentNodeId = nodeId ?: return

        pendingSave = false
        updateSyncStatus(SyncStatus.SYNCING)

        lifecycleScope.launch {
            // Save locally
            app.nodeRepository.updateNoteLocally(currentNodeId, note)

            // Update widget
            WidgetUpdater.updateWidget(this@EditorActivity, appWidgetId)

            // Trigger sync
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
        // Save immediately when leaving
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

    private enum class SyncStatus {
        SYNCED, SYNCING, PENDING, ERROR
    }
}
