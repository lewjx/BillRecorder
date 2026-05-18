package com.billrecorder

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class PopupOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }

        val txnId    = intent?.getStringExtra("txnId")    ?: return START_NOT_STICKY
        val title    = intent.getStringExtra("title")      ?: ""
        val amount   = intent.getStringExtra("amount")     ?: "S$0.00"
        val isIncome = intent.getBooleanExtra("isIncome", false)

        dismiss()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.popup_overlay, null)

        overlayView?.apply {
            // Amount (read-only)
            val tvAmount = findViewById<TextView>(R.id.tvPopupAmount)
            tvAmount.text = amount
            tvAmount.setTextColor(Color.parseColor(if (isIncome) "#77C388" else "#E26C59"))

            // Income / Expense badge
            val tvBadge = findViewById<TextView>(R.id.tvTypeBadge)
            tvBadge.text = if (isIncome) "INCOME" else "EXPENSE"
            tvBadge.setTextColor(Color.parseColor(if (isIncome) "#77C388" else "#E26C59"))

            // Pre-fill the label with the auto-detected title
            val etLabel = findViewById<EditText>(R.id.etPopupLabel)
            etLabel.setText(title)

            // Close (✕) — discards the transaction entirely
            findViewById<TextView>(R.id.tvClose).setOnClickListener {
                DataManager.deleteTransaction(txnId)
                sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
                dismiss()
            }

            // Discard button — same as close
            findViewById<Button>(R.id.btnPopupDiscard).setOnClickListener {
                DataManager.deleteTransaction(txnId)
                sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
                dismiss()
            }

            // Save button — update the transaction with edited label + note
            findViewById<Button>(R.id.btnPopupSave).setOnClickListener {
                val newLabel = etLabel.text.toString().trim().ifEmpty { title }
                val newNote  = findViewById<EditText>(R.id.etPopupNote).text.toString().trim()

                val txn = DataManager.getTransactions().find { it.id == txnId }
                txn?.let {
                    DataManager.updateTransaction(it.copy(title = newLabel, note = newNote))
                    sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
                }
                dismiss()
            }
        }

        // Allow touch + keyboard input (remove FLAG_NOT_FOCUSABLE for EditText)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        windowManager?.addView(overlayView, params)
        return START_NOT_STICKY
    }

    private fun dismiss() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null
    }

    override fun onDestroy() { super.onDestroy(); dismiss() }
}
