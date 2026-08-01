package com.pointquest.android.data.points

import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.network.AppResult

interface PointsRepository {
    suspend fun balance(): AppResult<Int>
    suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>>
}
