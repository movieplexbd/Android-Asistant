package com.assistant.android.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.assistant.android.R
import com.assistant.android.ai.GeminiClient
import com.assistant.android.core.ApiKeyManager
import kotlinx.coroutines.launch

/**
 * Settings: paste one or more Gemini API keys (newline / comma / semicolon separated)
 * and pick the default model. Includes a Test button that pings the first key live.
 *
 * v4.4: multi-key support so the orchestrator can rotate when one key hits its
 * 20-req/day free-tier quota.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var keyEdit: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var keyCountText: TextView

    private val models = listOf(
        "gemini-2.5-flash"  to "Gemini 2.5 Flash (recommended)",
        "gemini-2.5-pro"    to "Gemini 2.5 Pro (slower, smarter)",
        "gemini-2.0-flash"  to "Gemini 2.0 Flash",
        "gemini-1.5-flash"  to "Gemini 1.5 Flash",
        "gemini-1.5-flash-8b" to "Gemini 1.5 Flash 8B (lightest)",
        "gemini-1.5-pro"    to "Gemini 1.5 Pro"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        keyEdit = findViewById(R.id.api_key_edit)
        modelSpinner = findViewById(R.id.model_spinner)
        statusText = findViewById(R.id.status_text)
        keyCountText = findViewById(R.id.key_count_text)

        val saveBtn = findViewById<Button>(R.id.save_btn)
        val testBtn = findViewById<Button>(R.id.test_btn)
        val clearBtn = findViewById<Button>(R.id.clear_btn)

        // Pre-fill: each saved key on its own line.
        keyEdit.setText(ApiKeyManager.getAllKeys(this).joinToString("\n"))

        ArrayAdapter(this, android.R.layout.simple_spinner_item, models.map { it.second }).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = adapter
        }
        val current = ApiKeyManager.getModel(this)
        val idx = models.indexOfFirst { it.first == current }.coerceAtLeast(0)
        modelSpinner.setSelection(idx)

        updateStatusBadge()

        saveBtn.setOnClickListener {
            val raw = keyEdit.text.toString().trim()
            if (raw.isEmpty()) {
                Toast.makeText(this, "Paste at least one Gemini API key", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ApiKeyManager.setApiKeys(this, raw)
            ApiKeyManager.setModel(this, models[modelSpinner.selectedItemPosition].first)
            updateStatusBadge()
            Toast.makeText(this, "Saved ${ApiKeyManager.getAllKeys(this).size} key(s)", Toast.LENGTH_SHORT).show()
        }

        testBtn.setOnClickListener {
            val keys = parseKeys(keyEdit.text.toString())
            if (keys.isEmpty()) {
                statusText.text = "No keys entered."
                return@setOnClickListener
            }
            statusText.text = "Testing first key (${ApiKeyManager.mask(keys.first())}) …"
            val model = models[modelSpinner.selectedItemPosition].first
            lifecycleScope.launch {
                val client = GeminiClient(keys.first(), model)
                val result = client.ping()
                runOnUiThread {
                    statusText.text = when (result) {
                        is GeminiClient.Result.Success -> "✓ Key works: ${result.text.take(80)}"
                        is GeminiClient.Result.Failure ->
                            "✗ ${result.short}\nDetails: ${result.detail.take(220)}"
                    }
                }
            }
        }

        clearBtn.setOnClickListener {
            ApiKeyManager.clearApiKey(this)
            keyEdit.setText("")
            updateStatusBadge()
            statusText.text = "Saved keys cleared."
        }
    }

    private fun parseKeys(raw: String): List<String> =
        raw.split("\n", ",", ";").map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun updateStatusBadge() {
        val n = ApiKeyManager.getAllKeys(this).size
        keyCountText.text = when (n) {
            0 -> "No keys saved (using build-time fallback only)"
            1 -> "1 key saved — add more to extend free-tier quota"
            else -> "$n keys saved — orchestrator will rotate on 429 errors"
        }
        keyCountText.visibility = View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
