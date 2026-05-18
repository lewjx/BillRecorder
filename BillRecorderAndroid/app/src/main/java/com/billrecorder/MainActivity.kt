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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvBalance: TextView
    private lateinit var btnGrantPermission: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadTransactions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvBalance)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(emptyList())
        recyclerView.adapter = adapter

        btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        loadTransactions()
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
        val filter = IntentFilter("com.billrecorder.NEW_BILL")
        registerReceiver(logReceiver, filter)
        loadTransactions()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }

    private fun checkPermission() {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        if (enabledListeners.contains(packageName)) {
            tvStatus.text = "Status: Active (Listening to Notifications)"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#A0AEC0"))
            btnGrantPermission.visibility = View.GONE
        } else {
            tvStatus.text = "Status: Permission Required"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#E53E3E"))
            btnGrantPermission.visibility = View.VISIBLE
        }
    }

    private fun loadTransactions() {
        val prefs = getSharedPreferences("BillLogs", Context.MODE_PRIVATE)
        val historyStr = prefs.getString("transactions", "[]") ?: "[]"
        
        val transactions = mutableListOf<Transaction>()
        var balance = 0.0

        try {
            val jsonArray = JSONArray(historyStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val txn = Transaction(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    date = obj.getString("date"),
                    amount = obj.getDouble("amount"),
                    isIncome = obj.getBoolean("isIncome"),
                    rawText = obj.getString("rawText")
                )
                transactions.add(txn)
                if (txn.isIncome) balance += txn.amount else balance -= txn.amount
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Display newest first
        transactions.sortByDescending { it.date }
        adapter.updateData(transactions)

        // Update Balance UI
        tvBalance.text = String.format(Locale.getDefault(), "$%.2f", balance)
    }
}
