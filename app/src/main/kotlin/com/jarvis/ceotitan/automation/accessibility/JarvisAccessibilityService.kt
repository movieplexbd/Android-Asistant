package com.jarvis.ceotitan.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: JarvisAccessibilityService? = null
        private val _currentApp = MutableStateFlow("")
        val currentApp: StateFlow<String> = _currentApp.asStateFlow()

        fun isEnabled(): Boolean = instance != null

        fun getInstance(): JarvisAccessibilityService? = instance

        fun performGoHome() = instance?.performGlobalAction(GLOBAL_ACTION_HOME)
        fun performGoBack() = instance?.performGlobalAction(GLOBAL_ACTION_BACK)
        fun performRecentApps() = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS)
        fun performQuickSettings() = instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        fun performNotifications() = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val packageName = it.packageName?.toString() ?: ""
                _currentApp.value = packageName
            }
        }
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun findAndClickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    fun findAndTypeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findEditText(root) ?: return false
        val arguments = android.os.Bundle()
        arguments.putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return true
    }

    private fun findEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.className?.contains("EditText") == true) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findEditText(child)
            if (found != null) return found
        }
        return null
    }

    fun scrollDown(): Boolean {
        return performScroll(false)
    }

    fun scrollUp(): Boolean {
        return performScroll(true)
    }

    private fun performScroll(up: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(root)
        return if (scrollableNode != null) {
            if (up) scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            else scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            val path = Path()
            val screenHeight = resources.displayMetrics.heightPixels
            val screenWidth = resources.displayMetrics.widthPixels
            if (up) {
                path.moveTo(screenWidth / 2f, screenHeight * 0.3f)
                path.lineTo(screenWidth / 2f, screenHeight * 0.7f)
            } else {
                path.moveTo(screenWidth / 2f, screenHeight * 0.7f)
                path.lineTo(screenWidth / 2f, screenHeight * 0.3f)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        extractText(root, builder)
        return builder.toString().trim()
    }

    private fun extractText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        if (!node.text.isNullOrEmpty()) {
            builder.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractText(child, builder)
        }
    }
}
