package com.billrecorder

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView
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
    private var isUnconfirmedExpanded = false
    private var selectedFilterAccountId: String? = null

    private val billReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            refresh()
        }
    }

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

        view.findViewById<ImageView>(R.id.btnMenu).setOnClickListener {
            (requireActivity() as MainActivity).openDrawer()
        }

        view.findViewById<TextView>(R.id.btnPrevMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            refresh()
        }
        view.findViewById<TextView>(R.id.btnNextMonth).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            refresh()
        }
        val fabAdd = view.findViewById<View>(R.id.fabAdd)
        fabAdd.setOnClickListener { showAddTransactionDialog() }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && fabAdd.translationY == 0f) {
                    fabAdd.animate().translationY(fabAdd.height.toFloat() + 100f).setDuration(200).start()
                } else if (dy < 0 && fabAdd.translationY > 0f) {
                    fabAdd.animate().translationY(0f).setDuration(200).start()
                }
            }
        })
        
        // 🚀 Auto-run Test Trigger: Long press FAB to trigger test notifications directly
        view.findViewById<View>(R.id.fabAdd).setOnLongClickListener {
            val options = arrayOf(
                "Simulate SimplyGo Transit notification",
                "Simulate DBS Bank notification",
                "Simulate GrabPay Wallet notification"
            )
            AlertDialog.Builder(requireContext())
                .setTitle("Select Test Notification")
                .setItems(options) { _, index ->
                    val intent = Intent("com.billrecorder.TRIGGER_TEST")
                    when (index) {
                        0 -> {
                            intent.putExtra("package", "com.transitlink.simplygo")
                            intent.putExtra("title", "SimplyGo")
                            intent.putExtra("text", "Spent S$2.62 on MRT/Bus travel")
                        }
                        1 -> {
                            intent.putExtra("package", "sg.com.dbs.mobile.banking")
                            intent.putExtra("title", "DBS Alert")
                            intent.putExtra("text", "Spent SGD 15.80 at Food Junction")
                        }
                        2 -> {
                            intent.putExtra("package", "com.grabtaxi.passenger")
                            intent.putExtra("title", "GrabPay")
                            intent.putExtra("text", "Payment of S$8.50 to GrabFood merchant successful")
                        }
                    }
                    requireContext().sendBroadcast(intent)
                    Toast.makeText(requireContext(), "🚀 Simulated test notification broadcast sent!", Toast.LENGTH_SHORT).show()
                }
                .show()
            true
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        try {
            requireContext().registerReceiver(
                billReceiver,
                android.content.IntentFilter("com.billrecorder.NEW_BILL")
            )
        } catch (_: Exception) {}
        refresh()
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(billReceiver) } catch (_: Exception) {}
    }

    private fun refreshAccountFilterUI() {
        val view = view ?: return
        val llAccountFilter = view.findViewById<LinearLayout>(R.id.llAccountFilter)
        llAccountFilter.removeAllViews()

        val accounts = DataManager.getAccounts()

        // Helper to add a pill
        fun addPill(id: String?, label: String) {
            val tv = TextView(requireContext()).apply {
                text = label
                textSize = 14f
                setPadding(32, 12, 32, 12)
                
                val isSelected = (id == selectedFilterAccountId)
                
                setTextColor(Color.parseColor(if (isSelected) "#1E1E1E" else "#EAEAEA"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 40f
                    setColor(Color.parseColor(if (isSelected) "#F6AD55" else "#333333"))
                    setStroke(2, Color.parseColor(if (isSelected) "#F6AD55" else "#555555"))
                }

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 16 }

                setOnClickListener {
                    selectedFilterAccountId = id
                    refresh()
                }
            }
            llAccountFilter.addView(tv)
        }

        addPill(null, "All")
        accounts.forEach { addPill(it.id, it.name) }
    }

    private fun currentMonthYear(): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)

    private fun refresh() {
        val sdfDisplay = SimpleDateFormat("MMM, yyyy", Locale.getDefault())
        tvMonth.text = sdfDisplay.format(currentCalendar.time)

        val monthYear = currentMonthYear()
        val allTxns = DataManager.getTransactions()
            .filter { it.isConfirmed && it.date.startsWith(monthYear) }
            .filter { selectedFilterAccountId == null || it.accountId == selectedFilterAccountId }
            .sortedByDescending { it.date }

        val expense = allTxns.filter { !it.isIncome }.sumOf { it.amount }
        val income = allTxns.filter { it.isIncome }.sumOf { it.amount }
        val total = income - expense

        tvTotalExpense.text = "S$%.2f".format(expense)
        tvTotalIncome.text = "S$%.2f".format(income)
        tvTotal.text = "S$%.2f".format(total)
        tvTotal.setTextColor(Color.parseColor(if (total >= 0) "#77C388" else "#E26C59"))

        adapter.updateData(allTxns)
        refreshAccountFilterUI()

        // ── Unconfirmed Dropdown Dynamic Binding ─────────────────────────────
        val view = view ?: return
        val llUnconfirmedHeader = view.findViewById<LinearLayout>(R.id.llUnconfirmedHeader)
        val tvUnconfirmedTitle = view.findViewById<TextView>(R.id.tvUnconfirmedTitle)
        val tvUnconfirmedArrow = view.findViewById<TextView>(R.id.tvUnconfirmedArrow)
        val llUnconfirmedContainer = view.findViewById<LinearLayout>(R.id.llUnconfirmedContainer)
        val llUnconfirmedList = view.findViewById<LinearLayout>(R.id.llUnconfirmedList)

        val unconfirmedTxns = DataManager.getTransactions().filter { !it.isConfirmed }
        if (unconfirmedTxns.isEmpty()) {
            llUnconfirmedHeader.visibility = View.GONE
            llUnconfirmedContainer.visibility = View.GONE
        } else {
            llUnconfirmedHeader.visibility = View.VISIBLE
            tvUnconfirmedTitle.text = "⚠️ ${unconfirmedTxns.size} Unconfirmed Transactions"
            tvUnconfirmedArrow.text = if (isUnconfirmedExpanded) "▲" else "▼"
            llUnconfirmedContainer.visibility = if (isUnconfirmedExpanded) View.VISIBLE else View.GONE

            llUnconfirmedHeader.setOnClickListener {
                isUnconfirmedExpanded = !isUnconfirmedExpanded
                tvUnconfirmedArrow.text = if (isUnconfirmedExpanded) "▲" else "▼"
                llUnconfirmedContainer.visibility = if (isUnconfirmedExpanded) View.VISIBLE else View.GONE
            }

            llUnconfirmedList.removeAllViews()
            unconfirmedTxns.forEach { txn ->
                val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_transaction, null)
                val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
                val tvBank = row.findViewById<TextView>(R.id.tvBank)
                val tvAmount = row.findViewById<TextView>(R.id.tvAmount)
                val cvIconBg = row.findViewById<CardView>(R.id.cvIconBg)
                val ivIcon = row.findViewById<android.widget.ImageView>(R.id.ivIcon)
                val tvEmojiFallback = row.findViewById<TextView>(R.id.tvEmojiFallback)
                val ivDefaultIcon = row.findViewById<android.widget.ImageView>(R.id.ivDefaultIcon)

                tvTitle.text = txn.title
                val acc = DataManager.getAccountById(txn.accountId)
                tvBank.text = acc?.name?.lowercase() ?: txn.accountId.ifEmpty { "bank" }

                val sign = if (txn.isIncome) "+" else "-"
                val amtColor = if (txn.isIncome) "#77C388" else "#E26C59"
                tvAmount.setTextColor(Color.parseColor(amtColor))
                tvAmount.text = "${sign}S\$${"%.2f".format(txn.amount)}"

                val cat = DataManager.getCategoryById(txn.categoryId)
                val colorHex = cat?.colorHex ?: (if (txn.isIncome) "#77C388" else "#E26C59")

                if (cat != null) {
                    val resId = CategoryIcons.getDrawableResId(requireContext(), cat.iconName, cat.name)
                    if (resId != 0) {
                        ivIcon.visibility = View.VISIBLE
                        ivIcon.setImageResource(resId)
                        tvEmojiFallback.visibility = View.GONE
                        ivDefaultIcon.visibility = View.GONE
                        cvIconBg.setCardBackgroundColor(Color.TRANSPARENT)
                    } else {
                        ivIcon.visibility = View.GONE
                        tvEmojiFallback.visibility = View.VISIBLE
                        tvEmojiFallback.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
                        ivDefaultIcon.visibility = View.GONE
                        cvIconBg.setCardBackgroundColor(Color.parseColor(colorHex))
                    }
                } else {
                    ivIcon.visibility = View.GONE
                    tvEmojiFallback.visibility = View.VISIBLE
                    tvEmojiFallback.text = "💸"
                    ivDefaultIcon.visibility = View.GONE
                    cvIconBg.setCardBackgroundColor(Color.parseColor(colorHex))
                }

                row.setOnClickListener { showEditDeleteDialog(txn) }
                llUnconfirmedList.addView(row)
            }
        }
    }

    private fun showAddTransactionDialog(existing: Transaction? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_transaction, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.attributes?.windowAnimations = R.style.DialogSlideUp

        // State variables
        var selectedType = when {
            existing?.categoryId?.contains("transfer", ignoreCase = true) == true -> "transfer"
            existing?.isIncome == true -> "income"
            else -> "expense"
        }
        var selectedAccount = DataManager.getAccounts().find { it.id == existing?.accountId }
            ?: DataManager.getAccounts().firstOrNull()
        var selectedCategory = DataManager.getCategories().find { it.id == existing?.categoryId }
            ?: DataManager.getCategories().firstOrNull { if (selectedType == "income") it.type == "income" else it.type == "expense" }

        // Calculator states
        fun formatDouble(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                "%.2f".format(Locale.US, value)
            }
        }

        var calcDisplayStr = if (existing != null) formatDouble(existing.amount) else "0.00"
        var operand1: Double? = null
        var pendingOp: String? = null
        // When editing an existing transaction, first digit should REPLACE the amount, not append
        var clearOnNextDigit = existing != null
        val selectedCalendar = Calendar.getInstance()
        existing?.let {
            try {
                selectedCalendar.time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(it.date) ?: Date()
            } catch (_: Exception) {}
        }

        // View references
        val tvPillAccName = dialogView.findViewById<TextView>(R.id.tvPillAccName)
        val tvPillAccLogo = dialogView.findViewById<TextView>(R.id.tvPillAccLogo)
        val tvPillCatName = dialogView.findViewById<TextView>(R.id.tvPillCatName)
        val cvPillCatIconBg = dialogView.findViewById<CardView>(R.id.cvPillCatIconBg)
        val ivPillCatIcon = dialogView.findViewById<ImageView>(R.id.ivPillCatIcon)
        val tvPillCatEmoji = dialogView.findViewById<TextView>(R.id.tvPillCatEmoji)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val tvCalcAmount = dialogView.findViewById<TextView>(R.id.tvCalcAmount)
        val btnCalcBackspace = dialogView.findViewById<ImageView>(R.id.btnCalcBackspace)

        val btnTypeIncome = dialogView.findViewById<LinearLayout>(R.id.btnTypeIncome)
        val btnTypeExpense = dialogView.findViewById<LinearLayout>(R.id.btnTypeExpense)
        val btnTypeTransfer = dialogView.findViewById<LinearLayout>(R.id.btnTypeTransfer)
              val llPillAccount = dialogView.findViewById<LinearLayout>(R.id.llPillAccount)
        val llPillCategory = dialogView.findViewById<LinearLayout>(R.id.llPillCategory)

        val btnPickerAddCategory = dialogView.findViewById<Button>(R.id.btnPickerAddCategory)
        val btnPickerAddAccount = dialogView.findViewById<Button>(R.id.btnPickerAddAccount)

        // ── Visual Bindings Helper ───────────────────────────────────────────
        fun updateAccountPillVisuals() {
            if (selectedAccount != null) {
                tvPillAccName.text = selectedAccount!!.name
                tvPillAccLogo.visibility = View.VISIBLE
            } else {
                tvPillAccName.text = "Select Account"
                tvPillAccLogo.visibility = View.GONE
            }
        }

        fun updateCategoryPillVisuals() {
            if (selectedCategory != null) {
                tvPillCatName.text = selectedCategory!!.name
                val resId = CategoryIcons.getDrawableResId(requireContext(), selectedCategory!!.iconName, selectedCategory!!.name)
                if (resId != 0) {
                    ivPillCatIcon.visibility = View.VISIBLE
                    ivPillCatIcon.setImageResource(resId)
                    tvPillCatEmoji.visibility = View.GONE
                } else {
                    ivPillCatIcon.visibility = View.GONE
                    tvPillCatEmoji.visibility = View.VISIBLE
                    tvPillCatEmoji.text = CategoryIcons.getEmoji(selectedCategory!!.iconName, selectedCategory!!.name)
                }
            } else {
                tvPillCatName.text = "Category"
                ivPillCatIcon.visibility = View.GONE
                tvPillCatEmoji.visibility = View.VISIBLE
                tvPillCatEmoji.text = "🏷️"
            }
        }

        fun showPanel(panelName: String) {
            dialogView.findViewById<View>(R.id.llPanelCalculator).visibility = if (panelName == "calculator") View.VISIBLE else View.INVISIBLE
            dialogView.findViewById<View>(R.id.llPanelCategoryPicker).visibility = if (panelName == "category") View.VISIBLE else View.INVISIBLE
            dialogView.findViewById<View>(R.id.llPanelAccountPicker).visibility = if (panelName == "account") View.VISIBLE else View.INVISIBLE

            if (panelName == "category") {
                val rvCategoryPicker = dialogView.findViewById<RecyclerView>(R.id.rvCategoryPicker)
                rvCategoryPicker.post {
                    rvCategoryPicker.adapter?.notifyDataSetChanged()
                }
            } else if (panelName == "account") {
                val rvAccountPicker = dialogView.findViewById<RecyclerView>(R.id.rvAccountPicker)
                rvAccountPicker.post {
                    rvAccountPicker.adapter?.notifyDataSetChanged()
                }
            }
        }

        fun refreshCategoryPicker() {
            val activeCategories = DataManager.getCategories().filter {
                if (selectedType == "income") it.type.equals("income", true)
                else !it.type.equals("income", true)
            }
            
            // Re-bind selected category if it became incompatible with selected type
            val isCurrentCatValid = activeCategories.any { it.id == selectedCategory?.id }
            if (!isCurrentCatValid) {
                selectedCategory = activeCategories.firstOrNull()
                updateCategoryPillVisuals()
            }

            val rvCategoryPicker = dialogView.findViewById<RecyclerView>(R.id.rvCategoryPicker)
            rvCategoryPicker.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
            rvCategoryPicker.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                inner class VH(val v: View) : RecyclerView.ViewHolder(v) {
                    val ivPickerIcon: ImageView = v.findViewById(R.id.ivPickerIcon)
                    val tvPickerEmoji: TextView = v.findViewById(R.id.tvPickerEmoji)
                    val tvPickerName: TextView = v.findViewById(R.id.tvPickerName)
                    val cvPickerIcon: CardView = v.findViewById(R.id.cvPickerIcon)
                }

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val v = LayoutInflater.from(requireContext()).inflate(R.layout.item_category_picker, parent, false)
                    return VH(v)
                }

                override fun getItemCount(): Int = activeCategories.size

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val vh = holder as VH
                    val cat = activeCategories[position]
                    vh.tvPickerName.text = cat.name
                    
                    val resId = CategoryIcons.getDrawableResId(requireContext(), cat.iconName, cat.name)
                    if (resId != 0) {
                        vh.ivPickerIcon.visibility = View.VISIBLE
                        vh.ivPickerIcon.setImageResource(resId)
                        vh.tvPickerEmoji.visibility = View.GONE
                        vh.cvPickerIcon.setCardBackgroundColor(Color.TRANSPARENT)
                    } else {
                        vh.ivPickerIcon.visibility = View.GONE
                        vh.tvPickerEmoji.visibility = View.VISIBLE
                        vh.tvPickerEmoji.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
                        vh.cvPickerIcon.setCardBackgroundColor(Color.parseColor(cat.colorHex))
                    }
                    
                    vh.v.setOnClickListener {
                        selectedCategory = cat
                        updateCategoryPillVisuals()
                        showPanel("calculator")
                    }
                }
            }
        }

        fun refreshAccountPicker() {
            val activeAccounts = DataManager.getAccounts()
            val rvAccountPicker = dialogView.findViewById<RecyclerView>(R.id.rvAccountPicker)
            rvAccountPicker.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            rvAccountPicker.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                inner class VH(val v: View) : RecyclerView.ViewHolder(v) {
                    val tvAccName: TextView = v.findViewById(R.id.tvAccName)
                    val tvAccBalance: TextView = v.findViewById(R.id.tvAccBalance)
                }

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val v = LayoutInflater.from(requireContext()).inflate(R.layout.item_account_picker, parent, false)
                    return VH(v)
                }

                override fun getItemCount(): Int = activeAccounts.size

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val vh = holder as VH
                    val acc = activeAccounts[position]
                    vh.tvAccName.text = acc.name
                    vh.tvAccBalance.text = "S$%.2f".format(acc.balance)
                    
                    vh.v.setOnClickListener {
                        selectedAccount = acc
                        updateAccountPillVisuals()
                        showPanel("calculator")
                    }
                }
            }
        }

        fun updateTypeToggleVisuals() {
            val checkIncome = dialogView.findViewById<TextView>(R.id.tvIncomeCheck)
            val textIncome = dialogView.findViewById<TextView>(R.id.tvIncomeText)
            val checkExpense = dialogView.findViewById<TextView>(R.id.tvExpenseCheck)
            val textExpense = dialogView.findViewById<TextView>(R.id.tvExpenseText)
            val checkTransfer = dialogView.findViewById<TextView>(R.id.tvTransferCheck)
            val textTransfer = dialogView.findViewById<TextView>(R.id.tvTransferText)

            checkIncome.visibility = if (selectedType == "income") View.VISIBLE else View.GONE
            textIncome.setTextColor(Color.parseColor(if (selectedType == "income") "#FFF9DE" else "#8E8C82"))

            checkExpense.visibility = if (selectedType == "expense") View.VISIBLE else View.GONE
            textExpense.setTextColor(Color.parseColor(if (selectedType == "expense") "#FFF9DE" else "#8E8C82"))

            checkTransfer.visibility = if (selectedType == "transfer") View.VISIBLE else View.GONE
            textTransfer.setTextColor(Color.parseColor(if (selectedType == "transfer") "#FFF9DE" else "#8E8C82"))
            
            refreshCategoryPicker()
        }

        fun updateDateTimeFooter() {
            val tvCalcDate = dialogView.findViewById<TextView>(R.id.tvCalcDate)
            val tvCalcTime = dialogView.findViewById<TextView>(R.id.tvCalcTime)
            val sdfDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
            tvCalcDate.text = sdfDate.format(selectedCalendar.time)
            tvCalcTime.text = sdfTime.format(selectedCalendar.time)
        }

        // ── Calculator Core Logic ───────────────────────────────────────────
        fun updateAmountDisplay() {
            tvCalcAmount.text = calcDisplayStr
        }

        fun onDigitClick(digit: String) {
            if (calcDisplayStr == "0" || calcDisplayStr == "0.00" || clearOnNextDigit) {
                calcDisplayStr = digit
                clearOnNextDigit = false
            } else {
                val dotIdx = calcDisplayStr.indexOf('.')
                if (dotIdx == -1 || calcDisplayStr.length - dotIdx <= 2) {
                    calcDisplayStr += digit
                }
            }
            updateAmountDisplay()
        }

        fun onDotClick() {
            if (clearOnNextDigit) {
                calcDisplayStr = "0."
                clearOnNextDigit = false
            } else if (!calcDisplayStr.contains('.')) {
                calcDisplayStr += "."
            }
            updateAmountDisplay()
        }

        fun onBackspaceClick() {
            if (calcDisplayStr.length > 1) {
                calcDisplayStr = calcDisplayStr.dropLast(1)
                if (calcDisplayStr == "-") calcDisplayStr = "0"
            } else {
                calcDisplayStr = "0"
            }
            updateAmountDisplay()
        }

        fun performCalculation(op1: Double, op2: Double, op: String): Double {
            return when (op) {
                "+" -> op1 + op2
                "-" -> op1 - op2
                "×", "*" -> op1 * op2
                "÷", "/" -> if (op2 != 0.0) op1 / op2 else 0.0
                else -> op2
            }
        }

        fun onOperatorClick(op: String) {
            val currentVal = calcDisplayStr.toDoubleOrNull() ?: 0.0
            if (operand1 != null && pendingOp != null && !clearOnNextDigit) {
                val result = performCalculation(operand1!!, currentVal, pendingOp!!)
                calcDisplayStr = formatDouble(result)
                updateAmountDisplay()
                operand1 = result
            } else {
                operand1 = currentVal
            }
            pendingOp = op
            clearOnNextDigit = true
        }

        fun evaluatePendingOperationIfNeeded() {
            val currentVal = calcDisplayStr.toDoubleOrNull() ?: 0.0
            if (operand1 != null && pendingOp != null) {
                val result = performCalculation(operand1!!, currentVal, pendingOp!!)
                calcDisplayStr = formatDouble(result)
                updateAmountDisplay()
                operand1 = null
                pendingOp = null
                clearOnNextDigit = true
            }
        }

        fun onEqualsClick() {
            evaluatePendingOperationIfNeeded()
        }

        // ── Click Listeners & Bindings ───────────────────────────────────────
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        btnCancel.setOnClickListener { dialog.dismiss() }
        
        btnTypeIncome.setOnClickListener {
            selectedType = "income"
            updateTypeToggleVisuals()
        }
        btnTypeExpense.setOnClickListener {
            selectedType = "expense"
            updateTypeToggleVisuals()
        }
        btnTypeTransfer.setOnClickListener {
            selectedType = "transfer"
            updateTypeToggleVisuals()
        }

        llPillAccount.setOnClickListener {
            val pickerPanel = dialogView.findViewById<View>(R.id.llPanelAccountPicker)
            if (pickerPanel.visibility == View.VISIBLE) showPanel("calculator") else showPanel("account")
        }

        llPillCategory.setOnClickListener {
            val pickerPanel = dialogView.findViewById<View>(R.id.llPanelCategoryPicker)
            if (pickerPanel.visibility == View.VISIBLE) showPanel("calculator") else showPanel("category")
        }

        // Digits
        val numButtons = listOf(
            R.id.btnCalc0 to "0", R.id.btnCalc1 to "1", R.id.btnCalc2 to "2",
            R.id.btnCalc3 to "3", R.id.btnCalc4 to "4", R.id.btnCalc5 to "5",
            R.id.btnCalc6 to "6", R.id.btnCalc7 to "7", R.id.btnCalc8 to "8",
            R.id.btnCalc9 to "9"
        )
        numButtons.forEach { (resId, digit) ->
            dialogView.findViewById<Button>(resId).setOnClickListener { onDigitClick(digit) }
        }

        // Operators & Special keys
        dialogView.findViewById<Button>(R.id.btnCalcDot).setOnClickListener { onDotClick() }
        btnCalcBackspace.setOnClickListener { onBackspaceClick() }
        dialogView.findViewById<Button>(R.id.btnCalcPlus).setOnClickListener { onOperatorClick("+") }
        dialogView.findViewById<Button>(R.id.btnCalcMinus).setOnClickListener { onOperatorClick("-") }
        dialogView.findViewById<Button>(R.id.btnCalcMultiply).setOnClickListener { onOperatorClick("×") }
        dialogView.findViewById<Button>(R.id.btnCalcDivide).setOnClickListener { onOperatorClick("÷") }
        dialogView.findViewById<Button>(R.id.btnCalcEquals).setOnClickListener { onEqualsClick() }

        // Date and Time picker
        val btnCalcDateTime = dialogView.findViewById<View>(R.id.btnCalcDateTime)
        btnCalcDateTime.setOnClickListener {
            val dateSetListener = android.app.DatePickerDialog.OnDateSetListener { _, year, month, day ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day)
                
                val timeSetListener = android.app.TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                    selectedCalendar.set(Calendar.MINUTE, minute)
                    updateDateTimeFooter()
                }
                android.app.TimePickerDialog(
                    requireContext(),
                    timeSetListener,
                    selectedCalendar.get(Calendar.HOUR_OF_DAY),
                    selectedCalendar.get(Calendar.MINUTE),
                    false
                ).show()
            }
            android.app.DatePickerDialog(
                requireContext(),
                dateSetListener,
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Add Category/Account from sheet buttons
        btnPickerAddCategory.setOnClickListener {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 24)
            }
            val etNewCatName = EditText(requireContext()).apply { hint = "Category name" }
            layout.addView(etNewCatName)

            AlertDialog.Builder(requireContext())
                .setTitle("Add New Category")
                .setView(layout)
                .setPositiveButton("Add") { _, _ ->
                    val name = etNewCatName.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val isInc = selectedType == "income"
                        val typeStr = if (isInc) "income" else "expense"
                        val newCat = Category(
                            id = "${name.lowercase(Locale.getDefault()).replace(" ", "_")}_${typeStr.take(3)}",
                            name = name,
                            type = typeStr,
                            colorHex = if (isInc) "#38A169" else "#805AD5",
                            iconName = "others"
                        )
                        DataManager.addCategory(newCat)
                        selectedCategory = newCat
                        updateCategoryPillVisuals()
                        refreshCategoryPicker()
                        showPanel("calculator")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnPickerAddAccount.setOnClickListener {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 24)
            }
            val etNewAccName = EditText(requireContext()).apply { hint = "Account name (e.g. OCBC)" }
            val etNewAccBalance = EditText(requireContext()).apply {
                hint = "Starting balance"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            layout.addView(etNewAccName)
            layout.addView(etNewAccBalance)

            AlertDialog.Builder(requireContext())
                .setTitle("Add New Account")
                .setView(layout)
                .setPositiveButton("Add") { _, _ ->
                    val name = etNewAccName.text.toString().trim()
                    val balance = etNewAccBalance.text.toString().toDoubleOrNull() ?: 0.0
                    if (name.isNotEmpty()) {
                        val newAcc = Account(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            bankName = name,
                            balance = balance
                        )
                        DataManager.addAccount(newAcc)
                        selectedAccount = newAcc
                        updateAccountPillVisuals()
                        refreshAccountPicker()
                        showPanel("calculator")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Pre-fill existing data for edit
        existing?.let {
            etNote.setText(it.note)
        }

        // Initialize state view triggers
        updateAccountPillVisuals()
        updateCategoryPillVisuals()
        updateTypeToggleVisuals()
        updateDateTimeFooter()
        refreshCategoryPicker()
        refreshAccountPicker()
        updateAmountDisplay()
        showPanel("calculator")

        // ── Save Trigger ─────────────────────────────────────────────────────
        dialogView.findViewById<View>(R.id.btnSave).setOnClickListener {
            evaluatePendingOperationIfNeeded()
            val amount = calcDisplayStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0.0) {
                Toast.makeText(requireContext(), "Please enter an amount greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedAccount == null) {
                Toast.makeText(requireContext(), "Please select an account", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedCategory == null) {
                Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val note = etNote.text.toString()
            val isIncome = selectedType == "income"
            val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(selectedCalendar.time)

            if (existing == null) {
                val txn = Transaction(
                    id = java.util.UUID.randomUUID().toString(),
                    title = selectedCategory!!.name,
                    date = nowFormatted,
                    amount = amount,
                    isIncome = isIncome,
                    categoryId = selectedCategory!!.id,
                    accountId = selectedAccount!!.id,
                    note = note,
                    rawText = "",
                    isConfirmed = true
                )
                DataManager.addTransaction(txn)
                DataManager.updateAccountBalance(selectedAccount!!.id, if (isIncome) amount else -amount)
            } else {
                val oldAmount = existing.amount
                val oldIsIncome = existing.isIncome
                val oldAccId = existing.accountId
                
                val updated = existing.copy(
                    amount = amount,
                    isIncome = isIncome,
                    categoryId = selectedCategory!!.id,
                    accountId = selectedAccount!!.id,
                    note = note,
                    title = selectedCategory!!.name,
                    date = nowFormatted
                )
                DataManager.updateTransaction(updated)
                
                if (existing.isConfirmed) {
                    // Refund the old values
                    DataManager.updateAccountBalance(oldAccId, if (oldIsIncome) -oldAmount else oldAmount)
                    // Charge the new values
                    DataManager.updateAccountBalance(selectedAccount!!.id, if (isIncome) amount else -amount)
                }
            }
            refresh()
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(android.view.Gravity.BOTTOM)
            decorView.setPadding(0, 0, 0, 0)
        }
    }

    private fun showEditDeleteDialog(txn: Transaction) {
        var currentTxn = txn
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_detail, null)
        
        val llHeader = dialogView.findViewById<LinearLayout>(R.id.llHeader)
        val tvClose = dialogView.findViewById<TextView>(R.id.tvClose)
        val ivDelete = dialogView.findViewById<ImageView>(R.id.ivDelete)
        val ivEdit = dialogView.findViewById<ImageView>(R.id.ivEdit)
        val tvType = dialogView.findViewById<TextView>(R.id.tvType)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvAmount)
        val tvDateTime = dialogView.findViewById<TextView>(R.id.tvDateTime)
        
        val tvAccName = dialogView.findViewById<TextView>(R.id.tvAccName)
        val tvCatName = dialogView.findViewById<TextView>(R.id.tvCatName)
        val ivCatIcon = dialogView.findViewById<ImageView>(R.id.ivCatIcon)
        val tvCatEmojiFallback = dialogView.findViewById<TextView>(R.id.tvCatEmojiFallback)
        val cvCatIcon = dialogView.findViewById<View>(R.id.cvCatIcon)
        val tvNotes = dialogView.findViewById<TextView>(R.id.tvNotes)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirm)
        

        // Helper to bind all views to the dialog layout
        fun bindViews() {
            // 1. Transaction Type and Color Styling
            if (currentTxn.isIncome) {
                tvType.text = "INCOME"
                llHeader.setBackgroundColor(Color.parseColor("#77C388")) // Soft Green
                tvAmount.text = "+S$%.2f".format(currentTxn.amount)
            } else {
                tvType.text = "EXPENSE"
                llHeader.setBackgroundColor(Color.parseColor("#E26C59")) // Soft Coral
                tvAmount.text = "-S$%.2f".format(currentTxn.amount)
            }
            
            // 2. Date & Time
            tvDateTime.text = currentTxn.date
            
            // 3. Account Name
            val acc = DataManager.getAccountById(currentTxn.accountId)
            tvAccName.text = acc?.name ?: "No account"
            
            // 4. Category Name & Icon
            val cat = DataManager.getCategoryById(currentTxn.categoryId)
            tvCatName.text = cat?.name ?: currentTxn.title
            
            if (cat != null) {
                val resId = CategoryIcons.getDrawableResId(requireContext(), cat.iconName, cat.name)
                if (resId != 0) {
                    ivCatIcon.visibility = View.VISIBLE
                    ivCatIcon.setImageResource(resId)
                    tvCatEmojiFallback.visibility = View.GONE
                    if (cvCatIcon is CardView) {
                        cvCatIcon.setCardBackgroundColor(Color.TRANSPARENT)
                    }
                } else {
                    ivCatIcon.visibility = View.GONE
                    tvCatEmojiFallback.visibility = View.VISIBLE
                    tvCatEmojiFallback.text = CategoryIcons.getEmoji(cat.iconName, cat.name)
                    if (cvCatIcon is CardView) {
                        cvCatIcon.setCardBackgroundColor(Color.parseColor(cat.colorHex))
                    }
                }
            } else {
                ivCatIcon.visibility = View.GONE
                tvCatEmojiFallback.visibility = View.VISIBLE
                tvCatEmojiFallback.text = "💸"
                if (cvCatIcon is CardView) {
                    cvCatIcon.setCardBackgroundColor(Color.GRAY)
                }
            }
            
            // 5. Notes
            tvNotes.text = if (currentTxn.note.isNullOrEmpty()) "No notes" else currentTxn.note

            // 6. Confirm button visibility
            if (!currentTxn.isConfirmed) {
                btnConfirm.visibility = View.VISIBLE
            } else {
                btnConfirm.visibility = View.GONE
            }
        }
        
        bindViews()
        
        // Create and show custom dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.attributes?.windowAnimations = R.style.DialogSlideUp
        
        // Click Listeners
        tvClose.setOnClickListener { dialog.dismiss() }
        
        // Make edit pen visible and hook up to edit dialog
        ivEdit.visibility = View.VISIBLE
        ivEdit.setOnClickListener {
            dialog.dismiss()
            showAddTransactionDialog(currentTxn)
        }
        
        // Tap to Confirm & Save directly from the popup list/overlay
        btnConfirm.setOnClickListener {
            // Confirm the transaction!
            currentTxn = currentTxn.copy(isConfirmed = true)
            DataManager.updateTransaction(currentTxn)
            
            // Apply balance now!
            val acc = DataManager.getAccountById(currentTxn.accountId)
            acc?.let { a ->
                DataManager.updateAccountBalance(a.id, if (currentTxn.isIncome) currentTxn.amount else -currentTxn.amount)
            }
            
            refresh()
            dialog.dismiss()
        }

        // Tap to Change Account Pill
        val btnAccountPill = tvAccName.parent as View
        btnAccountPill.setOnClickListener {
            val accounts = DataManager.getAccounts()
            val names = accounts.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Account")
                .setItems(names) { _, index ->
                    val selectedAcc = accounts[index]
                    val oldAccId = currentTxn.accountId
                    if (oldAccId != selectedAcc.id) {
                        if (currentTxn.isConfirmed) {
                            // Adjust balances only for confirmed transactions to keep ledger accurate!
                            DataManager.updateAccountBalance(oldAccId, if (currentTxn.isIncome) -currentTxn.amount else currentTxn.amount)
                            DataManager.updateAccountBalance(selectedAcc.id, if (currentTxn.isIncome) currentTxn.amount else -currentTxn.amount)
                        }
                        
                        currentTxn = currentTxn.copy(accountId = selectedAcc.id)
                        DataManager.updateTransaction(currentTxn)
                        bindViews()
                        refresh()
                    }
                }
                .show()
        }
        
        // Tap to Change Category Pill (Lists all categories & switches type automatically)
        val btnCategoryPill = tvCatName.parent as View
        btnCategoryPill.setOnClickListener {
            val categories = DataManager.getCategories()
            val names = categories.map { "${it.name} (${it.type.lowercase().replaceFirstChar { c -> c.uppercase() }})" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Category")
                .setItems(names) { _, index ->
                    val selectedCat = categories[index]
                    val oldIsIncome = currentTxn.isIncome
                    val newIsIncome = selectedCat.type.equals("income", true)
                    
                    if (currentTxn.isConfirmed && oldIsIncome != newIsIncome) {
                        val acc = DataManager.getAccountById(currentTxn.accountId)
                        acc?.let { a ->
                            // Refund old type direction
                            DataManager.updateAccountBalance(a.id, if (oldIsIncome) -currentTxn.amount else currentTxn.amount)
                            // Charge new type direction
                            DataManager.updateAccountBalance(a.id, if (newIsIncome) currentTxn.amount else -currentTxn.amount)
                        }
                    }
                    
                    currentTxn = currentTxn.copy(
                        isIncome = newIsIncome,
                        categoryId = selectedCat.id,
                        title = selectedCat.name
                    )
                    DataManager.updateTransaction(currentTxn)
                    bindViews()
                    refresh()
                }
                .show()
        }
        
        // Tap on type text to directly toggle Income / Expense
        tvType.setOnClickListener {
            val options = arrayOf("Expense", "Income")
            AlertDialog.Builder(requireContext())
                .setTitle("Change Transaction Type")
                .setItems(options) { _, index ->
                    val newIsIncome = index == 1
                    val oldIsIncome = currentTxn.isIncome
                    if (oldIsIncome != newIsIncome) {
                        if (currentTxn.isConfirmed) {
                            val acc = DataManager.getAccountById(currentTxn.accountId)
                            acc?.let { a ->
                                DataManager.updateAccountBalance(a.id, if (oldIsIncome) -currentTxn.amount else currentTxn.amount)
                                DataManager.updateAccountBalance(a.id, if (newIsIncome) currentTxn.amount else -currentTxn.amount)
                            }
                        }
                        
                        // Select default category for the new type
                        val defaultCatId = if (newIsIncome) "others_inc" else "others_exp"
                        val defaultCat = DataManager.getCategoryById(defaultCatId)
                        
                        currentTxn = currentTxn.copy(
                            isIncome = newIsIncome,
                            categoryId = defaultCatId,
                            title = defaultCat?.name ?: "Others"
                        )
                        DataManager.updateTransaction(currentTxn)
                        bindViews()
                        refresh()
                    }
                }
                .show()
        }
        
        // Tap to Change Note Directly
        tvNotes.setOnClickListener {
            val et = EditText(requireContext()).apply {
                hint = "Enter note..."
                setText(if (currentTxn.note == "No notes") "" else currentTxn.note)
                setPadding(32, 32, 32, 32)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Edit Note")
                .setView(et)
                .setPositiveButton("Save") { _, _ ->
                    val newNote = et.text.toString().trim()
                    currentTxn = currentTxn.copy(note = newNote)
                    DataManager.updateTransaction(currentTxn)
                    bindViews()
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        ivDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete") { _, _ ->
                    DataManager.deleteTransaction(currentTxn.id)
                    if (currentTxn.isConfirmed) {
                        val acc = DataManager.getAccountById(currentTxn.accountId)
                        acc?.let { a ->
                            DataManager.updateAccountBalance(a.id, if (currentTxn.isIncome) -currentTxn.amount else currentTxn.amount)
                        }
                    }
                    refresh()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        dialog.show()
    }
}
