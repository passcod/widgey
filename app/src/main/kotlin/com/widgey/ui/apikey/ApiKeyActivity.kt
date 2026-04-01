package com.widgey.ui.apikey

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.data.api.WorkflowyApi
import com.widgey.databinding.ActivityApiKeyBinding
import com.widgey.widget.WidgetUpdater
import kotlinx.coroutines.launch

class ApiKeyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_WIDGET_CONFIG = "from_widget_config"
    }

    private lateinit var binding: ActivityApiKeyBinding
    private val app: WidgeyApp by lazy { application as WidgeyApp }
    private var isFromWidgetConfig = false
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApiKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isFromWidgetConfig = intent.getBooleanExtra(EXTRA_FROM_WIDGET_CONFIG, false)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        setupUI()
        if (isFromWidgetConfig) {
            loadExistingKey()
        } else {
            loadStandaloneState()
        }
    }

    private fun setupUI() {
        binding.validateButton.setOnClickListener {
            val apiKey = binding.apiKeyInput.text?.toString()?.trim()
            if (apiKey.isNullOrEmpty()) {
                binding.apiKeyInputLayout.error = getString(R.string.api_key_invalid)
                return@setOnClickListener
            }
            validateAndSaveKey(apiKey)
        }

        binding.clearButton.setOnClickListener {
            showClearConfirmation()
        }

        binding.changeButton.setOnClickListener {
            showEntryForm()
        }

        binding.apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.apiKeyInputLayout.error = null
            }
        }
    }

    private fun loadExistingKey() {
        lifecycleScope.launch {
            val hasKey = app.settingsRepository.hasApiKey()
            binding.clearButton.visibility = if (hasKey) View.VISIBLE else View.GONE

            if (hasKey) {
                // Show masked key
                binding.apiKeyInput.hint = "••••••••••••••••"
            }
        }
    }

    private fun loadStandaloneState() {
        lifecycleScope.launch {
            val hasKey = app.settingsRepository.hasApiKey()
            if (hasKey) {
                showManagementUI()
            } else {
                showEntryForm()
            }
        }
    }

    private fun showManagementUI() {
        binding.titleText.text = getString(R.string.api_key_set_title)
        binding.instructionsText.text = getString(R.string.api_key_set_instructions)
        binding.apiKeyInputLayout.visibility = View.GONE
        binding.validateButton.visibility = View.GONE
        binding.changeButton.visibility = View.VISIBLE
        binding.clearButton.visibility = View.VISIBLE
    }

    private fun showEntryForm() {
        binding.titleText.text = getString(R.string.api_key_title)
        binding.instructionsText.text = getString(R.string.api_key_instructions)
        binding.apiKeyInputLayout.visibility = View.VISIBLE
        binding.validateButton.visibility = View.VISIBLE
        binding.changeButton.visibility = View.GONE
        binding.clearButton.visibility = View.GONE
    }

    private fun validateAndSaveKey(apiKey: String) {
        binding.validateButton.isEnabled = false
        binding.validateButton.text = getString(R.string.api_key_validating)
        binding.apiKeyInputLayout.error = null

        lifecycleScope.launch {
            // Create a temporary API instance for validation
            val tempApi = WorkflowyApi { apiKey }

            when (val result = tempApi.validateApiKey(apiKey)) {
                is WorkflowyApi.ApiResult.Success -> {
                    // Save the key
                    app.settingsRepository.setApiKey(apiKey)

                    Toast.makeText(
                        this@ApiKeyActivity,
                        R.string.api_key_success,
                        Toast.LENGTH_SHORT
                    ).show()

                    // Update all widgets
                    WidgetUpdater.updateAllWidgets(this@ApiKeyActivity)

                    setResult(RESULT_OK)
                    finish()
                }

                is WorkflowyApi.ApiResult.Unauthorized -> {
                    binding.apiKeyInputLayout.error = getString(R.string.api_key_invalid)
                    resetButton()
                }

                is WorkflowyApi.ApiResult.NetworkError -> {
                    binding.apiKeyInputLayout.error = getString(R.string.error_network)
                    resetButton()
                }

                else -> {
                    binding.apiKeyInputLayout.error = getString(R.string.error_unknown)
                    resetButton()
                }
            }
        }
    }

    private fun resetButton() {
        binding.validateButton.isEnabled = true
        binding.validateButton.text = getString(R.string.api_key_validate)
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.api_key_clear)
            .setMessage(R.string.api_key_clear_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                clearApiKey()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearApiKey() {
        lifecycleScope.launch {
            app.settingsRepository.clearApiKey()
            binding.clearButton.visibility = View.GONE
            binding.apiKeyInput.text?.clear()
            binding.apiKeyInput.hint = getString(R.string.api_key_hint)

            // Update all widgets to show error state
            WidgetUpdater.updateAllWidgets(this@ApiKeyActivity)

            Toast.makeText(
                this@ApiKeyActivity,
                "API key cleared",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onBackPressed() {
        if (isFromWidgetConfig) {
            // If we came from widget config and no key is set, cancel
            lifecycleScope.launch {
                if (!app.settingsRepository.hasApiKey()) {
                    setResult(RESULT_CANCELED)
                }
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }
}
