package com.billrecorder

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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

        // Wrap Context with Theme so theme colors / dropdown style apply perfectly
        val themedContext = ContextThemeWrapper(this, R.style.Theme_BillRecorder)
        windowManager = themedContext.getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(themedContext).inflate(R.layout.popup_overlay, null)

        val txn = DataManager.getTransactions().find { it.id == txnId }

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

            // Populate Spinners using themedContext
            val categories = DataManager.getCategories().filter {
                it.type.equals(if (isIncome) "income" else "expense", true)
            }
            val catNames = categories.map { it.name }
            val spinnerCat = findViewById<Spinner>(R.id.spinnerPopupCategory)
            val catAdapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_item, catNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerCat.adapter = catAdapter

            val accounts = DataManager.getAccounts()
            val accNames = accounts.map { it.name }.ifEmpty { listOf("No accounts") }
            val spinnerAcc = findViewById<Spinner>(R.id.spinnerPopupAccount)
            val accAdapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_item, accNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerAcc.adapter = accAdapter

            // Select active/default values
            txn?.let { t ->
                val catIdx = categories.indexOfFirst { c -> c.id == t.categoryId }
                if (catIdx >= 0) spinnerCat.setSelection(catIdx)

                val accIdx = accounts.indexOfFirst { a -> a.id == t.accountId }
                if (accIdx >= 0) spinnerAcc.setSelection(accIdx)

                findViewById<EditText>(R.id.etPopupNote).setText(t.note)
            }

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

            // Save button — update the transaction with edited label, category, account, and note
            findViewById<Button>(R.id.btnPopupSave).setOnClickListener {
                val newLabel = etLabel.text.toString().trim().ifEmpty { title }
                val newNote  = findViewById<EditText>(R.id.etPopupNote).text.toString().trim()
                val selectedCat = categories.getOrNull(spinnerCat.selectedItemPosition)
                val selectedAcc = accounts.getOrNull(spinnerAcc.selectedItemPosition)

                txn?.let { oldTxn ->
                    val updatedAmount = oldTxn.amount
                    val updatedIsIncome = oldTxn.isIncome

                    // If account changed, reconcile the balances
                    if (oldTxn.accountId != selectedAcc?.id) {
                        if (oldTxn.accountId.isNotEmpty()) {
                            DataManager.updateAccountBalance(oldTxn.accountId, if (updatedIsIncome) -updatedAmount else updatedAmount)
                        }
                        selectedAcc?.let { acc ->
                            DataManager.updateAccountBalance(acc.id, if (updatedIsIncome) updatedAmount else -updatedAmount)
                        }
                    }

                    val updatedTxn = oldTxn.copy(
                        title = newLabel,
                        note = newNote,
                        categoryId = selectedCat?.id ?: oldTxn.categoryId,
                        accountId = selectedAcc?.id ?: oldTxn.accountId
                    )
                    DataManager.updateTransaction(updatedTxn)
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
            gravity = Gravity.CENTER
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
