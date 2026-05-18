package com.billrecorder

data class Category(
    val id: String,
    val name: String,
    val type: String, // "income" or "expense"
    val colorHex: String,
    val iconName: String  // used to map to an emoji or drawable name
)
