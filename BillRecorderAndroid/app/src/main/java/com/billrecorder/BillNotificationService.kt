package com.billrecorder

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class BillNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val ticker = sbn.notification.tickerText?.toString() ?: ""
        
        val fullContent = "$title $text $bigText $ticker"

        // Expanded currency regex (RM, $, SGD, S$) capturing the numeric amount
        val moneyRegex = Pattern.compile("(?:RM|SGD|S\\$|\\$)\\s?(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = moneyRegex.matcher(fullContent)
        
        // Keywords for testing via Gmail/SMS
        val keywordRegex = Pattern.compile("(paid|transfer|transaction|receipt)", Pattern.CASE_INSENSITIVE)
        
        val isTargetApp = packageName.contains("bank", ignoreCase = true) || 
                          packageName.contains("ocbc", ignoreCase = true) ||
                          packageName.contains("com.google.android.gm")
        
        if (matcher.find() || keywordRegex.matcher(fullContent).find() || isTargetApp) {
            
            var amount = 0.0
            // If the regex matched an amount, parse it
            if (matcher.reset().find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: "0"
                amount = amountStr.toDoubleOrNull() ?: 0.0
            } else if (isTargetApp) {
                // For testing target apps without explicit amounts, we'll mock an amount if it's 0
                // Wait, it's better to only log transactions that actually have a parseable amount
                // But for debug purposes, we will default to 0.0
            }

            // Simple Income vs Expense Logic
            // If the notification says "received", "credited", it's income. Otherwise default to expense.
            val isIncome = fullContent.contains("received", ignoreCase = true) ||
                           fullContent.contains("credited", ignoreCase = true) ||
                           fullContent.contains("inward", ignoreCase = true)

            // If it's 0 amount and not from a target testing app, skip it to avoid noise
            if (amount == 0.0 && !isTargetApp) return

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val appTitle = if (title.isBlank()) packageName else title

            val transactionObj = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("title", appTitle)
                put("date", timestamp)
                put("amount", amount)
                put("isIncome", isIncome)
                put("rawText", fullContent)
            }
            
            Log.d("BillRecorder", transactionObj.toString())
            saveTransaction(transactionObj)
            
            // Broadcast to MainActivity to refresh UI instantly
            sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
        }
    }

    private fun saveTransaction(transaction: JSONObject) {
        val prefs = getSharedPreferences("BillLogs", Context.MODE_PRIVATE)
        val historyStr = prefs.getString("transactions", "[]") ?: "[]"
        
        try {
            val jsonArray = JSONArray(historyStr)
            jsonArray.put(transaction)
            
            // Keep history to last 500 to prevent massive SharedPreferences bloat
            var finalArray = jsonArray
            if (jsonArray.length() > 500) {
                finalArray = JSONArray()
                for (i in jsonArray.length() - 500 until jsonArray.length()) {
                    finalArray.put(jsonArray.getJSONObject(i))
                }
            }
            prefs.edit().putString("transactions", finalArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
