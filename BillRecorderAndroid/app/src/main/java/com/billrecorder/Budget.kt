package com.billrecorder

data class Budget(
    val id: String,
    val categoryId: String,
    val monthYear: String, // e.g., "2026-05"
    val limit: Double
)
