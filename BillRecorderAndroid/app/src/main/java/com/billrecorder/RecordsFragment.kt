package com.billrecorder

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun currentMonthYear(): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)

    private fun refresh() {
        val sdfDisplay = SimpleDateFormat("MMM, yyyy", Locale.getDefault())
        tvMonth.text = sdfDisplay.format(currentCalendar.time)

        val monthYear = currentMonthYear()
        val allTxns = DataManager.getTransactions()
            .filter { it.date.startsWith(monthYear) }
            .sortedByDescending { it.date }

        val expense = allTxns.filter { !it.isIncome }.sumOf { it.amount }
        val income = allTxns.filter { it.isIncome }.sumOf { it.amount }
        val total = income - expense

        tvTotalExpense.text = "S$%.2f".format(expense)
        tvTotalIncome.text = "S$%.2f".format(income)
        tvTotal.text = "S$%.2f".format(total)
        tvTotal.setTextColor(Color.parseColor(if (total >= 0) "#77C388" else "#E26C59"))

        adapter.updateData(allTxns)
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
        AlertDialog.Builder(requireContext())
            .setTitle(txn.title)
            .setMessage("S$%.2f - ${txn.date}".format(txn.amount))
            .setPositiveButton("Edit") { _, _ -> showAddTransactionDialog(txn) }
            .setNegativeButton("Delete") { _, _ ->
                DataManager.deleteTransaction(txn.id)
                refresh()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
}
