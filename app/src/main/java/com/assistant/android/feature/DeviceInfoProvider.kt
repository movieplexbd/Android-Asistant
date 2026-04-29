package com.assistant.android.feature

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * NEW FEATURE — Pure on-device snapshot of battery, RAM, storage, network, device.
 * Used by the "battery koto?" / "ram?" / "device info" voice intents — no API call needed.
 */
object DeviceInfoProvider {

    fun summary(context: Context): String {
        return buildString {
            append("Battery: ${batteryPct(context)}%")
            val charging = isCharging(context)
            if (charging) append(" (charging)")
            append("  •  RAM: ${ramUsedPercent(context)}% used")
            append("  •  Storage free: ${storageFreeGb()} GB")
            append("  •  Network: ${networkType(context)}")
            append("  •  ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        }
    }

    fun spokenSummary(context: Context): String {
        val pct = batteryPct(context)
        val charging = if (isCharging(context)) " and charging" else ""
        val ram = ramUsedPercent(context)
        val gb = storageFreeGb()
        val net = networkType(context)
        return "Battery is $pct percent$charging. RAM $ram percent used. $gb gigabytes free. On $net."
    }

    fun batteryPct(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        }
    }

    fun isCharging(context: Context): Boolean {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun ramUsedPercent(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            if (mi.totalMem == 0L) 0 else (((mi.totalMem - mi.availMem) * 100) / mi.totalMem).toInt()
        } catch (_: Exception) { 0 }
    }

    fun storageFreeGb(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val free = stat.availableBlocksLong * stat.blockSizeLong
            "%.1f".format(free / 1_073_741_824.0)
        } catch (_: Exception) { "?" }
    }

    fun networkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(net) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }
        } catch (_: Exception) { "Unknown" }
    }
}
