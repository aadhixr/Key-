package com.example.mykeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContentView(R.layout.activity_main)

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
}
