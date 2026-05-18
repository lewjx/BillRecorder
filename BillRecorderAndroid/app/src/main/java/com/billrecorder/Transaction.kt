package com.billrecorder

data class Transaction(
    val id: String,
    val title: String,
    val date: String,
    val amount: Double,
    val isIncome: Boolean,
    val rawText: String
)
