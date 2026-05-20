package com.billrecorder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

sealed class ListItem {
    data class Header(val dateStr: String) : ListItem()
    data class Txn(val transaction: Transaction) : ListItem()
}

class TransactionAdapter(
    private var items: List<ListItem>,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_TXN = 1

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDateHeader: TextView = view.findViewById(R.id.tvDateHeader)
    }

    class TxnViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cvIconBg: CardView = view.findViewById(R.id.cvIconBg)
        val ivIcon: android.widget.ImageView = view.findViewById(R.id.ivIcon)
        val ivDefaultIcon: android.widget.ImageView = view.findViewById(R.id.ivDefaultIcon)
        val tvEmojiFallback: TextView = view.findViewById(R.id.tvEmojiFallback)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvBank: TextView = view.findViewById(R.id.tvBank)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.Txn -> TYPE_TXN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false))
        } else {
            TxnViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is ListItem.Header) {
            holder.tvDateHeader.text = item.dateStr
        } else if (holder is TxnViewHolder && item is ListItem.Txn) {
            val txn = item.transaction
            holder.tvTitle.text = txn.title

            // Category color from DataManager
            val cat = DataManager.getCategoryById(txn.categoryId)
            val colorHex = cat?.colorHex ?: (if (txn.isIncome) "#77C388" else "#E26C59")

            if (cat != null) {
                val resId = CategoryIcons.getDrawableResId(holder.itemView.context, cat.iconName, cat.name)
                if (resId != 0) {
                    holder.ivIcon.visibility = View.VISIBLE
                    holder.ivIcon.setImageResource(resId)
                    holder.tvEmojiFallback.visibility = View.GONE
                    holder.ivDefaultIcon.visibility = View.GONE
                    holder.cvIconBg.setCardBackgroundColor(Color.TRANSPARENT)
                } else {
                    holder.ivIcon.visibility = View.GONE
                    holder.tvEmojiFallback.visibility = View.VISIBLE
                    holder.tvEmojiFallback.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
                    holder.ivDefaultIcon.visibility = View.GONE
                    holder.cvIconBg.setCardBackgroundColor(getCachedColor(colorHex))
                }
            } else {
                holder.ivIcon.visibility = View.GONE
                holder.tvEmojiFallback.visibility = View.VISIBLE
                holder.tvEmojiFallback.text = "💸"
                holder.ivDefaultIcon.visibility = View.GONE
                holder.cvIconBg.setCardBackgroundColor(getCachedColor(colorHex))
            }

            // Bank/account tag
            val acc = DataManager.getAccountById(txn.accountId)
            holder.tvBank.text = acc?.name?.lowercase() ?: txn.accountId.ifEmpty { "bank" }

            val sign = if (txn.isIncome) "+" else "-"
            val amtColor = if (txn.isIncome) "#77C388" else "#E26C59"
            holder.tvAmount.setTextColor(getCachedColor(amtColor))
            holder.tvAmount.text = "${sign}S\$${df.format(txn.amount)}"

            holder.itemView.setOnClickListener { onItemClick(txn) }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(transactions: List<Transaction>) {
        items = buildItems(transactions)
        notifyDataSetChanged()
    }

    companion object {
        private val df = java.text.DecimalFormat("0.00")
        private val colorCache = mutableMapOf<String, Int>()

        private fun getCachedColor(hex: String): Int {
            return colorCache.getOrPut(hex) {
                try {
                    Color.parseColor(hex)
                } catch (e: Exception) {
                    Color.GRAY
                }
            }
        }

        private fun buildItems(transactions: List<Transaction>): List<ListItem> {
            val result = mutableListOf<ListItem>()
            val sdfIn = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sdfGroup = SimpleDateFormat("MMM dd, EEEE", Locale.getDefault())
            var currentDate = ""
            transactions.forEach { txn ->
                val parsed = try { sdfIn.parse(txn.date) } catch (e: Exception) { null }
                val dateStr = if (parsed != null) sdfGroup.format(parsed) else txn.date.take(10)
                if (dateStr != currentDate) {
                    result.add(ListItem.Header(dateStr))
                    currentDate = dateStr
                }
                result.add(ListItem.Txn(txn))
            }
            return result
        }
    }
}
