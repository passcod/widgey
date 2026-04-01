package com.widgey.ui.nodeselection

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.data.entity.NodeEntity
import com.widgey.data.entity.WidgetConfigEntity
import com.widgey.data.repository.NodeRepository
import com.widgey.databinding.ActivityNodeSelectionBinding
import com.widgey.sync.SyncManager
import com.widgey.widget.WidgetUpdater
import kotlinx.coroutines.launch

class NodeSelectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NodeSelectionActivity"
    }

    private lateinit var binding: ActivityNodeSelectionBinding
    private lateinit var adapter: NodeListAdapter
    private val app: WidgeyApp by lazy { application as WidgeyApp }
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNodeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupUI()
        loadNodes()
    }

    private fun setupUI() {
        // Setup RecyclerView
        adapter = NodeListAdapter { node ->
            selectNode(node.id)
        }
        binding.nodeList.layoutManager = LinearLayoutManager(this)
        binding.nodeList.adapter = adapter

        // Setup create button
        binding.createButton.setOnClickListener {
            showCreateNodeDialog()
        }

        // Setup node ID input
        binding.nodeIdInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val nodeId = binding.nodeIdInput.text?.toString()?.trim()
                if (!nodeId.isNullOrEmpty()) {
                    validateAndSelectNodeId(nodeId)
                }
                true
            } else {
                false
            }
        }

        binding.nodeIdSubmitButton.setOnClickListener {
            val nodeId = binding.nodeIdInput.text?.toString()?.trim()
            if (!nodeId.isNullOrEmpty()) {
                validateAndSelectNodeId(nodeId)
            }
        }

        // Setup retry button
        binding.retryButton.setOnClickListener {
            loadNodes()
        }
    }

    private fun loadNodes() {
        showLoading()

        lifecycleScope.launch {
            Log.d(TAG, "loadNodes: starting, appWidgetId=$appWidgetId")

            // First try to load from cache
            val cachedNodes = app.nodeRepository.getTopLevelNodes()
            Log.d(TAG, "loadNodes: cache has ${cachedNodes.size} top-level nodes")
            if (cachedNodes.isNotEmpty()) {
                showNodes(cachedNodes)
            }

            // Then fetch from API
            Log.d(TAG, "loadNodes: fetching from API")
            when (val result = app.nodeRepository.fetchTopLevelNodes()) {
                is NodeRepository.FetchResult.Success -> {
                    val nodes = app.nodeRepository.getTopLevelNodes()
                    Log.d(TAG, "loadNodes: API fetch succeeded, now have ${nodes.size} nodes")
                    showNodes(nodes)
                }

                is NodeRepository.FetchResult.Unauthorized -> {
                    Log.w(TAG, "loadNodes: API returned Unauthorized - API key missing or invalid")
                    if (cachedNodes.isEmpty()) {
                        showError(getString(R.string.widget_no_api_key))
                    }
                }

                is NodeRepository.FetchResult.NetworkError -> {
                    val r = result as NodeRepository.FetchResult.NetworkError
                    Log.e(TAG, "loadNodes: network error", r.exception)
                    if (cachedNodes.isEmpty()) {
                        showError(getString(R.string.error_network))
                    }
                }

                is NodeRepository.FetchResult.NotFound -> {
                    Log.e(TAG, "loadNodes: API returned NotFound for top-level nodes")
                    if (cachedNodes.isEmpty()) {
                        showError(getString(R.string.node_selection_error))
                    }
                }

                is NodeRepository.FetchResult.Error -> {
                    Log.e(TAG, "loadNodes: API error: ${result.message}")
                    if (cachedNodes.isEmpty()) {
                        showError(getString(R.string.node_selection_error))
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.nodeList.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
    }

    private fun showNodes(nodes: List<NodeEntity>) {
        binding.progressBar.visibility = View.GONE
        binding.errorView.visibility = View.GONE

        if (nodes.isEmpty()) {
            binding.nodeList.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.nodeList.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            adapter.submitList(nodes.sortedBy { it.priority })
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.nodeList.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    private fun selectNode(nodeId: String) {
        lifecycleScope.launch {
            // Save widget configuration
            app.database.widgetConfigDao().insert(
                WidgetConfigEntity(widgetId = appWidgetId, nodeId = nodeId)
            )

            // Fetch the node content if not already cached
            val node = app.database.nodeDao().getById(nodeId)
            if (node == null) {
                app.nodeRepository.fetchNode(nodeId)
            }

            // Update widget
            WidgetUpdater.updateWidget(this@NodeSelectionActivity, appWidgetId)

            // Start periodic sync if not already running
            SyncManager(this@NodeSelectionActivity).startPeriodicSync()

            setResult(RESULT_OK)
            finish()
        }
    }

    private fun validateAndSelectNodeId(nodeId: String) {
        binding.nodeIdInputLayout.error = null
        binding.nodeIdSubmitButton.isEnabled = false

        lifecycleScope.launch {
            Log.d(TAG, "validateAndSelectNodeId: fetching node $nodeId")
            when (val result = app.nodeRepository.fetchNode(nodeId)) {
                is NodeRepository.FetchResult.Success -> {
                    Log.d(TAG, "validateAndSelectNodeId: node $nodeId found, selecting")
                    selectNode(nodeId)
                }

                is NodeRepository.FetchResult.NotFound -> {
                    Log.w(TAG, "validateAndSelectNodeId: node $nodeId not found")
                    binding.nodeIdInputLayout.error = getString(R.string.node_id_invalid)
                    binding.nodeIdSubmitButton.isEnabled = true
                }

                is NodeRepository.FetchResult.Unauthorized -> {
                    Log.w(TAG, "validateAndSelectNodeId: unauthorized")
                    binding.nodeIdInputLayout.error = getString(R.string.widget_no_api_key)
                    binding.nodeIdSubmitButton.isEnabled = true
                }

                is NodeRepository.FetchResult.NetworkError -> {
                    Log.e(TAG, "validateAndSelectNodeId: network error", result.exception)
                    binding.nodeIdInputLayout.error = getString(R.string.error_network)
                    binding.nodeIdSubmitButton.isEnabled = true
                }

                is NodeRepository.FetchResult.Error -> {
                    Log.e(TAG, "validateAndSelectNodeId: error: ${result.message}")
                    binding.nodeIdInputLayout.error = getString(R.string.error_network)
                    binding.nodeIdSubmitButton.isEnabled = true
                }
            }
        }
    }

    private fun showCreateNodeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_node, null)
        val nameInput =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.node_name_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.node_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    createNode(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createNode(name: String) {
        showLoading()

        lifecycleScope.launch {
            when (val result = app.nodeRepository.createTopLevelNode(name)) {
                is NodeRepository.CreateResult.Success -> {
                    selectNode(result.nodeId)
                }

                is NodeRepository.CreateResult.Unauthorized -> {
                    showError(getString(R.string.widget_no_api_key))
                    Toast.makeText(
                        this@NodeSelectionActivity,
                        R.string.widget_no_api_key,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is NodeRepository.CreateResult.NetworkError -> {
                    loadNodes() // Go back to list view
                    Toast.makeText(
                        this@NodeSelectionActivity,
                        R.string.error_network,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is NodeRepository.CreateResult.Error -> {
                    loadNodes()
                    Toast.makeText(
                        this@NodeSelectionActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
