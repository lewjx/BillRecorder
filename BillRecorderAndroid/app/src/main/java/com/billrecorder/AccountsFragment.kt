package com.billrecorder

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class AccountsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_accounts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.fabAddAccount).setOnClickListener { showAddAccountDialog() }
        refresh(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { refresh(it) }
    }

    private fun refresh(view: View) {
        val accounts = DataManager.getAccounts()
        val allBalance = accounts.sumOf { it.balance }
        val allTxns = DataManager.getTransactions()
        val totalExpense = allTxns.filter { !it.isIncome }.sumOf { it.amount }
        val totalIncome = allTxns.filter { it.isIncome }.sumOf { it.amount }

        view.findViewById<TextView>(R.id.tvAllAccounts).text = "[ All Accounts S${"%.2f".format(allBalance)} ]"
        view.findViewById<TextView>(R.id.tvExpenseSoFar).text = "S${"%.2f".format(totalExpense)}"
        view.findViewById<TextView>(R.id.tvIncomeSoFar).text = "S${"%.2f".format(totalIncome)}"

        val rv = view.findViewById<RecyclerView>(R.id.rvAccounts)
        rv.layoutManager = LinearLayoutManager(requireContext())

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class AVH(v: View) : RecyclerView.ViewHolder(v) {
                val tvName: TextView = v.findViewById(R.id.tvAccountName)
                val tvBalance: TextView = v.findViewById(R.id.tvAccountBalance)
                val tvMenu: TextView = v.findViewById(R.id.tvAccountMenu)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                AVH(LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false))

            override fun getItemCount() = accounts.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val avh = holder as AVH
                val acc = accounts[position]
                avh.tvName.text = acc.name
                avh.tvBalance.text = "Balance: S${"%.2f".format(acc.balance)}"
                avh.tvMenu.setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(acc.name)
                        .setNegativeButton("Delete") { _, _ ->
                            DataManager.deleteAccount(acc.id)
                            refresh(view)
                        }
                        .setNeutralButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun showAddAccountDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etName = EditText(requireContext()).apply { hint = "Account name (e.g. OCBC)" }
        val etBalance = EditText(requireContext()).apply {
            hint = "Starting balance"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(etName)
        layout.addView(etBalance)

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Account")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().ifEmpty { return@setPositiveButton }
                val balance = etBalance.text.toString().toDoubleOrNull() ?: 0.0
                DataManager.addAccount(Account(UUID.randomUUID().toString(), name, name, balance))
                view?.let { refresh(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
