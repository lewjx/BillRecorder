package com.billrecorder

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class BillNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        DataManager.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        DataManager.init(applicationContext)

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val ticker = sbn.notification.tickerText?.toString() ?: ""
        val fullContent = "$title $text $bigText $ticker"

        val moneyRegex = Pattern.compile("(?:RM|SGD|S\\$|\\$)\\s?(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = moneyRegex.matcher(fullContent)

        val isTargetApp = packageName.contains("bank", ignoreCase = true) ||
                packageName.contains("ocbc", ignoreCase = true) ||
                packageName.contains("dbs", ignoreCase = true) ||
                packageName.contains("uob", ignoreCase = true) ||
                packageName.contains("com.google.android.gm")

        if (!matcher.find() && !isTargetApp) return

        val amount = if (matcher.reset().find()) {
            matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else 0.0

        if (amount == 0.0 && !isTargetApp) return

        val isIncome = fullContent.contains("received", ignoreCase = true) ||
                fullContent.contains("credited", ignoreCase = true) ||
                fullContent.contains("inward", ignoreCase = true)

        // Auto-detect category from content keywords
        val categoryId = when {
            fullContent.contains("food", ignoreCase = true) ||
            fullContent.contains("restaurant", ignoreCase = true) ||
            fullContent.contains("grab", ignoreCase = true) -> "food_exp"
            fullContent.contains("transport", ignoreCase = true) ||
            fullContent.contains("mrt", ignoreCase = true) ||
            fullContent.contains("bus", ignoreCase = true) -> "transport_exp"
            fullContent.contains("grocery", ignoreCase = true) ||
            fullContent.contains("ntuc", ignoreCase = true) ||
            fullContent.contains("cold storage", ignoreCase = true) -> "grocery_exp"
            fullContent.contains("shopping", ignoreCase = true) ||
            fullContent.contains("lazada", ignoreCase = true) ||
            fullContent.contains("shopee", ignoreCase = true) -> "shopping_exp"
            fullContent.contains("salary", ignoreCase = true) -> "salary_inc"
            fullContent.contains("transfer", ignoreCase = true) -> if (isIncome) "transfer_inc" else "others_exp"
            else -> if (isIncome) "salary_inc" else "others_exp"
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val txnTitle = if (title.isBlank()) packageName else title

        val txn = Transaction(
            id = UUID.randomUUID().toString(),
            title = txnTitle,
            date = timestamp,
            amount = amount,
            isIncome = isIncome,
            categoryId = categoryId,
            accountId = inferAccountId(packageName),
            note = "",
            rawText = fullContent.take(300)
        )

        Log.d("BillRecorder", txn.toString())
        DataManager.addTransaction(txn)
        DataManager.updateAccountBalance(txn.accountId, if (isIncome) amount else -amount)
        sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
    }

    private fun inferAccountId(packageName: String): String {
        val accounts = DataManager.getAccounts()
        return accounts.find { packageName.contains(it.name, ignoreCase = true) }?.id ?: ""
    }
}
