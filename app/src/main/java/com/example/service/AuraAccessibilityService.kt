package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenAnalysisData(
    val isEnabled: Boolean,
    val packageName: String?,
    val appName: String?,
    val title: String?,
    val items: List<String>,
    val fullRawText: String,
    val aiSummary: String
)

class AuraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AuraAccessibilityService? = null
            private set

        val isServiceConnected: Boolean
            get() = instance != null

        fun getActiveScreenText(): String {
            val service = instance ?: return "Accessibility Service is not enabled. Please enable Aura AI in Android Accessibility Settings."
            val rootNode = service.rootInActiveWindow ?: return "No active window content could be captured."
            val sb = StringBuilder()
            extractNodeText(rootNode, sb, 0)
            return if (sb.isBlank()) "Active screen has no readable text elements." else sb.toString().take(2000)
        }

        fun analyzeCurrentScreen(context: Context): ScreenAnalysisData {
            val service = instance
            if (service == null) {
                return ScreenAnalysisData(
                    isEnabled = false,
                    packageName = null,
                    appName = null,
                    title = "Accessibility Not Enabled",
                    items = emptyList(),
                    fullRawText = "",
                    aiSummary = "Accessibility Service is not active. Enable Aura in Accessibility Settings to enable Siri-style screen reading and on-screen awareness."
                )
            }

            val root = service.rootInActiveWindow
            if (root == null) {
                return ScreenAnalysisData(
                    isEnabled = true,
                    packageName = null,
                    appName = null,
                    title = "Empty Window",
                    items = emptyList(),
                    fullRawText = "",
                    aiSummary = "The active window is currently blank or transitioning."
                )
            }

            val pkg = root.packageName?.toString() ?: ""
            val appLabel = resolveAppLabel(context, pkg)
            val extractedList = mutableListOf<String>()
            extractNodeList(root, extractedList, 0)

            val cleanItems = extractedList.distinct().filter { it.isNotBlank() && it.length > 1 }
            val rawSummary = cleanItems.joinToString(" • ")
            val title = cleanItems.firstOrNull() ?: appLabel

            val aiSummary = buildString {
                append("Active App: $appLabel. ")
                if (cleanItems.isNotEmpty()) {
                    append("Screen Contents: ")
                    append(cleanItems.take(6).joinToString(", "))
                    if (cleanItems.size > 6) {
                        append(" (and ${cleanItems.size - 6} more elements)")
                    }
                } else {
                    append("No textual elements detected.")
                }
            }

            return ScreenAnalysisData(
                isEnabled = true,
                packageName = pkg,
                appName = appLabel,
                title = title,
                items = cleanItems,
                fullRawText = rawSummary,
                aiSummary = aiSummary
            )
        }

        private fun resolveAppLabel(context: Context, packageName: String): String {
            if (packageName.isBlank()) return "Unknown App"
            return try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            }
        }

        fun performGlobalSystemAction(action: String): Boolean {
            val service = instance ?: return false
            val globalActionId = when (action.lowercase().trim()) {
                "back" -> GLOBAL_ACTION_BACK
                "home" -> GLOBAL_ACTION_HOME
                "recents", "recent_apps" -> GLOBAL_ACTION_RECENTS
                "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
                "lock_screen", "lock" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else return false
                "screenshot" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
                else -> return false
            }
            return service.performGlobalAction(globalActionId)
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

        private fun extractNodeList(node: AccessibilityNodeInfo?, list: MutableList<String>, depth: Int) {
            if (node == null || depth > 8) return
            val text = node.text?.toString()?.trim()
            val contentDesc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank()) {
                list.add(text)
            } else if (!contentDesc.isNullOrBlank()) {
                list.add(contentDesc)
            }

            for (i in 0 until node.childCount) {
                extractNodeList(node.getChild(i), list, depth + 1)
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
