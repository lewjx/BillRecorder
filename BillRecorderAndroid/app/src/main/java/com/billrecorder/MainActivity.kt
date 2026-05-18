package com.billrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnGrantPermission: Button
    private lateinit var tvLogs: TextView

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log_data") ?: return
            tvLogs.text = "$log\n\n${tvLogs.text}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        tvLogs = findViewById(R.id.tvLogs)

        btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        loadExistingLogs()
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
        val filter = IntentFilter("com.billrecorder.NEW_BILL")
        registerReceiver(logReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }

    private fun checkPermission() {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        if (enabledListeners.contains(packageName)) {
            tvStatus.text = "Status: Active (Listening to Notifications)"
            tvStatus.setTextColor(android.graphics.Color.GREEN)
            btnGrantPermission.visibility = View.GONE
        } else {
            tvStatus.text = "Status: Permission Required"
            tvStatus.setTextColor(android.graphics.Color.RED)
            btnGrantPermission.visibility = View.VISIBLE
        }
    }

    private fun loadExistingLogs() {
        val prefs = getSharedPreferences("BillLogs", Context.MODE_PRIVATE)
        val history = prefs.getString("history", "Waiting for bank notifications...")
        tvLogs.text = history
    }
}
