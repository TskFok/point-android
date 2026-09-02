package com.pointquest.android.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointquest.android.R
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.ui.ViewModelFactory
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.feature.auth.AuthEvent
import com.pointquest.android.feature.auth.AuthUiState
import com.pointquest.android.feature.auth.AuthViewModel
import com.pointquest.android.feature.auth.LoginScreen
import com.pointquest.android.feature.auth.RemoteHostUiState
import com.pointquest.android.feature.auth.RemoteHostViewModel
import com.pointquest.android.feature.auth.RegisterScreen
import com.pointquest.android.feature.home.HomeScreen
import com.pointquest.android.feature.home.HomeViewModel
import com.pointquest.android.feature.orders.OrderDetailScreen
import com.pointquest.android.feature.orders.OrderDetailViewModel
import com.pointquest.android.feature.orders.OrderListScreen
import com.pointquest.android.feature.orders.OrderListViewModel
import com.pointquest.android.feature.points.PointsScreen
import com.pointquest.android.feature.points.PointsViewModel
import com.pointquest.android.feature.profile.ProfileScreen
import com.pointquest.android.feature.profile.ProfileViewModel
import com.pointquest.android.feature.practice.PracticeHubScreen
import com.pointquest.android.feature.practice.PreviewScreen
import com.pointquest.android.feature.practice.PreviewViewModel
import com.pointquest.android.feature.practice.QuestionEvent
import com.pointquest.android.feature.practice.QuestionScreen
import com.pointquest.android.feature.practice.QuestionViewModel
import com.pointquest.android.feature.practice.WrongQuestionsScreen
import com.pointquest.android.feature.practice.WrongQuestionsViewModel
import com.pointquest.android.feature.shop.ProductDetailEvent
import com.pointquest.android.feature.shop.ProductDetailScreen
import com.pointquest.android.feature.shop.ProductDetailViewModel
import com.pointquest.android.feature.shop.ProductListScreen
import com.pointquest.android.feature.shop.ProductListViewModel

@Composable
fun AppNavHost(
    sessionStatus: SessionStatus,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    container: AppDependencies? = null,
) {
    val resolver = RootDestinationResolver()
    val startDestination = resolver.resolve(sessionStatus)
    val remoteHostViewModel = if (container == null) {
        null
    } else {
        val remoteHostFactory = remember(container.remoteHostStore) {
            ViewModelFactory<RemoteHostViewModel> {
                RemoteHostViewModel(container.remoteHostStore)
            }
        }
        viewModel<RemoteHostViewModel>(factory = remoteHostFactory)
    }

    LaunchedEffect(sessionStatus) {
        val target = resolver.resolve(sessionStatus)
        AppNavigationPolicy.rootRequest(
            sessionStatus = sessionStatus,
            alreadyAtTarget = navController.currentDestination.matches(target),
        )?.let { request ->
            navController.navigate(request.target) {
                if (request.clearBackStack) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
                launchSingleTop = request.launchSingleTop
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<AppRoute.Splash> { SplashScreen() }
        composable<AppRoute.Login> { entry ->
            if (container == null) {
                LoginScreen(
                    state = AuthUiState(),
                    hostState = RemoteHostUiState(activeHost = "", draftHost = ""),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                    onRegister = { navController.navigate(AppRoute.Register) },
                    onHostChange = {},
                    onApplyHost = {},
                )
            } else {
                val hostViewModel = checkNotNull(remoteHostViewModel)
                val factory = remember(container.authRepository) {
                    ViewModelFactory<AuthViewModel> { AuthViewModel(container.authRepository) }
                }
                val authViewModel: AuthViewModel = viewModel(factory = factory)
                val state by authViewModel.uiState.collectAsStateWithLifecycle()
                val hostState by hostViewModel.uiState.collectAsStateWithLifecycle()
                val registeredUsername by entry.savedStateHandle
                    .getStateFlow<String?>(REGISTERED_USERNAME_KEY, null)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(registeredUsername) {
                    registeredUsername?.let { username ->
                        authViewModel.prefillUsername(username, registrationSucceeded = true)
                        entry.savedStateHandle.remove<String>(REGISTERED_USERNAME_KEY)
                    }
                }
                LoginScreen(
                    state = state,
                    hostState = hostState,
                    onUsernameChange = authViewModel::updateUsername,
                    onPasswordChange = authViewModel::updatePassword,
                    onLogin = {
                        if (hostViewModel.requireAppliedForAuthentication()) {
                            authViewModel.login()
                        }
                    },
                    onRegister = {
                        if (hostViewModel.requireAppliedForAuthentication()) {
                            navController.navigate(AppRoute.Register)
                        }
                    },
                    onHostChange = hostViewModel::updateHost,
                    onApplyHost = { hostViewModel.apply() },
                )
            }
        }
        composable<AppRoute.Register> {
            if (container == null) {
                RegisterScreen(
                    state = AuthUiState(),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onConfirmPasswordChange = {},
                    onRegister = {},
                    onBackToLogin = {
                        if (!navController.popBackStack()) navController.navigate(AppRoute.Login)
                    },
                )
            } else {
                val hostViewModel = checkNotNull(remoteHostViewModel)
                val factory = remember(container.authRepository) {
                    ViewModelFactory<AuthViewModel> { AuthViewModel(container.authRepository) }
                }
                val authViewModel: AuthViewModel = viewModel(factory = factory)
                val state by authViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(authViewModel) {
                    for (event in authViewModel.events) {
                        when (event) {
                            is AuthEvent.RegistrationSucceeded -> {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(REGISTERED_USERNAME_KEY, event.username)
                                if (!navController.popBackStack()) {
                                    navController.navigate(AppRoute.Login)
                                    navController.currentBackStackEntry
                                        ?.savedStateHandle
                                        ?.set(REGISTERED_USERNAME_KEY, event.username)
                                }
                            }
                        }
                    }
                }
                RegisterScreen(
                    state = state,
                    onUsernameChange = authViewModel::updateUsername,
                    onPasswordChange = authViewModel::updatePassword,
                    onConfirmPasswordChange = authViewModel::updateConfirmPassword,
                    onRegister = {
                        if (hostViewModel.requireAppliedForAuthentication()) {
                            authViewModel.register()
                        }
                    },
                    onBackToLogin = {
                        if (!navController.popBackStack()) navController.navigate(AppRoute.Login)
                    },
                )
            }
        }
        composable<AppRoute.Home> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Home, R.string.home_title, R.string.home_placeholder)
            } else {
                val factory = remember(container) {
                    ViewModelFactory<HomeViewModel> {
                        HomeViewModel(
                            container.practiceRepository,
                            container.pointsRepository,
                            container.sessionState,
                            container.learnerLanguageStore,
                            appDataSync = container.appDataSync,
                        )
                    }
                }
                val homeViewModel: HomeViewModel = viewModel(factory = factory)
                val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onRetry = { homeViewModel.retry() },
                    onStartPractice = { navController.navigate(AppRoute.Question(PracticeMode.FIRST, null)) },
                    onPreview = { navController.navigate(AppRoute.Preview) },
                    onWrongQuestions = { navController.navigate(AppRoute.WrongQuestions) },
                    onOrders = { navController.navigate(AppRoute.Orders) },
                    onPoints = { navController.navigate(AppRoute.Points) },
                    bottomBar = { TopLevelNavigationBar(navController) },
                )
            }
        }
        composable<AppRoute.Practice> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Practice, R.string.practice_title, R.string.practice_placeholder)
            } else {
                PracticeHubScreen(
                    onFirstPractice = { navController.navigate(AppRoute.Question(PracticeMode.FIRST, null)) },
                    onWrongQuestions = { navController.navigate(AppRoute.WrongQuestions) },
                    onPreview = { navController.navigate(AppRoute.Preview) },
                    bottomBar = { TopLevelNavigationBar(navController) },
                )
            }
        }
        composable<AppRoute.Preview> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Preview, R.string.preview_title, R.string.practice_placeholder)
            } else {
                val factory = remember(
                    container.practiceRepository,
                    container.learnerLanguageStore,
                    container.appDataSync,
                ) {
                    ViewModelFactory<PreviewViewModel> {
                        PreviewViewModel(
                            repository = container.practiceRepository,
                            learnerLanguageStore = container.learnerLanguageStore,
                            appDataSync = container.appDataSync,
                        )
                    }
                }
                val previewViewModel: PreviewViewModel = viewModel(factory = factory)
                val state by previewViewModel.uiState.collectAsStateWithLifecycle()
                PreviewScreen(
                    state = state,
                    onCountChange = previewViewModel::selectCount,
                    onStart = { previewViewModel.startPreview() },
                    onSelectOption = previewViewModel::selectOption,
                    onSubmit = { previewViewModel.submitCurrent() },
                    onPrevious = previewViewModel::goPrevious,
                    onNext = { previewViewModel.goNext() },
                    onRetryLoad = { previewViewModel.retryLoad() },
                    onRetrySubmit = { previewViewModel.retrySubmit() },
                    onReset = previewViewModel::resetSession,
                    onPractice = { navController.navigateTopLevel(AppRoute.Practice) },
                    onProfile = { navController.navigateTopLevel(AppRoute.Profile) },
                    onWrongQuestions = { navController.navigate(AppRoute.WrongQuestions) },
                    onHome = { navController.navigateTopLevel(AppRoute.Home) },
                )
            }
        }
        composable<AppRoute.Shop> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Shop, R.string.shop_title, R.string.shop_placeholder)
            } else {
                val factory = remember(container.productsRepository, container.pointsRepository, container.appDataSync) {
                    ViewModelFactory<ProductListViewModel> {
                        ProductListViewModel(
                            container.productsRepository,
                            appDataSync = container.appDataSync,
                            pointsRepository = container.pointsRepository,
                        )
                    }
                }
                val productListViewModel: ProductListViewModel = viewModel(factory = factory)
                val state by productListViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(productListViewModel) { productListViewModel.initialize() }
                ProductListScreen(
                    state = state,
                    imageUrlFactory = container.productImageUrlFactory,
                    onSearchChange = productListViewModel::updateSearch,
                    onRetry = { productListViewModel.retry() },
                    onRefresh = { productListViewModel.refresh() },
                    onRefreshErrorShown = productListViewModel::clearRefreshError,
                    onLoadMore = { productListViewModel.loadMore() },
                    onProductClick = { product ->
                        navController.navigate(AppRoute.ProductDetail(product.id))
                    },
                    bottomBar = { TopLevelNavigationBar(navController) },
                )
            }
        }
        composable<AppRoute.Profile> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Profile, R.string.profile_title, R.string.profile_placeholder)
            } else {
                val factory = remember(container) {
                    ViewModelFactory<ProfileViewModel> {
                        ProfileViewModel(
                            container.authRepository,
                            container.sessionState,
                            container.learnerLanguageStore,
                            appDataSync = container.appDataSync,
                        )
                    }
                }
                val profileViewModel: ProfileViewModel = viewModel(factory = factory)
                val state by profileViewModel.uiState.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = state,
                    onOrders = { navController.navigate(AppRoute.Orders) },
                    onPoints = { navController.navigate(AppRoute.Points) },
                    onRequestLogout = profileViewModel::requestLogout,
                    onDismissLogout = profileViewModel::dismissLogout,
                    onConfirmLogout = { profileViewModel.confirmLogout() },
                    onLanguageChange = profileViewModel::setLanguage,
                    bottomBar = { TopLevelNavigationBar(navController) },
                )
            }
        }
        composable<AppRoute.Question> { entry ->
            val route = entry.toRoute<AppRoute.Question>()
            if (container == null) {
                PlaceholderScreen(
                    navController,
                    route,
                    R.string.question_title,
                    R.string.question_placeholder,
                )
            } else {
                val factory = remember(container, route) {
                    ViewModelFactory<QuestionViewModel> {
                        QuestionViewModel(
                            repository = container.practiceRepository,
                            mode = route.mode,
                            draftStore = container.practiceDraftStore,
                            questionId = route.questionId,
                            appDataSync = container.appDataSync,
                            learnerLanguageStore = container.learnerLanguageStore,
                        )
                    }
                }
                val questionViewModel: QuestionViewModel = viewModel(factory = factory)
                val state by questionViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(questionViewModel) { questionViewModel.initialize() }
                LaunchedEffect(questionViewModel, navController) {
                    questionViewModel.events.collect { event ->
                        when (event) {
                            QuestionEvent.DraftMissing -> {
                                val previousEntry = navController.previousBackStackEntry
                                if (previousEntry != null) {
                                    previousEntry.savedStateHandle.set(DRAFT_EXPIRED_KEY, true)
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(AppRoute.WrongQuestions) {
                                        popUpTo<AppRoute.Question> { inclusive = true }
                                    }
                                    navController.currentBackStackEntry
                                        ?.savedStateHandle
                                        ?.set(DRAFT_EXPIRED_KEY, true)
                                }
                            }
                            is QuestionEvent.WrongMastered -> {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(MASTERED_QUESTION_KEY, event.questionId)
                                if (event.returnToList && !navController.popBackStack()) {
                                    navController.navigate(AppRoute.WrongQuestions) {
                                        popUpTo<AppRoute.Question> { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                }
                QuestionScreen(
                    state = state,
                    onSelectOption = questionViewModel::selectOption,
                    onSubmit = { questionViewModel.submit() },
                    onPrevious = questionViewModel::goPrevious,
                    onNext = {
                        when (route.mode) {
                            PracticeMode.FIRST -> if (state.completed) {
                                if (!navController.popBackStack()) navController.navigate(AppRoute.Practice)
                            } else {
                                questionViewModel.goNext()
                            }
                            PracticeMode.WRONG -> if (!navController.popBackStack()) {
                                navController.navigate(AppRoute.WrongQuestions)
                            }
                        }
                    },
                    onRetry = questionViewModel::load,
                    onRetryTailLoad = { questionViewModel.retryTailLoad() },
                    onWrongQuestions = { navController.navigate(AppRoute.WrongQuestions) },
                    onPreview = { navController.navigate(AppRoute.Preview) },
                    onProfile = { navController.navigateTopLevel(AppRoute.Profile) },
                )
            }
        }
        composable<AppRoute.WrongQuestions> { entry ->
            if (container == null) {
                PlaceholderScreen(
                    navController,
                    AppRoute.WrongQuestions,
                    R.string.wrong_questions_title,
                    R.string.wrong_questions_placeholder,
                )
            } else {
                val factory = remember(container.practiceRepository) {
                    ViewModelFactory<WrongQuestionsViewModel> {
                        WrongQuestionsViewModel(
                            repository = container.practiceRepository,
                            learnerLanguageStore = container.learnerLanguageStore,
                        )
                    }
                }
                val wrongQuestionsViewModel: WrongQuestionsViewModel = viewModel(factory = factory)
                val state by wrongQuestionsViewModel.uiState.collectAsStateWithLifecycle()
                val masteredQuestionId by entry.savedStateHandle
                    .getStateFlow<String?>(MASTERED_QUESTION_KEY, null)
                    .collectAsStateWithLifecycle()
                val draftExpired by entry.savedStateHandle
                    .getStateFlow(DRAFT_EXPIRED_KEY, false)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(wrongQuestionsViewModel) { wrongQuestionsViewModel.initialize() }
                LaunchedEffect(masteredQuestionId) {
                    masteredQuestionId?.let { questionId ->
                        wrongQuestionsViewModel.removeMastered(questionId)
                        entry.savedStateHandle.remove<String>(MASTERED_QUESTION_KEY)
                    }
                }
                LaunchedEffect(draftExpired) {
                    if (draftExpired) {
                        wrongQuestionsViewModel.showDraftExpiredNotice()
                        entry.savedStateHandle.remove<Boolean>(DRAFT_EXPIRED_KEY)
                    }
                }
                WrongQuestionsScreen(
                    state = state,
                    onRetry = wrongQuestionsViewModel::load,
                    onLoadMore = { wrongQuestionsViewModel.loadMore() },
                    onSelectQuestion = { wrongQuestion ->
                        container.practiceDraftStore.put(wrongQuestion)
                        navController.navigate(
                            AppRoute.Question(PracticeMode.WRONG, wrongQuestion.question.id),
                        )
                    },
                    onNoticeShown = wrongQuestionsViewModel::clearNotice,
                    onFirstPractice = { navController.navigate(AppRoute.Question(PracticeMode.FIRST, null)) },
                    onPreview = { navController.navigate(AppRoute.Preview) },
                    onProfile = { navController.navigateTopLevel(AppRoute.Profile) },
                )
            }
        }
        composable<AppRoute.ProductDetail> { entry ->
            val route = entry.toRoute<AppRoute.ProductDetail>()
            if (container == null) {
                PlaceholderScreen(
                    navController,
                    route,
                    R.string.product_detail_title,
                    R.string.product_detail_placeholder,
                )
            } else {
                val factory = remember(container, route.productId) {
                    ViewModelFactory<ProductDetailViewModel> {
                        ProductDetailViewModel(
                            route.productId,
                            container.productsRepository,
                            container.ordersRepository,
                            container.pointsRepository,
                            appDataSync = container.appDataSync,
                        )
                    }
                }
                val productDetailViewModel: ProductDetailViewModel = viewModel(factory = factory)
                val state by productDetailViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(productDetailViewModel) { productDetailViewModel.initialize() }
                LaunchedEffect(productDetailViewModel, navController) {
                    productDetailViewModel.events.collect { event ->
                        when (event) {
                            is ProductDetailEvent.NavigateToOrder -> {
                                navController.navigate(AppRoute.OrderDetail(event.orderId))
                            }
                            ProductDetailEvent.ReturnToShop -> if (!navController.popBackStack()) {
                                navController.navigate(AppRoute.Shop) {
                                    popUpTo<AppRoute.ProductDetail> { inclusive = true }
                                }
                            }
                        }
                    }
                }
                ProductDetailScreen(
                    state = state,
                    imageUrlFactory = container.productImageUrlFactory,
                    onRetry = { productDetailViewModel.retry() },
                    onBack = {
                        if (!navController.popBackStack()) navController.navigate(AppRoute.Shop)
                    },
                    onRequestRedeem = productDetailViewModel::requestRedeemConfirmation,
                    onDismissRedeem = productDetailViewModel::dismissRedeemConfirmation,
                    onConfirmRedeem = { productDetailViewModel.confirmRedeem() },
                    onMessageShown = productDetailViewModel::clearMessage,
                )
            }
        }
        composable<AppRoute.Orders> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Orders, R.string.orders_title, R.string.orders_placeholder)
            } else {
                val factory = remember(container.ordersRepository) {
                    ViewModelFactory<OrderListViewModel> { OrderListViewModel(container.ordersRepository) }
                }
                val orderListViewModel: OrderListViewModel = viewModel(factory = factory)
                val state by orderListViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(orderListViewModel) { orderListViewModel.initialize() }
                OrderListScreen(
                    state = state,
                    imageUrlFactory = container.productImageUrlFactory,
                    onRetry = { orderListViewModel.retry() },
                    onLoadMore = { orderListViewModel.loadMore() },
                    onOrderClick = { order -> navController.navigate(AppRoute.OrderDetail(order.id)) },
                    onBack = { navController.popBackStack() },
                    onShop = { navController.navigate(AppRoute.Shop) },
                )
            }
        }
        composable<AppRoute.OrderDetail> { entry ->
            val route = entry.toRoute<AppRoute.OrderDetail>()
            if (container == null) {
                PlaceholderScreen(
                    navController,
                    route,
                    R.string.order_detail_title,
                    R.string.order_detail_placeholder,
                )
            } else {
                val factory = remember(container.ordersRepository, route.orderId) {
                    ViewModelFactory<OrderDetailViewModel> {
                        OrderDetailViewModel(route.orderId, container.ordersRepository)
                    }
                }
                val orderDetailViewModel: OrderDetailViewModel = viewModel(factory = factory)
                val state by orderDetailViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(orderDetailViewModel) { orderDetailViewModel.initialize() }
                OrderDetailScreen(
                    state = state,
                    imageUrlFactory = container.productImageUrlFactory,
                    onRetry = { orderDetailViewModel.retry() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<AppRoute.Points> {
            if (container == null) {
                PlaceholderScreen(navController, AppRoute.Points, R.string.points_title, R.string.points_placeholder)
            } else {
                val factory = remember(container.pointsRepository) {
                    ViewModelFactory<PointsViewModel> { PointsViewModel(container.pointsRepository) }
                }
                val pointsViewModel: PointsViewModel = viewModel(factory = factory)
                val state by pointsViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(pointsViewModel) { pointsViewModel.initialize() }
                PointsScreen(
                    state = state,
                    onRetry = { pointsViewModel.retry() },
                    onLoadMore = { pointsViewModel.loadMore() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private const val REGISTERED_USERNAME_KEY = "registered_username"
private const val MASTERED_QUESTION_KEY = "mastered_question"
private const val DRAFT_EXPIRED_KEY = "draft_expired"

@Composable
private fun SplashScreen() {
    val copy = stringResource(R.string.restoring_session)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = copy })
        Text(copy)
    }
}

@Composable
private fun PlaceholderScreen(
    navController: NavHostController,
    route: AppRoute,
    titleRes: Int,
    copyRes: Int,
) {
    PointScaffold(
        title = stringResource(titleRes),
        bottomBar = {
            if (AppNavigationPolicy.showsBottomBar(route)) {
                TopLevelNavigationBar(navController)
            }
        },
    ) { padding -> PlaceholderBody(copyRes, Modifier.padding(padding)) }
}

@Composable
private fun PlaceholderBody(copyRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PointCard {
            Text(
                text = stringResource(copyRes),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TopLevelNavigationBar(navController: NavHostController) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    val destinations = listOf(
        TopLevelDestination(AppRoute.Home, R.string.tab_home, TopLevelIcon.Home),
        TopLevelDestination(AppRoute.Practice, R.string.tab_practice, TopLevelIcon.Practice),
        TopLevelDestination(AppRoute.Shop, R.string.tab_shop, TopLevelIcon.Shop),
        TopLevelDestination(AppRoute.Profile, R.string.tab_profile, TopLevelIcon.Profile),
    )

    NavigationBar {
        destinations.forEach { destination ->
            val selected = currentDestination.matches(destination.route)
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateTopLevel(destination.route) },
                icon = { TopLevelIcon(destination.icon, label, selected) },
                label = { Text(label) },
                alwaysShowLabel = true,
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: AppRoute) {
    val request = AppNavigationPolicy.topLevelRequest(route)
    navigate(request.target) {
        popUpTo(request.anchor) { saveState = request.saveState }
        launchSingleTop = request.launchSingleTop
        restoreState = request.restoreState
    }
}

private fun NavDestination?.matches(route: AppRoute): Boolean = when (route) {
    AppRoute.Splash -> this?.hasRoute<AppRoute.Splash>() == true
    AppRoute.Login -> this?.hasRoute<AppRoute.Login>() == true
    AppRoute.Register -> this?.hasRoute<AppRoute.Register>() == true
    AppRoute.Home -> this?.hasRoute<AppRoute.Home>() == true
    AppRoute.Practice -> this?.hasRoute<AppRoute.Practice>() == true
    AppRoute.Shop -> this?.hasRoute<AppRoute.Shop>() == true
    AppRoute.Profile -> this?.hasRoute<AppRoute.Profile>() == true
    AppRoute.Preview -> this?.hasRoute<AppRoute.Preview>() == true
    is AppRoute.Question -> this?.hasRoute<AppRoute.Question>() == true
    AppRoute.WrongQuestions -> this?.hasRoute<AppRoute.WrongQuestions>() == true
    is AppRoute.ProductDetail -> this?.hasRoute<AppRoute.ProductDetail>() == true
    AppRoute.Orders -> this?.hasRoute<AppRoute.Orders>() == true
    is AppRoute.OrderDetail -> this?.hasRoute<AppRoute.OrderDetail>() == true
    AppRoute.Points -> this?.hasRoute<AppRoute.Points>() == true
}

private data class TopLevelDestination(
    val route: AppRoute,
    val labelRes: Int,
    val icon: TopLevelIcon,
)

private enum class TopLevelIcon { Home, Practice, Shop, Profile }

@Composable
private fun TopLevelIcon(icon: TopLevelIcon, label: String, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { contentDescription = label },
    ) {
        val stroke = size.minDimension * 0.09f
        when (icon) {
            TopLevelIcon.Home -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.48f)
                    lineTo(size.width * 0.5f, size.height * 0.16f)
                    lineTo(size.width * 0.88f, size.height * 0.48f)
                }
                drawPath(roof, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawRect(
                    color,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.46f),
                    size = Size(size.width * 0.52f, size.height * 0.4f),
                    style = Stroke(stroke),
                )
            }
            TopLevelIcon.Practice -> repeat(3) { index ->
                val y = size.height * (0.28f + index * 0.22f)
                drawLine(color, Offset(size.width * 0.18f, y), Offset(size.width * 0.82f, y), stroke, StrokeCap.Round)
            }
            TopLevelIcon.Shop -> {
                drawRect(
                    color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.28f),
                    size = Size(size.width * 0.64f, size.height * 0.42f),
                    style = Stroke(stroke),
                )
                drawCircle(color, stroke, Offset(size.width * 0.32f, size.height * 0.82f))
                drawCircle(color, stroke, Offset(size.width * 0.7f, size.height * 0.82f))
            }
            TopLevelIcon.Profile -> {
                drawCircle(color, size.width * 0.16f, Offset(size.width * 0.5f, size.height * 0.32f))
                drawArc(
                    color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.46f),
                    size = Size(size.width * 0.64f, size.height * 0.48f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
