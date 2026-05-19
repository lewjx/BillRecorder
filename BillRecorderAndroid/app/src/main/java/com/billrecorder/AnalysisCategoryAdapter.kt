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
    private val totalAmount: Double,
    private val isIncome: Boolean
) : RecyclerView.Adapter<AnalysisCategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cvIcon: CardView = view.findViewById(R.id.cvIcon)
        val ivIcon: android.widget.ImageView = view.findViewById(R.id.ivIcon)
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
        val percent = if (totalAmount > 0) (amount / totalAmount * 100) else 0.0
        val color = Color.parseColor(cat.colorHex)

        val resId = CategoryIcons.getDrawableResId(holder.itemView.context, cat.iconName, cat.name)
        if (resId != 0) {
            holder.ivIcon.visibility = View.VISIBLE
            holder.ivIcon.setImageResource(resId)
            holder.tvCatIcon.visibility = View.GONE
            holder.cvIcon.setCardBackgroundColor(Color.TRANSPARENT)
        } else {
            holder.ivIcon.visibility = View.GONE
            holder.tvCatIcon.visibility = View.VISIBLE
            holder.tvCatIcon.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
            holder.cvIcon.setCardBackgroundColor(color)
        }

        holder.tvCatName.text = cat.name
        val sign = if (isIncome) "+" else "-"
        holder.tvCatAmount.text = "${sign}S$%.2f".format(amount)
        holder.tvCatAmount.setTextColor(color)
        holder.tvCatPercent.text = "%.2f%%".format(percent)
        holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
        holder.progressBar.progress = percent.toInt()
    }

    override fun getItemCount() = items.size
}
