package com.example.mykeyboard

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
                    // Make visible
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Toast.makeText(context, "App is now VISIBLE in app drawer", Toast.LENGTH_LONG).show()

                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launchIntent)
                } else {
                    // Make invisible
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Toast.makeText(context, "App is now INVISIBLE", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
