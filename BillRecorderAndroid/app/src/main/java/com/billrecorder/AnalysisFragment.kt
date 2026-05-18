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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analysis, container, false)
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

        // Pie chart
        val expenseTxns = allTxns.filter { !it.isIncome }
        val categories = DataManager.getCategories().filter { it.type == "expense" }
        val grouped = categories.mapNotNull { cat ->
            val sum = expenseTxns.filter { it.categoryId == cat.id }.sumOf { it.amount }
            if (sum > 0) Pair(cat, sum) else null
        }.sortedByDescending { it.second }

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
            centerText = "Expenses"
            setCenterTextColor(Color.parseColor("#F1E3A1"))
            setCenterTextSize(14f)
            legend.textColor = Color.parseColor("#F1E3A1")
            setEntryLabelColor(Color.TRANSPARENT)
            animateY(700)
            invalidate()
        }

        // Category rows
        val rv = view.findViewById<RecyclerView>(R.id.rvCategories)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = AnalysisCategoryAdapter(grouped, expense)
    }
}
