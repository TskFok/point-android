package com.pointquest.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.app.AppDependencies
import com.pointquest.android.app.AppNavHost
import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.RemoteHostPersistence
import com.pointquest.android.core.network.RemoteHostStore
import com.pointquest.android.core.network.RemoteHostValidator
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.data.auth.AuthRepository
import com.pointquest.android.data.orders.OrdersRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.data.preferences.LearnerLanguageStore
import com.pointquest.android.data.products.ProductImageUrlFactory
import com.pointquest.android.data.products.ProductsRepository
import com.pointquest.android.feature.practice.PracticeDraftStore
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
internal fun AppNavigationTestShell(
    session: FakeAppSession,
    navController: NavHostController = rememberNavController(),
    dependencies: AppDependencies = FakeAppDependencies(),
) {
    PointQuestTheme {
        AppNavHost(
            sessionStatus = session.status,
            navController = navController,
            container = dependencies,
        )
    }
}

internal class FakeAppSession(initialStatus: SessionStatus) {
    var status by mutableStateOf(initialStatus)
        private set

    fun signIn(user: User) {
        status = SessionStatus.SignedIn(user)
    }

    fun expire() {
        status = SessionStatus.SignedOut
    }
}

internal fun testStudent() = User(
    id = "student-device-test",
    username = "student",
    role = UserRole.STUDENT,
    pointsBalance = 42,
)

internal class FakeAppDependencies(
    private val onLogin: (User) -> Unit = {},
    private val noUnansweredQuestionsAfterFirstQuestion: Boolean = false,
) : AppDependencies {
    var registerCalls: Int = 0
        private set
    var loginCalls: Int = 0
        private set

    override val sessionState = SessionState().apply {
        publish(
            ActiveSession(
                user = testStudent(),
                accessToken = "test-access",
                accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
                generation = 1,
                loginSessionId = 1,
            ),
        )
    }
    override val appDataSync = AppDataSync(sessionState)
    override val practiceDraftStore = PracticeDraftStore()
    private val mutableLearnerLanguage = MutableStateFlow(LearnerLanguage.ALL)
    override val learnerLanguageStore: LearnerLanguageStore = object : LearnerLanguageStore {
        override val language: StateFlow<LearnerLanguage> = mutableLearnerLanguage.asStateFlow()

        override fun setLanguage(value: LearnerLanguage): Boolean {
            mutableLearnerLanguage.value = value
            return true
        }
    }
    private val remoteHostPersistence = object : RemoteHostPersistence {
        private var host: String? = null

        override fun read(): String? = host

        override fun write(value: String) {
            host = value
        }
    }
    override val remoteHostStore = RemoteHostStore(
        defaultHost = "https://images.example.invalid/",
        persistence = remoteHostPersistence,
        validator = RemoteHostValidator(allowHttp = true),
    )
    override val productImageUrlFactory = ProductImageUrlFactory(remoteHostStore::currentHost)

    override val authRepository: AuthRepository = object : AuthRepository {
        override suspend fun register(username: String, password: String): AppResult<User> {
            registerCalls++
            return AppResult.Success(testStudent().copy(username = username))
        }
        override suspend fun login(username: String, password: String): AppResult<User> {
            loginCalls++
            val user = testStudent().copy(username = username)
            onLogin(user)
            return AppResult.Success(user)
        }
        override suspend fun restore() = AppResult.Success(testStudent())
        override suspend fun currentUser() = AppResult.Success(testStudent())
        override suspend fun logout() = Unit
    }

    override val practiceRepository: PracticeRepository = object : PracticeRepository {
        private var nextQuestionCalls = 0

        override suspend fun summary(language: LearnerLanguage) = AppResult.Success(
            PracticeSummary(10, 42, 3, 1, 2, 7),
        )
        override suspend fun nextQuestion(
            excludeIds: List<String>,
            language: LearnerLanguage,
        ): AppResult<Question> {
            nextQuestionCalls += 1
            return if (noUnansweredQuestionsAfterFirstQuestion && nextQuestionCalls > 1) {
                AppResult.Failure(
                    AppError(
                        httpStatus = 404,
                        code = "NO_UNANSWERED_QUESTIONS",
                        message = "没有未答题目",
                        requestId = null,
                    ),
                )
            } else {
                AppResult.Success(question)
            }
        }
        override suspend fun previewQuestions(count: Int, language: LearnerLanguage) =
            AppResult.Success(List(count) { index -> question.copy(id = "preview-${index + 1}") })
        override suspend fun answerFirst(
            questionId: String,
            selectedOptionId: String,
            idempotencyKey: String?,
        ) =
            AppResult.Success(answer)
        override suspend fun wrongQuestions(page: Int, language: LearnerLanguage) =
            AppResult.Success(Page<WrongQuestion>(emptyList(), PageMeta(page, 20, 0, 0)))
        override suspend fun answerWrong(
            questionId: String,
            selectedOptionId: String,
            idempotencyKey: String?,
        ) =
            AppResult.Success(answer)
    }

    override val pointsRepository: PointsRepository = object : PointsRepository {
        override suspend fun balance() = AppResult.Success(42)
        override suspend fun ledger(page: Int) =
            AppResult.Success(Page<PointLedgerEntry>(emptyList(), PageMeta(page, 20, 0, 0)))
    }

    override val productsRepository: ProductsRepository = object : ProductsRepository {
        override suspend fun page(search: String?, page: Int) =
            AppResult.Success(Page(listOf(product), PageMeta(page, 20, 1, 1)))
        override suspend fun detail(id: String) = AppResult.Success(product.copy(id = id))
    }

    override val ordersRepository: OrdersRepository = object : OrdersRepository {
        override suspend fun redeem(productId: String, idempotencyKey: String?) =
            AppResult.Success(order.copy(productId = productId))
        override suspend fun page(page: Int) =
            AppResult.Success(Page(listOf(order), PageMeta(page, 20, 1, 1)))
        override suspend fun detail(id: String) = AppResult.Success(order.copy(id = id))
    }

    private companion object {
        val question = Question(
            id = "question-1",
            stem = "1 + 1 等于几？",
            basePoints = 5,
            options = listOf(
                QuestionOption("option-1", "A", "1", 1),
                QuestionOption("option-2", "B", "2", 2),
            ),
        )
        val answer = AnswerResult(47, true, "option-2", 0, "1 + 1 = 2", 5, "option-2")
        val product = Product(
            id = "product-1",
            name = "测试笔记本",
            description = "真实商品详情分支",
            imageKey = "invalid-test-image",
            pointsCost = 10,
            stock = 2,
            isActive = true,
            createdAt = Instant.parse("2030-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2030-01-01T00:00:00Z"),
        )
        val order = Order(
            id = "order-1",
            orderNo = "TEST-ORDER-1",
            userId = "student-device-test",
            productId = "product-1",
            productNameSnapshot = "测试笔记本",
            productImageKeySnapshot = "invalid-test-image",
            pointsCostSnapshot = 10,
            status = OrderStatus.PENDING_PICKUP,
            balance = 32,
            createdAt = Instant.parse("2030-01-01T00:00:00Z"),
            completedAt = null,
            cancelledAt = null,
            updatedBy = null,
        )
    }
}
