package com.billrecorder

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.*

class RecordsFragment : Fragment() {

    private lateinit var tvMonth: TextView
    private lateinit var tvTotalExpense: TextView
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotal: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private var currentCalendar = Calendar.getInstance()
    private var isUnconfirmedExpanded = false

    private val billReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            refresh()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_records, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvMonth = view.findViewById(R.id.tvMonth)
        tvTotalExpense = view.findViewById(R.id.tvTotalExpense)
        tvTotalIncome = view.findViewById(R.id.tvTotalIncome)
        tvTotal = view.findViewById(R.id.tvTotal)
        recyclerView = view.findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TransactionAdapter(emptyList<ListItem>()) { txn ->
            showEditDeleteDialog(txn)
        }
        recyclerView.adapter = adapter

        view.findViewById<TextView>(R.id.btnPrevMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            refresh()
        }
        view.findViewById<TextView>(R.id.btnNextMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            refresh()
        }
        view.findViewById<View>(R.id.fabAdd).setOnClickListener { showAddTransactionDialog() }
        
        // 🚀 Auto-run Test Trigger: Long press FAB to trigger custom overlay popup
        view.findViewById<View>(R.id.fabAdd).setOnLongClickListener {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val testTxn = Transaction(
                id = UUID.randomUUID().toString(),
                title = "Mock Bank Notification",
                date = now,
                amount = 28.50,
                isIncome = false,
                categoryId = "food_exp",
                accountId = DataManager.getAccounts().firstOrNull()?.id ?: "",
                note = "Mock SMS Alert text...",
                rawText = "Transaction spent S$28.50 at Starbucks",
                isConfirmed = false
            )
            DataManager.addTransaction(testTxn)

            val popupIntent = Intent(requireContext(), PopupOverlayService::class.java).apply {
                putExtra("txnId", testTxn.id)
                putExtra("title", testTxn.title)
                putExtra("amount", "-S$28.50")
                putExtra("isIncome", false)
            }
            try {
                requireContext().startService(popupIntent)
                Toast.makeText(requireContext(), "🚀 Started Test Popup Overlay!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: Make sure overlay permission is granted!", Toast.LENGTH_LONG).show()
            }
            true
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        try {
            requireContext().registerReceiver(
                billReceiver,
                android.content.IntentFilter("com.billrecorder.NEW_BILL")
            )
        } catch (_: Exception) {}
        refresh()
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(billReceiver) } catch (_: Exception) {}
    }

    private fun currentMonthYear(): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)

    private fun refresh() {
        val sdfDisplay = SimpleDateFormat("MMM, yyyy", Locale.getDefault())
        tvMonth.text = sdfDisplay.format(currentCalendar.time)

        val monthYear = currentMonthYear()
        val allTxns = DataManager.getTransactions()
            .filter { it.isConfirmed && it.date.startsWith(monthYear) }
            .sortedByDescending { it.date }

        val expense = allTxns.filter { !it.isIncome }.sumOf { it.amount }
        val income = allTxns.filter { it.isIncome }.sumOf { it.amount }
        val total = income - expense

        tvTotalExpense.text = "S$%.2f".format(expense)
        tvTotalIncome.text = "S$%.2f".format(income)
        tvTotal.text = "S$%.2f".format(total)
        tvTotal.setTextColor(Color.parseColor(if (total >= 0) "#77C388" else "#E26C59"))

        adapter.updateData(allTxns)

        // ── Unconfirmed Dropdown Dynamic Binding ─────────────────────────────
        val view = view ?: return
        val llUnconfirmedHeader = view.findViewById<LinearLayout>(R.id.llUnconfirmedHeader)
        val tvUnconfirmedTitle = view.findViewById<TextView>(R.id.tvUnconfirmedTitle)
        val tvUnconfirmedArrow = view.findViewById<TextView>(R.id.tvUnconfirmedArrow)
        val llUnconfirmedContainer = view.findViewById<LinearLayout>(R.id.llUnconfirmedContainer)
        val llUnconfirmedList = view.findViewById<LinearLayout>(R.id.llUnconfirmedList)

        val unconfirmedTxns = DataManager.getTransactions().filter { !it.isConfirmed }
        if (unconfirmedTxns.isEmpty()) {
            llUnconfirmedHeader.visibility = View.GONE
            llUnconfirmedContainer.visibility = View.GONE
        } else {
            llUnconfirmedHeader.visibility = View.VISIBLE
            tvUnconfirmedTitle.text = "⚠️ ${unconfirmedTxns.size} Unconfirmed Transactions"
            tvUnconfirmedArrow.text = if (isUnconfirmedExpanded) "▲" else "▼"
            llUnconfirmedContainer.visibility = if (isUnconfirmedExpanded) View.VISIBLE else View.GONE

            llUnconfirmedHeader.setOnClickListener {
                isUnconfirmedExpanded = !isUnconfirmedExpanded
                tvUnconfirmedArrow.text = if (isUnconfirmedExpanded) "▲" else "▼"
                llUnconfirmedContainer.visibility = if (isUnconfirmedExpanded) View.VISIBLE else View.GONE
            }

            llUnconfirmedList.removeAllViews()
            unconfirmedTxns.forEach { txn ->
                val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_transaction, null)
                val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
                val tvBank = row.findViewById<TextView>(R.id.tvBank)
                val tvAmount = row.findViewById<TextView>(R.id.tvAmount)
                val cvIconBg = row.findViewById<CardView>(R.id.cvIconBg)
                val ivIcon = row.findViewById<android.widget.ImageView>(R.id.ivIcon)
                val tvEmojiFallback = row.findViewById<TextView>(R.id.tvEmojiFallback)
                val ivDefaultIcon = row.findViewById<android.widget.ImageView>(R.id.ivDefaultIcon)

                tvTitle.text = txn.title
                val acc = DataManager.getAccountById(txn.accountId)
                tvBank.text = acc?.name?.lowercase() ?: txn.accountId.ifEmpty { "bank" }

                val sign = if (txn.isIncome) "+" else "-"
                val amtColor = if (txn.isIncome) "#77C388" else "#E26C59"
                tvAmount.setTextColor(Color.parseColor(amtColor))
                tvAmount.text = "${sign}S${"%.2f".format(txn.amount)}"

                val cat = DataManager.getCategoryById(txn.categoryId)
                val colorHex = cat?.colorHex ?: (if (txn.isIncome) "#77C388" else "#E26C59")

                if (cat != null) {
                    val resId = CategoryIcons.getDrawableResId(requireContext(), cat.iconName, cat.name)
                    if (resId != 0) {
                        ivIcon.visibility = View.VISIBLE
                        ivIcon.setImageResource(resId)
                        tvEmojiFallback.visibility = View.GONE
                        ivDefaultIcon.visibility = View.GONE
                        cvIconBg.setCardBackgroundColor(Color.TRANSPARENT)
                    } else {
                        ivIcon.visibility = View.GONE
                        tvEmojiFallback.visibility = View.VISIBLE
                        tvEmojiFallback.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
                        ivDefaultIcon.visibility = View.GONE
                        cvIconBg.setCardBackgroundColor(Color.parseColor(colorHex))
                    }
                } else {
                    ivIcon.visibility = View.GONE
                    tvEmojiFallback.visibility = View.VISIBLE
                    tvEmojiFallback.text = "💸"
                    ivDefaultIcon.visibility = View.GONE
                    cvIconBg.setCardBackgroundColor(Color.parseColor(colorHex))
                }

                row.setOnClickListener { showEditDeleteDialog(txn) }
                llUnconfirmedList.addView(row)
            }
        }
    }

    private fun showAddTransactionDialog(existing: Transaction? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_transaction, null)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgType)
        val rbExpense = dialogView.findViewById<RadioButton>(R.id.rbExpense)
        val rbIncome = dialogView.findViewById<RadioButton>(R.id.rbIncome)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val spinnerCat = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerAcc = dialogView.findViewById<Spinner>(R.id.spinnerAccount)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)

        // Populate category spinner
        var categories = DataManager.getCategories().filter { !it.type.equals("income", true) }
        fun updateCategorySpinner(isIncome: Boolean) {
            categories = DataManager.getCategories().filter {
                it.type.equals(if (isIncome) "income" else "expense", true)
            }
            val catNames = categories.map { it.name }
            spinnerCat.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, catNames)
        }
        updateCategorySpinner(false)
        rgType.setOnCheckedChangeListener { _, checkedId ->
            updateCategorySpinner(checkedId == R.id.rbIncome)
        }

        // Populate account spinner
        val accounts = DataManager.getAccounts()
        val accNames = accounts.map { it.name }.ifEmpty { listOf("No accounts") }
        spinnerAcc.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, accNames)

        // Pre-fill for edit
        existing?.let {
            etAmount.setText(it.amount.toString())
            etNote.setText(it.note)
            if (it.isIncome) rbIncome.isChecked = true else rbExpense.isChecked = true
            updateCategorySpinner(it.isIncome)
            val catIdx = categories.indexOfFirst { c -> c.id == it.categoryId }
            if (catIdx >= 0) spinnerCat.setSelection(catIdx)
            val accIdx = accounts.indexOfFirst { a -> a.id == it.accountId }
            if (accIdx >= 0) spinnerAcc.setSelection(accIdx)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Add Transaction" else "Edit Transaction")
            .setView(dialogView)
            .setPositiveButton(if (existing == null) "Add" else "Save") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val isIncome = rgType.checkedRadioButtonId == R.id.rbIncome
                val selectedCat = categories.getOrNull(spinnerCat.selectedItemPosition)
                val selectedAcc = accounts.getOrNull(spinnerAcc.selectedItemPosition)
                val note = etNote.text.toString()
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                if (existing == null) {
                    val txn = Transaction(
                        id = UUID.randomUUID().toString(),
                        title = selectedCat?.name ?: "Transaction",
                        date = now,
                        amount = amount,
                        isIncome = isIncome,
                        categoryId = selectedCat?.id ?: "others_exp",
                        accountId = selectedAcc?.id ?: "",
                        note = note,
                        rawText = ""
                    )
                    DataManager.addTransaction(txn)
                    // Update account balance
                    selectedAcc?.let { acc ->
                        DataManager.updateAccountBalance(acc.id, if (isIncome) amount else -amount)
                    }
                } else {
                    val updated = existing.copy(
                        amount = amount,
                        isIncome = isIncome,
                        categoryId = selectedCat?.id ?: existing.categoryId,
                        accountId = selectedAcc?.id ?: existing.accountId,
                        note = note,
                        title = selectedCat?.name ?: existing.title
                    )
                    DataManager.updateTransaction(updated)
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDeleteDialog(txn: Transaction) {
        var currentTxn = txn
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_detail, null)
        
        val llHeader = dialogView.findViewById<LinearLayout>(R.id.llHeader)
        val tvClose = dialogView.findViewById<TextView>(R.id.tvClose)
        val ivDelete = dialogView.findViewById<ImageView>(R.id.ivDelete)
        val ivEdit = dialogView.findViewById<ImageView>(R.id.ivEdit)
        val tvType = dialogView.findViewById<TextView>(R.id.tvType)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvAmount)
        val tvDateTime = dialogView.findViewById<TextView>(R.id.tvDateTime)
        
        val tvAccName = dialogView.findViewById<TextView>(R.id.tvAccName)
        val tvCatName = dialogView.findViewById<TextView>(R.id.tvCatName)
        val ivCatIcon = dialogView.findViewById<ImageView>(R.id.ivCatIcon)
        val tvCatEmojiFallback = dialogView.findViewById<TextView>(R.id.tvCatEmojiFallback)
        val cvCatIcon = dialogView.findViewById<View>(R.id.cvCatIcon)
        val tvNotes = dialogView.findViewById<TextView>(R.id.tvNotes)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirm)
        
        // Hide edit pen as requested
        ivEdit.visibility = View.GONE
        
        // Helper to bind all views to the dialog layout
        fun bindViews() {
            // 1. Transaction Type and Color Styling
            if (currentTxn.isIncome) {
                tvType.text = "INCOME"
                llHeader.setBackgroundColor(Color.parseColor("#77C388")) // Soft Green
                tvAmount.text = "+S$%.2f".format(currentTxn.amount)
            } else {
                tvType.text = "EXPENSE"
                llHeader.setBackgroundColor(Color.parseColor("#E26C59")) // Soft Coral
                tvAmount.text = "-S$%.2f".format(currentTxn.amount)
            }
            
            // 2. Date & Time
            tvDateTime.text = currentTxn.date
            
            // 3. Account Name
            val acc = DataManager.getAccountById(currentTxn.accountId)
            tvAccName.text = acc?.name ?: "No account"
            
            // 4. Category Name & Icon
            val cat = DataManager.getCategoryById(currentTxn.categoryId)
            tvCatName.text = cat?.name ?: currentTxn.title
            
            if (cat != null) {
                val resId = CategoryIcons.getDrawableResId(requireContext(), cat.iconName, cat.name)
                if (resId != 0) {
                    ivCatIcon.visibility = View.VISIBLE
                    ivCatIcon.setImageResource(resId)
                    tvCatEmojiFallback.visibility = View.GONE
                    if (cvCatIcon is CardView) {
                        cvCatIcon.setCardBackgroundColor(Color.TRANSPARENT)
                    }
                } else {
                    ivCatIcon.visibility = View.GONE
                    tvCatEmojiFallback.visibility = View.VISIBLE
                    tvCatEmojiFallback.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
                    if (cvCatIcon is CardView) {
                        cvCatIcon.setCardBackgroundColor(Color.parseColor(cat.colorHex))
                    }
                }
            } else {
                ivCatIcon.visibility = View.GONE
                tvCatEmojiFallback.visibility = View.VISIBLE
                tvCatEmojiFallback.text = "💸"
                if (cvCatIcon is CardView) {
                    cvCatIcon.setCardBackgroundColor(Color.GRAY)
                }
            }
            
            // 5. Notes
            tvNotes.text = if (currentTxn.note.isNullOrEmpty()) "No notes" else currentTxn.note

            // 6. Confirm button visibility
            if (!currentTxn.isConfirmed) {
                btnConfirm.visibility = View.VISIBLE
            } else {
                btnConfirm.visibility = View.GONE
            }
        }
        
        bindViews()
        
        // Create and show custom dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        
        // Click Listeners
        tvClose.setOnClickListener { dialog.dismiss() }
        
        // Tap to Confirm & Save directly from the popup list/overlay
        btnConfirm.setOnClickListener {
            // Confirm the transaction!
            currentTxn = currentTxn.copy(isConfirmed = true)
            DataManager.updateTransaction(currentTxn)
            
            // Apply balance now!
            val acc = DataManager.getAccountById(currentTxn.accountId)
            acc?.let { a ->
                DataManager.updateAccountBalance(a.id, if (currentTxn.isIncome) currentTxn.amount else -currentTxn.amount)
            }
            
            refresh()
            dialog.dismiss()
        }

        // Tap to Change Account Pill
        val btnAccountPill = tvAccName.parent as View
        btnAccountPill.setOnClickListener {
            val accounts = DataManager.getAccounts()
            val names = accounts.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Account")
                .setItems(names) { _, index ->
                    val selectedAcc = accounts[index]
                    val oldAccId = currentTxn.accountId
                    if (oldAccId != selectedAcc.id) {
                        if (currentTxn.isConfirmed) {
                            // Adjust balances only for confirmed transactions to keep ledger accurate!
                            DataManager.updateAccountBalance(oldAccId, if (currentTxn.isIncome) -currentTxn.amount else currentTxn.amount)
                            DataManager.updateAccountBalance(selectedAcc.id, if (currentTxn.isIncome) currentTxn.amount else -currentTxn.amount)
                        }
                        
                        currentTxn = currentTxn.copy(accountId = selectedAcc.id)
                        DataManager.updateTransaction(currentTxn)
                        bindViews()
                        refresh()
                    }
                }
                .show()
        }
        
        // Tap to Change Category Pill (Lists all categories & switches type automatically)
        val btnCategoryPill = tvCatName.parent as View
        btnCategoryPill.setOnClickListener {
            val categories = DataManager.getCategories()
            val names = categories.map { "${it.name} (${it.type.lowercase().replaceFirstChar { c -> c.uppercase() }})" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Category")
                .setItems(names) { _, index ->
                    val selectedCat = categories[index]
                    val oldIsIncome = currentTxn.isIncome
                    val newIsIncome = selectedCat.type.equals("income", true)
                    
                    if (currentTxn.isConfirmed && oldIsIncome != newIsIncome) {
                        val acc = DataManager.getAccountById(currentTxn.accountId)
                        acc?.let { a ->
                            // Refund old type direction
                            DataManager.updateAccountBalance(a.id, if (oldIsIncome) -currentTxn.amount else currentTxn.amount)
                            // Charge new type direction
                            DataManager.updateAccountBalance(a.id, if (newIsIncome) currentTxn.amount else -currentTxn.amount)
                        }
                    }
                    
                    currentTxn = currentTxn.copy(
                        isIncome = newIsIncome,
                        categoryId = selectedCat.id,
                        title = selectedCat.name
                    )
                    DataManager.updateTransaction(currentTxn)
                    bindViews()
                    refresh()
                }
                .show()
        }
        
        // Tap on type text to directly toggle Income / Expense
        tvType.setOnClickListener {
            val options = arrayOf("Expense", "Income")
            AlertDialog.Builder(requireContext())
                .setTitle("Change Transaction Type")
                .setItems(options) { _, index ->
                    val newIsIncome = index == 1
                    val oldIsIncome = currentTxn.isIncome
                    if (oldIsIncome != newIsIncome) {
                        if (currentTxn.isConfirmed) {
                            val acc = DataManager.getAccountById(currentTxn.accountId)
                            acc?.let { a ->
                                DataManager.updateAccountBalance(a.id, if (oldIsIncome) -currentTxn.amount else currentTxn.amount)
                                DataManager.updateAccountBalance(a.id, if (newIsIncome) currentTxn.amount else -currentTxn.amount)
                            }
                        }
                        
                        // Select default category for the new type
                        val defaultCatId = if (newIsIncome) "others_inc" else "others_exp"
                        val defaultCat = DataManager.getCategoryById(defaultCatId)
                        
                        currentTxn = currentTxn.copy(
                            isIncome = newIsIncome,
                            categoryId = defaultCatId,
                            title = defaultCat?.name ?: "Others"
                        )
                        DataManager.updateTransaction(currentTxn)
                        bindViews()
                        refresh()
                    }
                }
                .show()
        }
        
        // Tap to Change Note Directly
        tvNotes.setOnClickListener {
            val et = EditText(requireContext()).apply {
                hint = "Enter note..."
                setText(if (currentTxn.note == "No notes") "" else currentTxn.note)
                setPadding(32, 32, 32, 32)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Edit Note")
                .setView(et)
                .setPositiveButton("Save") { _, _ ->
                    val newNote = et.text.toString().trim()
                    currentTxn = currentTxn.copy(note = newNote)
                    DataManager.updateTransaction(currentTxn)
                    bindViews()
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        ivDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete") { _, _ ->
                    DataManager.deleteTransaction(currentTxn.id)
                    if (currentTxn.isConfirmed) {
                        val acc = DataManager.getAccountById(currentTxn.accountId)
                        acc?.let { a ->
                            DataManager.updateAccountBalance(a.id, if (currentTxn.isIncome) -currentTxn.amount else currentTxn.amount)
                        }
                    }
                    refresh()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        dialog.show()
    }
}
