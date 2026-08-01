package com.pointquest.android.core.network

import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationTest {
    @Test
    fun mergeUsesServerMetaDeduplicatesAndFallsBackFromOutOfRangePage() {
        val state = PagedState(items = listOf(item("1")), meta = meta(page = 1, totalPages = 2))

        val merged = state.merge(
            page(items = listOf(item("1"), item("2")), meta = meta(page = 2, totalPages = 2)),
        ) { it.id }

        assertEquals(listOf("1", "2"), merged.items.map { it.id })
        assertFalse(merged.canLoadMore)
        assertEquals(2, merged.meta.page)
        assertEquals(PageAdjustment.Reload(2), merged.adjustmentFor(requestedPage = 3))
    }

    @Test
    fun firstPageReplacesExistingItemsAndUsesItsServerMetadata() {
        val state = PagedState(
            items = listOf(item("stale-1"), item("stale-2")),
            meta = meta(page = 2, totalPages = 3),
        )

        val merged = state.merge(
            page(items = listOf(item("fresh-1"), item("fresh-1")), meta = meta(page = 1, totalPages = 1)),
        ) { it.id }

        assertEquals(listOf("fresh-1"), merged.items.map { it.id })
        assertEquals(1, merged.meta.page)
        assertEquals(1, merged.meta.totalPages)
        assertFalse(merged.canLoadMore)
    }

    @Test
    fun emptyServerResultCreatesEmptyStateAndKeepsInitialPageValid() {
        val state = PagedState(items = listOf(item("old")), meta = meta(page = 1, totalPages = 1))

        val merged = state.merge(page(emptyList(), meta(page = 1, totalPages = 0))) { it.id }

        assertTrue(merged.items.isEmpty())
        assertEquals(0, merged.meta.totalPages)
        assertFalse(merged.canLoadMore)
        assertEquals(null, merged.adjustmentFor(requestedPage = 1))
        assertEquals(PageAdjustment.Reload(1), merged.adjustmentFor(requestedPage = 2))
    }

    @Test
    fun invalidLowPageReloadsFirstPageAndHighPageReloadsLastServerPage() {
        val state = PagedState(items = emptyList<Item>(), meta = meta(page = 2, totalPages = 3))

        assertEquals(PageAdjustment.Reload(1), state.adjustmentFor(requestedPage = 0))
        assertEquals(null, state.adjustmentFor(requestedPage = 1))
        assertEquals(null, state.adjustmentFor(requestedPage = 3))
        assertEquals(PageAdjustment.Reload(3), state.adjustmentFor(requestedPage = 4))
    }

    private data class Item(val id: String)

    private fun item(id: String) = Item(id)

    private fun page(items: List<Item>, meta: PageMeta) = Page(items, meta)

    private fun meta(page: Int, totalPages: Int) = PageMeta(
        page = page,
        pageSize = 20,
        total = if (totalPages == 0) 0 else totalPages * 20,
        totalPages = totalPages,
    )
}
