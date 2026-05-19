package com.billrecorder

data class Transaction(
    val id: String,
    val title: String,
    val date: String,           // "yyyy-MM-dd HH:mm"
    val amount: Double,
    val isIncome: Boolean,
    val categoryId: String,
    val accountId: String,
    val note: String,
    val rawText: String,
    val isConfirmed: Boolean = true
)
