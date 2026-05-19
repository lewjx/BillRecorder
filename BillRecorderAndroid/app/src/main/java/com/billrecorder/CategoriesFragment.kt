package com.billrecorder

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class CategoriesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.fabAddCategory).setOnClickListener { showAddCategoryDialog() }
        refresh(view)
    }

    private fun refresh(view: View) {
        val categories = DataManager.getCategories()
        val income = categories.filter { it.type == "income" }
        val expense = categories.filter { it.type == "expense" }

        // Build flat list with section headers
        data class SectionHeader(val title: String)
        val allItems: List<Any> = listOf(SectionHeader("Income categories")) + income +
                listOf(SectionHeader("Expense categories")) + expense

        val rv = view.findViewById<RecyclerView>(R.id.rvCategories)
        rv.layoutManager = LinearLayoutManager(requireContext())

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
                val tv: TextView = v.findViewById(R.id.tvDateHeader)
            }

            inner class CatVH(v: View) : RecyclerView.ViewHolder(v) {
                val cvIcon: CardView = v.findViewById(R.id.cvIcon)
                val ivIcon: ImageView = v.findViewById(R.id.ivIcon)
                val tvIcon: TextView = v.findViewById(R.id.tvIcon)
                val tvName: TextView = v.findViewById(R.id.tvCatName)
                val tvMenu: TextView = v.findViewById(R.id.tvMenu)
            }

            override fun getItemViewType(position: Int) = if (allItems[position] is SectionHeader) 0 else 1

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return if (viewType == 0)
                    HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false))
                else
                    CatVH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))
            }

            override fun getItemCount() = allItems.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = allItems[position]
                if (holder is HeaderVH && item is SectionHeader) {
                    holder.tv.text = item.title
                } else if (holder is CatVH && item is Category) {
                    val resId = CategoryIcons.getDrawableResId(holder.itemView.context, item.iconName, item.name)
                    if (resId != 0) {
                        holder.ivIcon.visibility = View.VISIBLE
                        holder.ivIcon.setImageResource(resId)
                        holder.tvIcon.visibility = View.GONE
                        holder.cvIcon.setCardBackgroundColor(Color.TRANSPARENT)
                    } else {
                        holder.ivIcon.visibility = View.GONE
                        holder.tvIcon.visibility = View.VISIBLE
                        holder.tvIcon.text = CategoryIcons.getEmoji(item.iconName, item.name)
                        holder.cvIcon.setCardBackgroundColor(Color.parseColor(item.colorHex))
                    }
                    holder.tvName.text = item.name
                    holder.cvIcon.setOnClickListener {
                        CategoryIcons.showIconPickerDialog(holder.itemView.context) { selectedIcon ->
                            DataManager.updateCategoryIcon(item.id, selectedIcon)
                            refresh(view)
                        }
                    }
                    holder.tvMenu.setOnClickListener {
                        AlertDialog.Builder(requireContext())
                            .setTitle(item.name)
                            .setNegativeButton("Delete") { _, _ ->
                                DataManager.deleteCategory(item.id)
                                refresh(view)
                            }
                            .setNeutralButton("Cancel", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun showAddCategoryDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etName = EditText(requireContext()).apply { hint = "Category name" }
        val rgType = RadioGroup(requireContext()).apply { orientation = RadioGroup.HORIZONTAL }
        val rbExp = RadioButton(requireContext()).apply { text = "Expense"; id = View.generateViewId() }
        val rbInc = RadioButton(requireContext()).apply { text = "Income"; id = View.generateViewId() }
        rbExp.isChecked = true
        rgType.addView(rbExp)
        rgType.addView(rbInc)
        
        var selectedIcon = "others"
        val btnSelectIcon = Button(requireContext()).apply {
            text = "Select Icon: others"
            setOnClickListener {
                CategoryIcons.showIconPickerDialog(requireContext()) { iconName ->
                    selectedIcon = iconName
                    text = "Select Icon: $iconName"
                }
            }
        }
        
        layout.addView(etName)
        layout.addView(rgType)
        layout.addView(btnSelectIcon)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Category")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().ifEmpty { return@setPositiveButton }
                val type = if (rgType.checkedRadioButtonId == rbInc.id) "income" else "expense"
                val cat = Category(
                    id = "${name.lowercase().replace(" ", "_")}_${type.take(3)}",
                    name = name, type = type, colorHex = "#805AD5", iconName = selectedIcon
                )
                DataManager.addCategory(cat)
                view?.let { refresh(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
