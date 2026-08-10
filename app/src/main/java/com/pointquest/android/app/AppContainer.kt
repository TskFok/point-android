package com.pointquest.android.app

import android.content.Context
import coil3.ImageLoader
import com.pointquest.android.BuildConfig
import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SecureSessionStore
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.network.ApiClients
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.auth.AuthRepository
import com.pointquest.android.data.auth.DefaultAuthRepository
import com.pointquest.android.data.gateway.GeneratedPublicAuthGateway
import com.pointquest.android.data.gateway.GeneratedStudentGateway
import com.pointquest.android.data.orders.DefaultOrdersRepository
import com.pointquest.android.data.orders.OrdersRepository
import com.pointquest.android.data.points.DefaultPointsRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.practice.DefaultPracticeRepository
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.data.products.DefaultProductsRepository
import com.pointquest.android.data.products.ProductImageUrlFactory
import com.pointquest.android.data.products.ProductsRepository
import com.pointquest.android.feature.practice.PracticeDraftStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class AppContainer(
    context: Context,
    apiBaseUrl: String = BuildConfig.API_BASE_URL,
    imageBaseUrl: String = BuildConfig.IMAGE_BASE_URL,
) : AppDependencies {
    private val applicationContext = context.applicationContext

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    override val sessionState = SessionState()
    private val sessionStore = SecureSessionStore(applicationContext, moshi = moshi)
    private val sessionManager = SessionManager(sessionStore, sessionState)

    private val apiClients = ApiClients(apiBaseUrl, sessionState)
    private val publicAuthGateway = GeneratedPublicAuthGateway(apiClients.publicApi)
    private val studentGateway = GeneratedStudentGateway(apiClients.protectedApi)
    private val refreshCoordinator = RefreshCoordinator(publicAuthGateway, sessionManager, sessionState)
    private val authorizedCallExecutor = AuthorizedCallExecutor(sessionState, refreshCoordinator)
    private val retryExecutor = RetryExecutor()
    override val appDataSync = AppDataSync(sessionState)

    val imageLoader: ImageLoader = createProductImageLoader(applicationContext, apiClients.publicHttpClient)

    override val authRepository: AuthRepository = DefaultAuthRepository(
        publicAuthGateway,
        sessionManager,
        sessionState,
        refreshCoordinator,
    )
    override val practiceRepository: PracticeRepository = DefaultPracticeRepository(
        studentGateway,
        authorizedCallExecutor,
        retryExecutor,
    )
    override val practiceDraftStore = PracticeDraftStore()
    override val pointsRepository: PointsRepository = DefaultPointsRepository(
        studentGateway,
        authorizedCallExecutor,
        retryExecutor,
    )
    override val productsRepository: ProductsRepository = DefaultProductsRepository(
        studentGateway,
        authorizedCallExecutor,
        retryExecutor,
    )
    override val ordersRepository: OrdersRepository = DefaultOrdersRepository(
        studentGateway,
        authorizedCallExecutor,
        retryExecutor,
    )
    override val productImageUrlFactory = ProductImageUrlFactory(imageBaseUrl)
}
