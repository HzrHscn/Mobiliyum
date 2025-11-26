package com.example.mobiliyum

data class CategorySection(
    val categoryName: String,
    val products: List<Product>,
    var isExpanded: Boolean = false // Açık mı kapalı mı?
)
