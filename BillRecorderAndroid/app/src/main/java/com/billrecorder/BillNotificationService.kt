package com.billrecorder

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class BillNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        
        // Bank apps often hide text in BigText or Ticker, so we extract everything possible
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val ticker = sbn.notification.tickerText?.toString() ?: ""
        
        val fullContent = "$title $text $bigText $ticker"

        // Expanded currency regex (RM, $, SGD, S$)
        val moneyRegex = Pattern.compile("(RM|SGD|S\\$|\\$)\\s?\\d+(\\.\\d{1,2})?", Pattern.CASE_INSENSITIVE)
        // Keywords for testing via Gmail/SMS
        val keywordRegex = Pattern.compile("(paid|transfer|transaction|receipt)", Pattern.CASE_INSENSITIVE)
        
        // Known banking and email apps
        val isTargetApp = packageName.contains("bank", ignoreCase = true) || 
                          packageName.contains("ocbc", ignoreCase = true) ||
                          packageName.contains("com.google.android.gm") // Gmail
        
        if (moneyRegex.matcher(fullContent).find() || keywordRegex.matcher(fullContent).find() || isTargetApp) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] App: $packageName\nTitle: $title\nBody: $text\nBigText: $bigText"
            
            Log.d("BillRecorder", logEntry)
            saveLog(logEntry)
            
            // Broadcast to MainActivity if it's open
            val intent = Intent("com.billrecorder.NEW_BILL")
            intent.putExtra("log_data", logEntry)
            sendBroadcast(intent)
        }
    }

    private fun saveLog(logEntry: String) {
        val prefs = getSharedPreferences("BillLogs", Context.MODE_PRIVATE)
        var history = prefs.getString("history", "") ?: ""
        history = "$logEntry\n\n-----------------\n\n$history"
        
        // Keep it to a reasonable size
        if (history.length > 10000) {
            history = history.substring(0, 10000)
        }
        
        prefs.edit().putString("history", history).apply()
    }
}
