package com.example.mobiliyum

import java.util.Date

data class Review(
    val id: String = "",
    val productId: Int = 0,
    val userId: String = "",
    val userName: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    val date: Date = Date(),
    val isVerified: Boolean = true
)