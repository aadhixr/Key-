package com.example.mykeyboard

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            try {
                val pm = context.packageManager
                val componentName = ComponentName(context, MainActivity::class.java)
                
                val currentState = pm.getComponentEnabledSetting(componentName)
                val isHidden = (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                        currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)

                if (isHidden) {
                    // Make visible and open
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "App is now VISIBLE", Toast.LENGTH_LONG).show()
                    }

                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(launchIntent)
                } else {
                    // Make invisible (disable)
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "App is now INVISIBLE", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
