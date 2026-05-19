package com.billrecorder

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class BudgetsFragment : Fragment() {

    private var currentCalendar = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_budgets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.btnPrevMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1); refresh(view)
        }
        view.findViewById<TextView>(R.id.btnNextMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1); refresh(view)
        }
        refresh(view)
    }

    private fun monthYear() = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)

    private fun refresh(view: View) {
        view.findViewById<TextView>(R.id.tvMonth).text =
            SimpleDateFormat("MMM, yyyy", Locale.getDefault()).format(currentCalendar.time)

        val budgets = DataManager.getBudgets().filter { it.monthYear == monthYear() }
        val totalBudget = budgets.sumOf { it.limit }
        val totalSpent = DataManager.getTotalExpenseForMonth(monthYear())

        view.findViewById<TextView>(R.id.tvTotalBudget).text = "S$%.2f".format(totalBudget)
        view.findViewById<TextView>(R.id.tvTotalSpent).text = "S$%.2f".format(totalSpent)

        val categories = DataManager.getCategories().filter { it.type == "expense" }
        val rv = view.findViewById<RecyclerView>(R.id.rvBudgets)
        rv.layoutManager = LinearLayoutManager(requireContext())

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class BVH(v: View) : RecyclerView.ViewHolder(v) {
                val cvIcon: CardView = v.findViewById(R.id.cvIcon)
                val tvIcon: TextView = v.findViewById(R.id.tvIcon)
                val tvName: TextView = v.findViewById(R.id.tvCatName)
                val tvCurrentBudget: TextView = v.findViewById(R.id.tvCurrentBudget)
                val btnSet: Button = v.findViewById(R.id.btnSetBudget)
            }

            private val categoryEmojis = mapOf(
                "food" to "🍴", "transport" to "🚌", "grocery" to "🛒", "shopping" to "🛍",
                "entertainment" to "🎮", "bills" to "📄", "clothing" to "👕",
                "education" to "📚", "health" to "💊", "others" to "💸"
            )

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                BVH(LayoutInflater.from(parent.context).inflate(R.layout.item_budget, parent, false))

            override fun getItemCount() = categories.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val bvh = holder as BVH
                val cat = categories[position]
                val existing = budgets.find { it.categoryId == cat.id }
                bvh.tvIcon.text = categoryEmojis[cat.iconName] ?: "💰"
                android.graphics.Color.parseColor(cat.colorHex).let { bvh.cvIcon.setCardBackgroundColor(it) }
                bvh.tvName.text = cat.name
                
                if (existing != null) {
                    bvh.tvCurrentBudget.visibility = View.VISIBLE
                    bvh.tvCurrentBudget.text = "S$%.2f".format(existing.limit)
                    bvh.btnSet.text = "EDIT"
                } else {
                    bvh.tvCurrentBudget.visibility = View.GONE
                    bvh.btnSet.text = "SET BUDGET"
                }
                
                bvh.btnSet.setOnClickListener { showSetBudgetDialog(cat, existing) }
            }
        }
    }

    private fun showSetBudgetDialog(cat: Category, existing: Budget?) {
        val et = EditText(requireContext()).apply {
            hint = "Enter budget limit"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 32, 32, 32)
            existing?.let { setText(it.limit.toString()) }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Set budget for ${cat.name}")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val limit = et.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                DataManager.setBudget(Budget(UUID.randomUUID().toString(), cat.id, monthYear(), limit))
                view?.let { refresh(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
