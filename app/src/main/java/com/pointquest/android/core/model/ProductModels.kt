package com.pointquest.android.core.model

import java.time.Instant

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val imageKey: String,
    val pointsCost: Int,
    val stock: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
