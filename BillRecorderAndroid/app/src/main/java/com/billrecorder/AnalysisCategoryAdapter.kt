package com.billrecorder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class AnalysisCategoryAdapter(
    private val items: List<Pair<Category, Double>>,
    private val totalExpense: Double
) : RecyclerView.Adapter<AnalysisCategoryAdapter.ViewHolder>() {

    private val categoryEmojis = mapOf(
        "food" to "🍴", "transport" to "🚌", "grocery" to "🛒", "shopping" to "🛍",
        "entertainment" to "🎮", "bills" to "📄", "clothing" to "👕",
        "education" to "📚", "health" to "💊", "others" to "💸"
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cvIcon: CardView = view.findViewById(R.id.cvIcon)
        val tvCatIcon: TextView = view.findViewById(R.id.tvCatIcon)
        val tvCatName: TextView = view.findViewById(R.id.tvCatName)
        val tvCatAmount: TextView = view.findViewById(R.id.tvCatAmount)
        val tvCatPercent: TextView = view.findViewById(R.id.tvCatPercent)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_analysis_row, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (cat, amount) = items[position]
        val percent = if (totalExpense > 0) (amount / totalExpense * 100) else 0.0
        val color = Color.parseColor(cat.colorHex)

        holder.cvIcon.setCardBackgroundColor(color)
        holder.tvCatIcon.text = categoryEmojis[cat.iconName] ?: "💰"
        holder.tvCatName.text = cat.name
        holder.tvCatAmount.text = "-S$%.2f".format(amount)
        holder.tvCatAmount.setTextColor(color)
        holder.tvCatPercent.text = "%.2f%%".format(percent)
        holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
        holder.progressBar.progress = percent.toInt()
    }

    override fun getItemCount() = items.size
}
