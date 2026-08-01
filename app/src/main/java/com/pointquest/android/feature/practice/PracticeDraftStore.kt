package com.pointquest.android.feature.practice

import com.pointquest.android.core.model.WrongQuestion

/**
 * 仅保存当前进程中的完整错题草稿。读取使用单次消费语义：同一份草稿最多成功返回一次。
 */
class PracticeDraftStore : PracticeDraftSource {
    private val drafts = mutableMapOf<String, WrongQuestion>()
    private val lock = Any()

    fun put(draft: WrongQuestion) = synchronized(lock) {
        drafts[draft.question.id] = draft
    }

    override fun consume(questionId: String): WrongQuestion? = synchronized(lock) {
        drafts.remove(questionId)
    }
}
