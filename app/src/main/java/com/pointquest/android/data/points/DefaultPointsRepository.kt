package com.pointquest.android.data.points

import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.StudentGateway

class DefaultPointsRepository(
    private val gateway: StudentGateway,
    private val authorized: AuthorizedCallExecutor,
    private val retry: RetryExecutor,
) : PointsRepository {
    override suspend fun balance(): AppResult<Int> = read { gateway.pointBalance() }

    override suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>> =
        read { gateway.pointLedger(page, PAGE_SIZE) }

    private suspend fun <T> read(operation: suspend () -> AppResult<T>): AppResult<T> =
        authorized.executeOperation { retry.executeRead { execute(operation) } }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
