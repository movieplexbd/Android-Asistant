package com.assistant.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.assistant.android.R
import com.assistant.android.service.ForegroundService
import com.assistant.android.voice.SpeechRecognizerManager
import com.assistant.android.voice.TTSManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var voiceButton: Button

    private val RECORD_AUDIO_PERMISSION_CODE = 1
    private val REQUEST_PHONE_CALL_PERMISSION = 2
    private val REQUEST_SEND_SMS_PERMISSION = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        voiceButton = findViewById(R.id.voiceButton)

        checkPermissions()

        voiceButton.setOnClickListener {
            toggleService()
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
        }
        // SYSTEM_ALERT_WINDOW and ACCESSIBILITY_SERVICE need special handling, not runtime permissions

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), RECORD_AUDIO_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permissions denied. Some features may not work.", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

    private fun toggleService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        if (isServiceRunning(ForegroundService::class.java)) {
            stopService(serviceIntent)
            statusTextView.text = "Service Stopped"
            voiceButton.text = "Start Assistant"
        } else {
            ContextCompat.startForegroundService(this, serviceIntent)
            statusTextView.text = "Service Running"
            voiceButton.text = "Stop Assistant"
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
