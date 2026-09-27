package com.example.mykeyboard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private val logs = mutableListOf<String>()
    private var listener: ((String) -> Unit)? = null

    fun setListener(listener: (String) -> Unit) {
        this.listener = listener
        listener(getAllLogs())
    }

    fun addLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timeStr] $message"
        logs.add(0, entry) // newest on top
        if (logs.size > 50) logs.removeAt(logs.size - 1)
        listener?.invoke(getAllLogs())
    }

    fun getAllLogs(): String {
        return if (logs.isEmpty()) "No sync activity yet." else logs.joinToString("\n")
    }
}
