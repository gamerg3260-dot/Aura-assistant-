package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AuraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AuraAccessibilityService? = null
            private set

        fun getActiveScreenText(): String {
            val service = instance ?: return "Accessibility Service is not enabled. Please enable Aura AI in Android Accessibility Settings."
            val rootNode = service.rootInActiveWindow ?: return "No active window content could be captured."
            val sb = StringBuilder()
            extractNodeText(rootNode, sb, 0)
            return if (sb.isBlank()) "Active screen has no readable text elements." else sb.toString().take(1500)
        }

        private fun extractNodeText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
            if (node == null || depth > 8) return
            val text = node.text?.toString()?.trim()
            val contentDesc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank()) {
                sb.append(text).append(" • ")
            } else if (!contentDesc.isNullOrBlank()) {
                sb.append(contentDesc).append(" • ")
            }

            for (i in 0 until node.childCount) {
                extractNodeText(node.getChild(i), sb, depth + 1)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event processing
    }

    override fun onInterrupt() {
        // Interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
