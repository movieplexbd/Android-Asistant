package com.assistant.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.assistant.android.R
import com.assistant.android.core.MasterController
import com.assistant.android.service.ForegroundService
import com.assistant.android.service.OverlayService
import com.assistant.android.service.WakeWordService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var statsTextView: TextView
    private lateinit var voiceButton: MaterialButton
    private lateinit var wakeWordButton: MaterialButton
    private lateinit var overlayButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        statsTextView = findViewById(R.id.statsTextView)
        voiceButton = findViewById(R.id.voiceButton)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        overlayButton = findViewById(R.id.overlayButton)

        checkPermissions()

        voiceButton.setOnClickListener { toggleService(ForegroundService::class.java, voiceButton, "Assistant") }
        wakeWordButton.setOnClickListener { toggleService(WakeWordService::class.java, wakeWordButton, "Wake Word") }
        overlayButton.setOnClickListener { toggleOverlay() }

        observeMaster()
    }

    private fun observeMaster() {
        lifecycleScope.launch {
            MasterController.state.collect { state ->
                statusTextView.text = "State: $state"
            }
        }
        lifecycleScope.launch {
            MasterController.stats.collect { s ->
                statsTextView.text = "Commands: ${s.commandsHandled}  •  Routines: ${s.routinesRun}  •  Translations: ${s.translationsDone}  •  Errors: ${s.errors}"
            }
        }
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) needed.add(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Toast.makeText(this, if (granted) "Permissions granted" else "Some permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleService(cls: Class<*>, button: MaterialButton, name: String) {
        val serviceIntent = Intent(this, cls)
        if (isServiceRunning(cls)) {
            stopService(serviceIntent)
            button.text = "Start $name"
        } else {
            ContextCompat.startForegroundService(this, serviceIntent)
            button.text = "Stop $name"
        }
    }

    private fun toggleOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        toggleService(OverlayService::class.java, overlayButton, "Overlay")
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className == serviceClass.name }
    }

    companion object {
        private const val REQ_PERMS = 1001
    }
}
