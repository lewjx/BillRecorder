package com.billrecorder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object DataManager {

    internal lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("BillLogs", Context.MODE_PRIVATE)
        seedDefaultsIfNeeded()
        CsvHistoryImporter.importIfNeeded()
    }

    // ─── TRANSACTIONS ─────────────────────────────────────────────────────────

    fun getTransactions(): List<Transaction> {
        val arr = JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Transaction(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title"),
                date = o.optString("date"),
                amount = o.optDouble("amount", 0.0),
                isIncome = o.optBoolean("isIncome", false),
                categoryId = o.optString("categoryId", "others_exp"),
                accountId = o.optString("accountId", ""),
                note = o.optString("note", ""),
                rawText = o.optString("rawText", ""),
                isConfirmed = o.optBoolean("isConfirmed", true)
            )
        }.sortedByDescending { it.date }
    }

    fun addTransaction(txn: Transaction) {
        val arr = JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        arr.put(txnToJson(txn))
        prefs.edit().putString("transactions", arr.toString()).apply()
    }

    fun updateTransaction(txn: Transaction) {
        val arr = JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == txn.id) updated.put(txnToJson(txn)) else updated.put(o)
        }
        prefs.edit().putString("transactions", updated.toString()).apply()
    }

    fun deleteTransaction(id: String) {
        val arr = JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) updated.put(o)
        }
        prefs.edit().putString("transactions", updated.toString()).apply()
    }

    private fun txnToJson(txn: Transaction) = JSONObject().apply {
        put("id", txn.id)
        put("title", txn.title)
        put("date", txn.date)
        put("amount", txn.amount)
        put("isIncome", txn.isIncome)
        put("categoryId", txn.categoryId)
        put("accountId", txn.accountId)
        put("note", txn.note)
        put("rawText", txn.rawText)
        put("isConfirmed", txn.isConfirmed)
    }

    // ─── ACCOUNTS ─────────────────────────────────────────────────────────────

    fun getAccounts(): List<Account> {
        val arr = JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Account(
                id = o.optString("id"),
                name = o.optString("name"),
                bankName = o.optString("bankName"),
                balance = o.optDouble("balance", 0.0)
            )
        }
    }

    fun addAccount(acc: Account) {
        val arr = JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        arr.put(accToJson(acc))
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    fun deleteAccount(id: String) {
        val arr = JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) updated.put(o)
        }
        prefs.edit().putString("accounts", updated.toString()).apply()
    }

    fun updateAccountBalance(accountId: String, delta: Double) {
        val arr = JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == accountId) {
                o.put("balance", o.optDouble("balance", 0.0) + delta)
            }
            updated.put(o)
        }
        prefs.edit().putString("accounts", updated.toString()).apply()
    }

    private fun accToJson(acc: Account) = JSONObject().apply {
        put("id", acc.id)
        put("name", acc.name)
        put("bankName", acc.bankName)
        put("balance", acc.balance)
    }

    // ─── CATEGORIES ───────────────────────────────────────────────────────────

    fun getCategories(): List<Category> {
        val arr = JSONArray(prefs.getString("categories", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Category(
                id = o.optString("id"),
                name = o.optString("name"),
                type = o.optString("type"),
                colorHex = o.optString("colorHex", "#888888"),
                iconName = o.optString("iconName", "others")
            )
        }
    }

    fun addCategory(cat: Category) {
        val arr = JSONArray(prefs.getString("categories", "[]") ?: "[]")
        arr.put(catToJson(cat))
        prefs.edit().putString("categories", arr.toString()).apply()
    }

    fun updateCategoryIcon(categoryId: String, newIconName: String) {
        val arr = JSONArray(prefs.getString("categories", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == categoryId) {
                o.put("iconName", newIconName)
            }
            updated.put(o)
        }
        prefs.edit().putString("categories", updated.toString()).apply()
    }

    fun deleteCategory(id: String) {
        val arr = JSONArray(prefs.getString("categories", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) updated.put(o)
        }
        prefs.edit().putString("categories", updated.toString()).apply()
    }

    private fun catToJson(cat: Category) = JSONObject().apply {
        put("id", cat.id)
        put("name", cat.name)
        put("type", cat.type)
        put("colorHex", cat.colorHex)
        put("iconName", cat.iconName)
    }

    // ─── BUDGETS ──────────────────────────────────────────────────────────────

    fun getBudgets(): List<Budget> {
        val arr = JSONArray(prefs.getString("budgets", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Budget(
                id = o.optString("id"),
                categoryId = o.optString("categoryId"),
                monthYear = o.optString("monthYear"),
                limit = o.optDouble("limit", 0.0)
            )
        }
    }

    fun setBudget(budget: Budget) {
        val arr = JSONArray(prefs.getString("budgets", "[]") ?: "[]")
        val updated = JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("categoryId") == budget.categoryId && o.optString("monthYear") == budget.monthYear) {
                updated.put(budToJson(budget))
                found = true
            } else updated.put(o)
        }
        if (!found) updated.put(budToJson(budget))
        prefs.edit().putString("budgets", updated.toString()).apply()
    }

    private fun budToJson(b: Budget) = JSONObject().apply {
        put("id", b.id)
        put("categoryId", b.categoryId)
        put("monthYear", b.monthYear)
        put("limit", b.limit)
    }

    // ─── SEEDING ──────────────────────────────────────────────────────────────

    private fun seedDefaultsIfNeeded() {
        if (prefs.getBoolean("seeded", false)) return

        val expenseCategories = listOf(
            Category("food_exp", "Food", "expense", "#E53E3E", "food"),
            Category("transport_exp", "Transport", "expense", "#DD6B20", "transport"),
            Category("grocery_exp", "Grocery", "expense", "#3182CE", "grocery"),
            Category("shopping_exp", "Shopping", "expense", "#805AD5", "shopping"),
            Category("entertainment_exp", "Entertainment", "expense", "#38A169", "entertainment"),
            Category("bills_exp", "Bills", "expense", "#718096", "bills"),
            Category("clothing_exp", "Clothing", "expense", "#D69E2E", "clothing"),
            Category("education_exp", "Education", "expense", "#2B6CB0", "education"),
            Category("healthcare_exp", "Healthcare", "expense", "#E53E3E", "health"),
            Category("others_exp", "Others", "expense", "#718096", "others"),
        )
        val incomeCategories = listOf(
            Category("salary_inc", "Salary", "income", "#38A169", "salary"),
            Category("awards_inc", "Awards", "income", "#3182CE", "awards"),
            Category("refunds_inc", "Refunds", "income", "#38A169", "refunds"),
            Category("rental_inc", "Rental", "income", "#805AD5", "rental"),
            Category("sale_inc", "Sale", "income", "#38A169", "sale"),
            Category("transfer_inc", "Transfer", "income", "#718096", "transfer"),
        )
        val all = JSONArray()
        (expenseCategories + incomeCategories).forEach { all.put(catToJson(it)) }
        prefs.edit()
            .putString("categories", all.toString())
            .putBoolean("seeded", true)
            .apply()
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    fun getCategoryById(id: String): Category? = getCategories().find { it.id == id }
    fun getAccountById(id: String): Account? = getAccounts().find { it.id == id }

    fun getTotalExpenseForMonth(monthYear: String): Double =
        getTransactions()
            .filter { !it.isIncome && it.date.startsWith(monthYear) }
            .sumOf { it.amount }

    fun getTotalIncomeForMonth(monthYear: String): Double =
        getTransactions()
            .filter { it.isIncome && it.date.startsWith(monthYear) }
            .sumOf { it.amount }

    // ─── EXPORT ───────────────────────────────────────────────────────────────

    fun exportCsvContent(): String {
        val builder = java.lang.StringBuilder()
        builder.append("ID,Title,Date,Amount,Type,Category,Account,Note,RawText,IsConfirmed\n")
        getTransactions().forEach {
            val type = if (it.isIncome) "Income" else "Expense"
            val category = getCategoryById(it.categoryId)?.name ?: ""
            val account = getAccountById(it.accountId)?.name ?: ""
            builder.append("${it.id.escapeCsv()},${it.title.escapeCsv()},${it.date.escapeCsv()},${it.amount},${type.escapeCsv()},${category.escapeCsv()},${account.escapeCsv()},${it.note.escapeCsv()},${it.rawText.escapeCsv()},${it.isConfirmed}\n")
        }
        return builder.toString()
    }

    private fun String.escapeCsv(): String {
        var escaped = this.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }
}
