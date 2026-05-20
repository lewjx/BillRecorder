package com.billrecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private val CREATE_FILE = 1
    private val PICK_CSV_FILE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataManager.init(this)
        setContentView(R.layout.activity_main)

        // Request overlay permission once if not granted (needed for popup)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            )
        }

        drawerLayout = findViewById(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_export_csv -> exportCsv()
                R.id.action_import_csv -> importCsv()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (savedInstanceState == null) {
            loadFragment(RecordsFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_records -> RecordsFragment()
                R.id.nav_analysis -> AnalysisFragment()
                R.id.nav_budgets -> BudgetsFragment()
                R.id.nav_accounts -> AccountsFragment()
                R.id.nav_categories -> CategoriesFragment()
                else -> RecordsFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "transactions_export.csv")
        }
        startActivityForResult(intent, CREATE_FILE)
    }

    private fun importCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, PICK_CSV_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                if (requestCode == CREATE_FILE) {
                    performExport(uri)
                } else if (requestCode == PICK_CSV_FILE) {
                    performImport(uri)
                }
            }
        }
    }

    private fun performExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("\"TIME\",\"TYPE\",\"AMOUNT\",\"CATEGORY\",\"ACCOUNT\",\"NOTES\"\n")
                    
                    val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val sdfOut = java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.US)
                    val df = java.text.DecimalFormat("0.00")
                    
                    val txns = DataManager.getTransactions()
                    for (t in txns) {
                        val dateFormatted = try {
                            sdfOut.format(sdfIn.parse(t.date)!!)
                        } catch (e: Exception) {
                            t.date
                        }
                        
                        val typeStr = if (t.isIncome) "(+) Income" else "(-) Expense"
                        
                        val cat = DataManager.getCategories().find { it.id == t.categoryId }
                        val catName = cat?.name ?: t.categoryId
                        
                        val acc = DataManager.getAccounts().find { it.id == t.accountId }
                        val accName = acc?.name ?: t.accountId
                        
                        val row = listOf(
                            "\"$dateFormatted\"",
                            "\"$typeStr\"",
                            "\"${df.format(t.amount)}\"",
                            "\"$catName\"",
                            "\"$accName\"",
                            "\"${t.note}\""
                        ).joinToString(",")
                        writer.write(row + "\n")
                    }
                }
            }
            Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(text: String): String {
        var escaped = text.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }

    private fun performImport(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var isFirstLine = true
                    var importedCount = 0
                    
                    val sdfIn = java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.US)
                    val sdfOut = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    
                    reader.forEachLine { line ->
                        if (isFirstLine) {
                            isFirstLine = false
                            return@forEachLine
                        }
                        if (line.isBlank()) return@forEachLine
                        
                        val parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                            .map { it.removeSurrounding("\"").replace("\"\"", "\"") }
                        
                        if (parts.size >= 5) {
                            val dateStr = parts[0]
                            val typeStr = parts[1]
                            val amountStr = parts[2]
                            val catStr = parts[3]
                            val accStr = parts[4]
                            val notesStr = parts.getOrNull(5) ?: ""
                            
                            val dateFormatted = try {
                                sdfOut.format(sdfIn.parse(dateStr)!!)
                            } catch (e: Exception) {
                                dateStr
                            }
                            
                            val isIncome = typeStr.contains("(+)")
                            val amount = amountStr.toDoubleOrNull() ?: 0.0
                            
                            val existingAcc = DataManager.getAccounts().find { it.name.equals(accStr, true) }
                            val accId = if (existingAcc != null) existingAcc.id else {
                                val newAcc = Account(id = UUID.randomUUID().toString(), name = accStr, bankName = accStr, balance = 0.0)
                                DataManager.addAccount(newAcc)
                                newAcc.id
                            }
                            
                            val existingCat = DataManager.getCategories().find { it.name.equals(catStr, true) }
                            val catId = if (existingCat != null) existingCat.id else {
                                val typeString = if (isIncome) "income" else "expense"
                                val newCat = Category(id = UUID.randomUUID().toString(), name = catStr, type = typeString, colorHex = "#888888", iconName = "ic_category_others")
                                DataManager.addCategory(newCat)
                                newCat.id
                            }
                            
                            val txn = Transaction(
                                id = UUID.randomUUID().toString(),
                                title = if (notesStr.isNotBlank()) notesStr else catStr,
                                date = dateFormatted,
                                amount = amount,
                                isIncome = isIncome,
                                categoryId = catId,
                                accountId = accId,
                                note = notesStr,
                                rawText = "",
                                isConfirmed = true
                            )
                            
                            DataManager.addTransaction(txn)
                            importedCount++
                        }
                    }
                    Toast.makeText(this, "Imported $importedCount transactions", Toast.LENGTH_SHORT).show()
                    sendBroadcast(Intent("com.billrecorder.NEW_BILL"))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
