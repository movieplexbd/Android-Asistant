package com.assistant.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.assistant.android.R
import com.assistant.android.ai.GeminiClient
import com.assistant.android.core.ApiKeyManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user paste their own Gemini API key, test it, auto-detect a working model,
 * and save. This is what unblocks the app when the bundled key has been disabled by Google.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var keyEdit: EditText
    private lateinit var modelEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var testButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var detectButton: MaterialButton
    private lateinit var openButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        keyEdit = findViewById(R.id.apiKeyEdit)
        modelEdit = findViewById(R.id.modelEdit)
        statusText = findViewById(R.id.statusText)
        progress = findViewById(R.id.progress)
        testButton = findViewById(R.id.testButton)
        saveButton = findViewById(R.id.saveButton)
        detectButton = findViewById(R.id.detectButton)
        openButton = findViewById(R.id.openAiStudioButton)

        // Pre-fill with current values
        if (ApiKeyManager.hasUserKey(this)) {
            keyEdit.setText(ApiKeyManager.getApiKey(this))
        }
        modelEdit.setText(ApiKeyManager.getModel(this))

        testButton.setOnClickListener { runTest() }
        detectButton.setOnClickListener { autoDetect() }
        saveButton.setOnClickListener { save() }
        openButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }
        findViewById<MaterialButton>(R.id.copyStatusButton).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Jarvis status", statusText.text.toString()))
            Toast.makeText(this, "Status copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        testButton.isEnabled = !busy
        detectButton.isEnabled = !busy
        saveButton.isEnabled = !busy
    }

    private fun runTest() {
        val key = keyEdit.text.toString().trim()
        val model = modelEdit.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
        if (key.isBlank()) {
            statusText.text = "Please paste a key first."
            return
        }
        setBusy(true)
        statusText.text = "Testing key + model '$model'..."
        lifecycleScope.launch {
            val client = GeminiClient(key, model)
            val result = withContext(Dispatchers.IO) { client.ping() }
            setBusy(false)
            statusText.text = when (result) {
                is GeminiClient.Result.Success ->
                    "✓ Working!  Model='$model'  Reply: ${result.text.take(100)}"
                is GeminiClient.Result.Failure ->
                    "✗ ${result.short}\n\n— Detail —\n${result.detail.take(1500)}"
            }
        }
    }

    private fun autoDetect() {
        val key = keyEdit.text.toString().trim()
        if (key.isBlank()) {
            statusText.text = "Paste a key first."
            return
        }
        setBusy(true)
        statusText.text = "Listing models for this key..."
        lifecycleScope.launch {
            val client = GeminiClient(key, "gemini-2.5-flash")
            val models = withContext(Dispatchers.IO) { client.listModels() }
            if (models.isEmpty()) {
                setBusy(false)
                statusText.text = "Could not list models — key may be invalid or no internet.\nTry the Test button to see the exact error."
                return@launch
            }
            // Pick first 'flash' model that's not deprecated/preview
            val preferred = models.firstOrNull {
                it.contains("flash", true) && !it.contains("deprecated", true) && !it.contains("preview", true)
            } ?: models.firstOrNull { it.contains("flash", true) }
              ?: models.first()
            statusText.text = "Found ${models.size} models. Trying '$preferred'..."
            client.modelName = preferred
            val ping = withContext(Dispatchers.IO) { client.ping() }
            setBusy(false)
            when (ping) {
                is GeminiClient.Result.Success -> {
                    modelEdit.setText(preferred)
                    statusText.text = "✓ Auto-detected working model: $preferred\n\nAvailable models:\n" +
                        models.joinToString("\n") { "  • $it" }
                }
                is GeminiClient.Result.Failure -> {
                    statusText.text = "Model '$preferred' picked but failed: ${ping.short}\n\nAll listed models:\n" +
                        models.joinToString("\n") { "  • $it" }
                }
            }
        }
    }

    private fun save() {
        val key = keyEdit.text.toString().trim()
        val model = modelEdit.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
        if (key.isBlank()) {
            statusText.text = "Cannot save — paste a key first."
            return
        }
        ApiKeyManager.setApiKey(this, key)
        ApiKeyManager.setModel(this, model)
        Toast.makeText(this, "Saved. Restart the assistant for it to pick up the new key.", Toast.LENGTH_LONG).show()
        statusText.text = "✓ Saved. Key=${ApiKeyManager.mask(key)}  Model=$model\nNow tap 'Stop Assistant' then 'Start Assistant' on the main screen."
    }
}
