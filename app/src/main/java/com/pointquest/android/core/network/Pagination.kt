package com.pointquest.android.core.network

import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta

sealed interface PageAdjustment {
    data class Reload(val lastValidPage: Int) : PageAdjustment
}

/**
 * A server-backed page collection. Page one replaces local items; later pages append only unseen
 * keys in encounter order. The response's [Page.meta] always replaces the previous metadata.
 */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val meta: PageMeta = EMPTY_META,
) {
    val canLoadMore: Boolean
        get() = meta.totalPages > 0 && meta.page < meta.totalPages

    fun <K> merge(page: Page<T>, keySelector: (T) -> K): PagedState<T> {
        if (page.meta.totalPages == 0) return PagedState(emptyList(), page.meta)

        val candidates = if (page.meta.page <= FIRST_PAGE) page.items else items + page.items
        val seen = HashSet<K>()
        val deduplicated = candidates.filter { item -> seen.add(keySelector(item)) }
        return PagedState(deduplicated, page.meta)
    }

    /** Returns the nearest page accepted by the server metadata, or null for a valid request. */
    fun adjustmentFor(requestedPage: Int): PageAdjustment? = when {
        requestedPage < FIRST_PAGE -> PageAdjustment.Reload(FIRST_PAGE)
        requestedPage > meta.totalPages.coerceAtLeast(FIRST_PAGE) ->
            PageAdjustment.Reload(meta.totalPages.coerceAtLeast(FIRST_PAGE))
        else -> null
    }

    private companion object {
        const val FIRST_PAGE = 1
        val EMPTY_META = PageMeta(page = FIRST_PAGE, pageSize = 0, total = 0, totalPages = 0)
    }
}
