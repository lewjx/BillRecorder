package com.billrecorder

import android.content.Context

object CategoryIcons {
    private val nameToEmoji = mapOf(
        "baby" to "🍼",
        "beauty" to "🌹",
        "bills" to "📄",
        "car" to "🚗",
        "clothing" to "👕",
        "education" to "🎓",
        "electronics" to "🔌",
        "entertainment" to "🎬",
        "food" to "🍴",
        "health" to "🩺",
        "home" to "🏠",
        "insurance" to "🛡️",
        "shopping" to "🛒",
        "social" to "👥",
        "sport" to "🎾",
        "tax" to "✂️",
        "telephone" to "📞",
        "transportation" to "🚌",
        "transport" to "🚌",
        "buffer" to "👛",
        "grocery" to "🛒",
        "investment" to "💰",
        "transfer" to "🔄",
        "salary" to "💼",
        "awards" to "🏆",
        "refunds" to "🔄",
        "rental" to "🏠",
        "sale" to "🏷️",
        "others" to "💸"
    )

    private val nameToDrawable = mapOf(
        "baby" to "ic_baby",
        "beauty" to "ic_beauty",
        "bills" to "ic_bills",
        "car" to "ic_car",
        "clothing" to "ic_clothing",
        "education" to "ic_education",
        "electronics" to "ic_electronics",
        "entertainment" to "ic_entertainment",
        "food" to "ic_food",
        "health" to "ic_health",
        "home" to "ic_home",
        "insurance" to "ic_insurance",
        "shopping" to "ic_shopping",
        "social" to "ic_social",
        "sport" to "ic_sport",
        "tax" to "ic_tax",
        "telephone" to "ic_telephone",
        "transportation" to "ic_transportation",
        "transport" to "ic_transportation",
        "buffer" to "ic_buffer",
        "grocery" to "ic_grocery",
        "investment" to "ic_investment",
        "transfer" to "ic_transfer"
    )

    private val resIdCache = mutableMapOf<Pair<String, String>, Int>()

    fun getDrawableResId(context: Context, iconName: String, categoryName: String): Int {
        val keyByIcon = iconName.lowercase().trim()
        val keyByName = categoryName.lowercase().trim()
        val cacheKey = Pair(keyByIcon, keyByName)
        
        resIdCache[cacheKey]?.let { return it }

        var foundResId = 0

        // 1. Direct match on iconName
        nameToDrawable[keyByIcon]?.let {
            val resId = context.resources.getIdentifier(it, "drawable", context.packageName)
            if (resId != 0) foundResId = resId
        }

        // 2. Direct match on categoryName
        if (foundResId == 0) {
            nameToDrawable[keyByName]?.let {
                val resId = context.resources.getIdentifier(it, "drawable", context.packageName)
                if (resId != 0) foundResId = resId
            }
        }

        // 3. Substring match on categoryName
        if (foundResId == 0) {
            for ((k, v) in nameToDrawable) {
                if (keyByName.contains(k) || k.contains(keyByName)) {
                    val resId = context.resources.getIdentifier(v, "drawable", context.packageName)
                    if (resId != 0) {
                        foundResId = resId
                        break
                    }
                }
            }
        }

        resIdCache[cacheKey] = foundResId
        return foundResId
    }

    fun getEmoji(iconName: String, categoryName: String): String {
        val keyByIcon = iconName.lowercase().trim()
        val keyByName = categoryName.lowercase().trim()

        // 1. Direct match on iconName
        nameToEmoji[keyByIcon]?.let { return it }

        // 2. Direct match on categoryName
        nameToEmoji[keyByName]?.let { return it }

        // 3. Substring match on categoryName
        for ((k, v) in nameToEmoji) {
            if (keyByName.contains(k) || k.contains(keyByName)) {
                return v
            }
        }

        // 4. Default fallbacks
        return "💸"
    }

    fun showIconPickerDialog(context: Context, onIconSelected: (String) -> Unit) {
        val icons = listOf(
            "baby", "beauty", "bills", "car", "clothing", "education", 
            "electronics", "entertainment", "food", "health", "home", 
            "insurance", "shopping", "social", "sport", "tax", "telephone", 
            "transportation", "buffer", "grocery", "investment", "investment_2", 
            "transfer", "others"
        )
        
        val grid = androidx.recyclerview.widget.RecyclerView(context).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 4)
            setPadding(24, 24, 24, 24)
            clipToPadding = false
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Select Icon")
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .create()
            
        grid.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class VH(val iv: android.widget.ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(iv)
            
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val iv = android.widget.ImageView(context).apply {
                    val size = (context.resources.displayMetrics.density * 56).toInt()
                    layoutParams = android.view.ViewGroup.MarginLayoutParams(size, size).apply {
                        setMargins(12, 12, 12, 12)
                    }
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
                return VH(iv)
            }
            
            override fun getItemCount() = icons.size
            
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val vh = holder as VH
                val name = icons[position]
                val resId = getDrawableResId(context, name, name)
                if (resId != 0) {
                    vh.iv.setImageResource(resId)
                } else {
                    vh.iv.setImageResource(android.R.drawable.ic_menu_help)
                }
                vh.iv.setOnClickListener {
                    onIconSelected(name)
                    dialog.dismiss()
                }
            }
        }
        
        dialog.show()
    }
}
