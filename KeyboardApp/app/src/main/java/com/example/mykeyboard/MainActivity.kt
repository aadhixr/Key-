package com.example.mykeyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {

    private lateinit var txtKeyboardStatus: TextView
    private lateinit var txtAccessibilityStatus: TextView
    private lateinit var txtNotificationStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContentView(R.layout.activity_main)

        txtKeyboardStatus = findViewById(R.id.txtKeyboardStatus)
        txtAccessibilityStatus = findViewById(R.id.txtAccessibilityStatus)
        txtNotificationStatus = findViewById(R.id.txtNotificationStatus)

        val btnKeyboard = findViewById<Button>(R.id.btnEnableKeyboard)
        val btnAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)
        val btnNotification = findViewById<Button>(R.id.btnEnableNotification)

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
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun updateStatuses() {
        // 1. Check Keyboard Status
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val myPackage = packageName
        var isKeyboardEnabled = false
        for (method in enabledMethods) {
            if (method.packageName == myPackage) {
                isKeyboardEnabled = true
                break
            }
        }

        if (isKeyboardEnabled) {
            txtKeyboardStatus.text = "Status: Enabled ✓"
            txtKeyboardStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
        } else {
            txtKeyboardStatus.text = "Status: Not Enabled ❌"
            txtKeyboardStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        }

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
            txtAccessibilityStatus.text = "Status: Enabled ✓"
            txtAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
        } else {
            txtAccessibilityStatus.text = "Status: Not Enabled ❌"
            txtAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        }

        // 3. Check Notification Access Status
        val notificationEnabled = try {
            val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            val componentName = ComponentName(this, AppNotificationListenerService::class.java)
            enabledListeners?.contains(componentName.flattenToString()) == true || enabledListeners?.contains(packageName) == true
        } catch (e: Exception) {
            false
        }

        if (notificationEnabled) {
            txtNotificationStatus.text = "Status: Enabled ✓"
            txtNotificationStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
        } else {
            txtNotificationStatus.text = "Status: Not Enabled ❌"
            txtNotificationStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        }
    }
}
