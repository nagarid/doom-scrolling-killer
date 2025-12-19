package com.example.doomscrollingkiller

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusValue = findViewById(R.id.status_value)

        findViewById<Button>(R.id.grant_access_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.start_button).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else if (hasUsageAccess(this)) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, UsageMonitorService::class.java)
                )
                updateStatus()
            } else {
                statusValue.setText(R.string.status_missing_access)
            }
        }

        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopService(Intent(this, UsageMonitorService::class.java))
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val statusRes = when {
            !hasUsageAccess(this) -> R.string.status_missing_access
            UsageMonitorService.isRunning -> R.string.status_monitoring
            else -> R.string.status_ready
        }
        statusValue.setText(statusRes)
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
