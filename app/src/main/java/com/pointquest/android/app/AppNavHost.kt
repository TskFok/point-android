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
import com.pointquest.android.R
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun AppNavHost(
    sessionStatus: SessionStatus,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val resolver = RootDestinationResolver()
    val startDestination = resolver.resolve(sessionStatus)

    LaunchedEffect(sessionStatus) {
        val target = resolver.resolve(sessionStatus)
        if (!navController.currentDestination.matches(target)) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<AppRoute.Splash> { SplashScreen() }
        composable<AppRoute.Login> {
            PlaceholderScreen(R.string.login_title, R.string.login_placeholder)
        }
        composable<AppRoute.Register> {
            PlaceholderScreen(R.string.register_title, R.string.register_placeholder)
        }
        composable<AppRoute.Home> {
            TopLevelPlaceholder(navController, R.string.home_title, R.string.home_placeholder)
        }
        composable<AppRoute.Practice> {
            TopLevelPlaceholder(navController, R.string.practice_title, R.string.practice_placeholder)
        }
        composable<AppRoute.Shop> {
            TopLevelPlaceholder(navController, R.string.shop_title, R.string.shop_placeholder)
        }
        composable<AppRoute.Profile> {
            TopLevelPlaceholder(navController, R.string.profile_title, R.string.profile_placeholder)
        }
        composable<AppRoute.Question> {
            PlaceholderScreen(R.string.question_title, R.string.question_placeholder)
        }
        composable<AppRoute.WrongQuestions> {
            PlaceholderScreen(R.string.wrong_questions_title, R.string.wrong_questions_placeholder)
        }
        composable<AppRoute.ProductDetail> {
            PlaceholderScreen(R.string.product_detail_title, R.string.product_detail_placeholder)
        }
        composable<AppRoute.Orders> {
            PlaceholderScreen(R.string.orders_title, R.string.orders_placeholder)
        }
        composable<AppRoute.OrderDetail> {
            PlaceholderScreen(R.string.order_detail_title, R.string.order_detail_placeholder)
        }
        composable<AppRoute.Points> {
            PlaceholderScreen(R.string.points_title, R.string.points_placeholder)
        }
    }
}

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
private fun TopLevelPlaceholder(
    navController: NavHostController,
    titleRes: Int,
    copyRes: Int,
) {
    PointScaffold(
        title = stringResource(titleRes),
        bottomBar = { TopLevelNavigationBar(navController) },
    ) { padding -> PlaceholderBody(copyRes, Modifier.padding(padding)) }
}

@Composable
private fun PlaceholderScreen(titleRes: Int, copyRes: Int) {
    PointScaffold(title = stringResource(titleRes)) { padding ->
        PlaceholderBody(copyRes, Modifier.padding(padding))
    }
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
    navigate(route) {
        popUpTo<AppRoute.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
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
