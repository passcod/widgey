package com.widgey.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.widgey.WidgeyApp
import com.widgey.ui.apikey.ApiKeyActivity
import com.widgey.ui.nodeselection.NodeSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Configuration activity launched when a widget is first added.
 * Handles the flow: API key setup (if needed) -> Node selection -> Widget creation
 */
class WidgetConfigActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_API_KEY = 1
        private const val REQUEST_NODE_SELECTION = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        // Get the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Check if we have an API key, if not, launch API key setup
        checkApiKeyAndProceed()
    }

    private fun checkApiKeyAndProceed() {
        val app = application as WidgeyApp
        scope.launch {
            val hasApiKey = app.settingsRepository.hasApiKey()
            if (hasApiKey) {
                // Go directly to node selection
                launchNodeSelection()
            } else {
                // Need to set up API key first
                launchApiKeySetup()
            }
        }
    }

    private fun launchApiKeySetup() {
        val intent = Intent(this, ApiKeyActivity::class.java).apply {
            putExtra(ApiKeyActivity.EXTRA_FROM_WIDGET_CONFIG, true)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(intent, REQUEST_API_KEY)
    }

    private fun launchNodeSelection() {
        val intent = Intent(this, NodeSelectionActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(intent, REQUEST_NODE_SELECTION)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_API_KEY -> {
                if (resultCode == RESULT_OK) {
                    // API key was set, now proceed to node selection
                    launchNodeSelection()
                } else {
                    // User cancelled API key setup, cancel widget creation
                    finish()
                }
            }

            REQUEST_NODE_SELECTION -> {
                if (resultCode == RESULT_OK) {
                    // Node was selected, widget is configured
                    val resultValue = Intent().apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    setResult(RESULT_OK, resultValue)

                    // Update the widget
                    WidgetUpdater.updateWidget(this, appWidgetId)
                }
                // Either way (OK or cancelled), finish the config activity
                finish()
            }
        }
    }
}
