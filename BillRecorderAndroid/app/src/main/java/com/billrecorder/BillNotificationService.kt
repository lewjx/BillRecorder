package com.billrecorder

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class BillNotificationService : NotificationListenerService() {

    // ── Only listen to these exact bank package names ─────────────────────────
    private val allowedPackages = setOf(
        "com.ocbc.mobile",
        "com.ocbc.onlinebanking",
        "sg.com.dbs.mobile.banking",
        "com.dbs.mbanking.sg",
        "sg.com.uob",
        "sg.com.posb.mobile"
    )

    private val moneyRegex = Pattern.compile(
        "(?:RM|SGD|S\\$|\\$)\\s?(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        DataManager.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in allowedPackages) return   // ← strict filter

        DataManager.init(applicationContext)

        val extras = sbn.notification.extras
        val title   = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val ticker  = sbn.notification.tickerText?.toString() ?: ""
        val fullContent = "$title $text $bigText $ticker"

        val matcher = moneyRegex.matcher(fullContent)
        val amount = if (matcher.find()) {
            matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else 0.0

        // Only record if we found an actual amount
        if (amount == 0.0) return

        // ── Determine Income vs Expense with explicit keyword matching ────────
        // Income keywords (bank notification language)
        val incomeKeywords = listOf(
            "credited", "received", "inward", "deposit", "salary",
            "refund", "cashback", "rebate", "incoming", "transfer in",
            "payment received", "funds received"
        )
        // Expense keywords (bank notification language)
        val expenseKeywords = listOf(
            "charged", "debited", "payment of", "purchase", "spent",
            "withdrawn", "deducted", "transfer out", "paid to",
            "transaction of", "bill payment"
        )

        val lowerContent = fullContent.lowercase()
        val isIncome = when {
            incomeKeywords.any { lowerContent.contains(it) } -> true
            expenseKeywords.any { lowerContent.contains(it) } -> false
            else -> false  // default to expense when ambiguous
        }

        val categoryId = when {
            fullContent.contains("food", ignoreCase = true) ||
            fullContent.contains("restaurant", ignoreCase = true) ||
            fullContent.contains("grab", ignoreCase = true)          -> "food_exp"
            fullContent.contains("transport", ignoreCase = true) ||
            fullContent.contains("mrt", ignoreCase = true) ||
            fullContent.contains("bus", ignoreCase = true)           -> "transport_exp"
            fullContent.contains("grocery", ignoreCase = true) ||
            fullContent.contains("ntuc", ignoreCase = true) ||
            fullContent.contains("cold storage", ignoreCase = true)  -> "grocery_exp"
            fullContent.contains("shopping", ignoreCase = true) ||
            fullContent.contains("lazada", ignoreCase = true) ||
            fullContent.contains("shopee", ignoreCase = true)        -> "shopping_exp"
            fullContent.contains("salary", ignoreCase = true)        -> "salary_inc"
            fullContent.contains("transfer", ignoreCase = true)      -> if (isIncome) "transfer_inc" else "others_exp"
            else -> if (isIncome) "salary_inc" else "others_exp"
        }

        val timestamp  = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val txnTitle   = title.ifBlank { packageName }
        val sign       = if (isIncome) "+" else "-"
        val amountText = "${sign}S${"%.2f".format(amount)}"

        val txn = Transaction(
            id         = UUID.randomUUID().toString(),
            title      = txnTitle,
            date       = timestamp,
            amount     = amount,
            isIncome   = isIncome,
            categoryId = categoryId,
            accountId  = inferAccountId(packageName),
            note       = "",
            rawText    = fullContent.take(300),
            isConfirmed = false
        )

        Log.d("BillRecorder", txn.toString())
        DataManager.addTransaction(txn)

        // ── Auto-cancel the status bar notification if amount < S$50 ──────────
        if (amount < 50.0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                cancelNotification(sbn.key)
            }, 10000)
        }

        // ── Trigger editable floating popup for all captured transactions ─────
        val popupIntent = Intent(applicationContext, PopupOverlayService::class.java).apply {
            putExtra("txnId",    txn.id)
            putExtra("title",    txn.title)
            putExtra("amount",   amountText)
            putExtra("isIncome", isIncome)
        }
        startService(popupIntent)

        sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
    }

    private fun inferAccountId(packageName: String): String =
        DataManager.getAccounts().find { packageName.contains(it.name, ignoreCase = true) }?.id ?: ""
}
