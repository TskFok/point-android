package com.pointquest.android.core.model

import java.time.Instant

data class PracticeSummary(
    val activeTotal: Int,
    val balance: Int,
    val firstAnsweredCount: Int,
    val masteredWrongCount: Int,
    val pendingWrongCount: Int,
    val unansweredCount: Int,
)

data class Question(
    val id: String,
    val stem: String,
    val basePoints: Int,
    val options: List<QuestionOption>,
)

data class QuestionOption(
    val id: String,
    val label: String,
    val content: String,
    val position: Int,
)

data class AnswerResult(
    val balance: Int,
    val correct: Boolean,
    val correctOptionId: String,
    val errorCount: Int,
    val explanation: String,
    val pointsAwarded: Int,
    val selectedOptionId: String,
)

data class WrongQuestion(
    val errorCount: Int,
    val firstAnsweredAt: Instant,
    val masteredAt: Instant?,
    val question: Question,
)
