package com.pointquest.android.feature.practice

import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiText

data class WrongQuestionsUiState(
    val paged: PagedState<WrongQuestion> = PagedState(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val loadMoreError: UiText? = null,
    val notice: UiText? = null,
) {
    val items: List<WrongQuestion>
        get() = paged.items

    val canLoadMore: Boolean
        get() = paged.canLoadMore && !loading && !loadingMore

    val empty: Boolean
        get() = !loading && error == null && items.isEmpty()
}
