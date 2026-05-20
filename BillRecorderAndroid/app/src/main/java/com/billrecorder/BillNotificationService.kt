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

    // ── Whitelisted bank, wallet, and transit package names ────────────────────
    private val allowedPackages = setOf(
        "com.ocbc.mobile",
        "com.ocbc.onlinebanking",
        "sg.com.dbs.mobile.banking",
        "com.dbs.mbanking.sg",
        "sg.com.uob",
        "sg.com.posb.mobile",

        // Wallets & Payments
        "com.grabtaxi.passenger",              // Grab / GrabPay
        "com.shopee.sg",                      // Shopee / ShopeePay
        "com.singtel.dash",                   // Singtel Dash
        "com.google.android.apps.walletnfcrel", // Google Pay / Wallet

        // Transit
        "com.transitlink.simplygo",           // SimplyGo
        "sg.com.transitlink.simplygo",        // SimplyGo (Legacy/alternative package)
        
        // Emails
        "com.google.android.gm"               // Gmail
    )

    private val moneyRegex = Pattern.compile(
        "(?:RM|SGD|S\\$|\\$)\\s?(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val testReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.billrecorder.TRIGGER_TEST") {
                val pkg = intent.getStringExtra("package") ?: "com.transitlink.simplygo"
                val title = intent.getStringExtra("title") ?: "SimplyGo"
                val text = intent.getStringExtra("text") ?: "Spent S$2.62 on MRT"
                processMockNotification(pkg, title, text)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            registerReceiver(testReceiver, android.content.IntentFilter("com.billrecorder.TRIGGER_TEST"))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(testReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    fun processMockNotification(packageName: String, title: String, text: String) {
        val fullContent = "$title $text"
        val matcher = moneyRegex.matcher(fullContent)
        val amount = if (matcher.find()) {
            matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else 0.0

        if (amount == 0.0) return

        val lowerContent = fullContent.lowercase()
        val isIncome = false

        // Correctly resolve transport category for SimplyGo/Transit
        val categoryId = when {
            packageName.contains("simplygo", ignoreCase = true) ||
            packageName.contains("transitlink", ignoreCase = true) ||
            fullContent.contains("transport", ignoreCase = true) ||
            fullContent.contains("mrt", ignoreCase = true) ||
            fullContent.contains("bus", ignoreCase = true)           -> "transport_exp"
            else -> "others_exp"
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val txnTitle = title.ifBlank { packageName }
        val amountText = "-S$%.2f".format(amount)

        val txn = Transaction(
            id         = UUID.randomUUID().toString(),
            title      = txnTitle,
            date       = timestamp,
            amount     = amount,
            isIncome   = isIncome,
            categoryId = categoryId,
            accountId  = inferAccountId(packageName),
            note       = "SIMULATED ALERT",
            rawText    = fullContent.take(300),
            isConfirmed = false
        )

        DataManager.init(applicationContext)
        DataManager.addTransaction(txn)

        val popupIntent = Intent(applicationContext, PopupOverlayService::class.java).apply {
            putExtra("txnId",    txn.id)
            putExtra("title",    txn.title)
            putExtra("amount",   amountText)
            putExtra("isIncome", isIncome)
        }
        startService(popupIntent)

        sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        DataManager.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in allowedPackages) return   // ← whitelist filter

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

        // ── Duplicate Detection (15 minute window) ─────────────────────────
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = Date()
        val isDuplicate = DataManager.getTransactions().take(20).any { existing ->
            if (existing.amount == amount && existing.isIncome == isIncome) {
                try {
                    val existingDate = sdf.parse(existing.date)
                    if (existingDate != null) {
                        val diffMinutes = Math.abs(now.time - existingDate.time) / (60 * 1000)
                        return@any diffMinutes <= 15
                    }
                } catch (e: Exception) {}
            }
            false
        }

        if (isDuplicate) {
            Log.d("BillRecorder", "Duplicate detected for amount $amount. Ignoring.")
            if (packageName == "com.google.android.gm") {
                // Delete the redundant email notification after 10 seconds as requested
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try { cancelNotification(sbn.key) } catch (e: Exception) {}
                }, 10000)
            }
            return // Skip recording duplicate
        }

        val categoryId = when {
            packageName.contains("simplygo", ignoreCase = true) ||
            packageName.contains("transitlink", ignoreCase = true) ||
            fullContent.contains("transport", ignoreCase = true) ||
            fullContent.contains("mrt", ignoreCase = true) ||
            fullContent.contains("bus", ignoreCase = true)           -> "transport_exp"
            fullContent.contains("food", ignoreCase = true) ||
            fullContent.contains("restaurant", ignoreCase = true) ||
            fullContent.contains("grab", ignoreCase = true)          -> "food_exp"
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
        val amountText = "${sign}S\$${"%.2f".format(amount)}"

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
