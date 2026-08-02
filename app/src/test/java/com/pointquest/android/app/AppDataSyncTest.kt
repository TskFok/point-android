package com.pointquest.android.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataSyncTest {
    @Test
    fun practiceAndOrderResultsPublishBalanceAndInvalidateOnlineTopLevelData() {
        val sync = AppDataSync()

        sync.recordPracticeChanged(balance = 47)
        assertEquals(47, sync.balance.value)
        assertEquals(1L, sync.homeRefreshRevision.value)

        sync.recordOrderCreated(balance = 32)
        assertEquals(32, sync.balance.value)
        assertEquals(2L, sync.homeRefreshRevision.value)
        assertEquals(1L, sync.shopRefreshRevision.value)
    }

    @Test
    fun inactiveProductIsPublishedBeforeShopRefreshRevision() {
        val sync = AppDataSync()

        sync.recordProductInactive("p1")

        assertTrue("p1" in sync.inactiveProductIds.value)
        assertEquals(1L, sync.shopRefreshRevision.value)
    }
}
