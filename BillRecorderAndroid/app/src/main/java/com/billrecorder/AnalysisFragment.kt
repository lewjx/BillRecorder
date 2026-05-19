package com.billrecorder

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.text.SimpleDateFormat
import java.util.*

class AnalysisFragment : Fragment() {

    private var currentCalendar = Calendar.getInstance()
    private var showIncome = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analysis, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnExpenses = view.findViewById<TextView>(R.id.btnToggleExpenses)
        val btnIncome = view.findViewById<TextView>(R.id.btnToggleIncome)

        btnExpenses.setOnClickListener {
            if (showIncome) {
                showIncome = false
                updateToggleUI(view)
                refresh(view)
            }
        }
        btnIncome.setOnClickListener {
            if (!showIncome) {
                showIncome = true
                updateToggleUI(view)
                refresh(view)
            }
        }

        view.findViewById<TextView>(R.id.btnPrevMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1); refresh(view)
        }
        view.findViewById<TextView>(R.id.btnNextMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1); refresh(view)
        }
        updateToggleUI(view)
        refresh(view)
    }

    private fun updateToggleUI(view: View) {
        val btnExpenses = view.findViewById<TextView>(R.id.btnToggleExpenses)
        val btnIncome = view.findViewById<TextView>(R.id.btnToggleIncome)

        if (showIncome) {
            btnExpenses.setBackgroundResource(R.drawable.toggle_inactive_bg)
            btnExpenses.setTextColor(Color.parseColor("#F1E3A1"))
            btnIncome.setBackgroundResource(R.drawable.toggle_active_bg)
            btnIncome.setTextColor(Color.parseColor("#403F3B"))
        } else {
            btnExpenses.setBackgroundResource(R.drawable.toggle_active_bg)
            btnExpenses.setTextColor(Color.parseColor("#403F3B"))
            btnIncome.setBackgroundResource(R.drawable.toggle_inactive_bg)
            btnIncome.setTextColor(Color.parseColor("#F1E3A1"))
        }
    }

    private fun refresh(view: View) {
        val sdfDisplay = SimpleDateFormat("MMM, yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvMonth).text = sdfDisplay.format(currentCalendar.time)
        val monthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)

        val allTxns = DataManager.getTransactions().filter { it.date.startsWith(monthYear) }
        val expense = allTxns.filter { !it.isIncome }.sumOf { it.amount }
        val income = allTxns.filter { it.isIncome }.sumOf { it.amount }
        val total = income - expense

        view.findViewById<TextView>(R.id.tvTotalExpense).text = "S$%.2f".format(expense)
        view.findViewById<TextView>(R.id.tvTotalIncome).text = "S$%.2f".format(income)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotal)
        tvTotal.text = "S$%.2f".format(total)
        tvTotal.setTextColor(Color.parseColor(if (total >= 0) "#77C388" else "#E26C59"))

        // Filter and group based on selected type (Income vs Expense)
        val filteredTxns = allTxns.filter { it.isIncome == showIncome }
        val targetCategories = DataManager.getCategories().filter {
            it.type.equals(if (showIncome) "income" else "expense", true)
        }
        val grouped = targetCategories.mapNotNull { cat ->
            val sum = filteredTxns.filter { it.categoryId == cat.id }.sumOf { it.amount }
            if (sum > 0) Pair(cat, sum) else null
        }.sortedByDescending { it.second }

        val totalAmount = if (showIncome) income else expense

        // Donut / Pie chart
        val pieChart = view.findViewById<PieChart>(R.id.pieChart)
        val entries = grouped.map { PieEntry(it.second.toFloat(), it.first.name) }
        val colors = grouped.map { Color.parseColor(it.first.colorHex) }
        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            valueTextColor = Color.WHITE
            valueTextSize = 10f
            sliceSpace = 3f
        }
        pieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 55f
            setHoleColor(Color.parseColor("#403F3B"))
            centerText = if (showIncome) "Income" else "Expenses"
            setCenterTextColor(Color.parseColor("#F1E3A1"))
            setCenterTextSize(14f)
            legend.textColor = Color.parseColor("#F1E3A1")
            setEntryLabelColor(Color.TRANSPARENT)
            animateY(700)
            invalidate()
        }

        // Category rows list
        val rv = view.findViewById<RecyclerView>(R.id.rvCategories)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = AnalysisCategoryAdapter(grouped, totalAmount, showIncome)
    }
}
