import os

csv_path = r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\export_20_05_26_139.csv'
kotlin_path = r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\java\com\billrecorder\CsvHistoryImporter.kt'

with open(csv_path, 'r', encoding='utf-8') as f:
    csv_data = f.read()

kotlin_code = f"""package com.billrecorder

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

object CsvHistoryImporter {{
    val csvData = \"\"\"
{csv_data}
\"\"\"

    fun importIfNeeded() {{
        if (DataManager.prefs.getBoolean("history_imported_v2", false)) return
        
        // Clear all existing data
        DataManager.prefs.edit()
            .remove("transactions")
            .remove("accounts")
            .remove("categories")
            .remove("budgets")
            .remove("seeded")
            .apply()
            
        // Re-seed default categories
        val initMethod = DataManager::class.java.getDeclaredMethod("seedDefaultsIfNeeded")
        initMethod.isAccessible = true
        initMethod.invoke(DataManager)
            
        val lines = csvData.lines().drop(1)
        val sdfIn = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US)
        val sdfOut = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        val existingAccounts = mutableMapOf<String, Account>()
        
        lines.forEach {{ line ->
            if (line.isBlank()) return@forEach
            val cols = line.split("\\",\\"").map {{ it.replace("\\"", "") }}
            if (cols.size >= 5) {{
                val dateStr = cols[0]
                val typeStr = cols[1]
                val amountStr = cols[2]
                val catStr = cols[3]
                val accStr = cols[4]
                val notesStr = cols.getOrNull(5) ?: ""
                
                val dateFormatted = try {{
                    sdfOut.format(sdfIn.parse(dateStr)!!)
                }} catch (e: Exception) {{
                    dateStr
                }}
                val isIncome = typeStr.contains("(+)")
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                
                // Find or create account
                val accKey = accStr.lowercase()
                val accountId = if (existingAccounts.containsKey(accKey)) {{
                    existingAccounts[accKey]!!.id
                }} else {{
                    val newAcc = Account(UUID.randomUUID().toString(), accStr, accStr, 0.0)
                    DataManager.addAccount(newAcc)
                    existingAccounts[accKey] = newAcc
                    newAcc.id
                }}
                
                // Determine category
                val catKey = catStr.lowercase()
                val catSuffix = if (isIncome) "inc" else "exp"
                var categoryId = "${{catKey.replace(" ", "_")}}_$catSuffix"
                
                // Make sure category exists, else fallback or create
                var cat = DataManager.getCategoryById(categoryId)
                if (cat == null) {{
                    cat = Category(
                        id = categoryId,
                        name = catStr,
                        type = if (isIncome) "income" else "expense",
                        colorHex = if (isIncome) "#38A169" else "#805AD5",
                        iconName = "others"
                    )
                    DataManager.addCategory(cat)
                }}

                val txn = Transaction(
                    id = UUID.randomUUID().toString(),
                    title = cat.name,
                    date = dateFormatted,
                    amount = amount,
                    isIncome = isIncome,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = notesStr,
                    rawText = "",
                    isConfirmed = true
                )
                DataManager.addTransaction(txn)
                DataManager.updateAccountBalance(accountId, if (isIncome) amount else -amount)
            }}
        }}
        
        DataManager.prefs.edit().putBoolean("history_imported_v2", true).apply()
    }}
}}
"""

with open(kotlin_path, 'w', encoding='utf-8') as f:
    f.write(kotlin_code)
