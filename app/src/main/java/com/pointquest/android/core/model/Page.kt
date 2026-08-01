package com.pointquest.android.core.model

data class Page<T>(
    val items: List<T>,
    val meta: PageMeta,
)

data class PageMeta(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
)
