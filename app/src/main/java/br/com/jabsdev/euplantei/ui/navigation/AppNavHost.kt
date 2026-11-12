package br.com.jabsdev.euplantei.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.prefs.AppSettings
import br.com.jabsdev.euplantei.ui.screens.camera.CameraScreen
import br.com.jabsdev.euplantei.ui.screens.garden.GardenDetailScreen
import br.com.jabsdev.euplantei.ui.screens.garden.GardenDetailViewModel
import br.com.jabsdev.euplantei.ui.screens.garden.GardenScreen
import br.com.jabsdev.euplantei.ui.screens.history.HistoryDetailScreen
import br.com.jabsdev.euplantei.ui.screens.history.HistoryScreen
import br.com.jabsdev.euplantei.ui.screens.home.HomeScreen
import br.com.jabsdev.euplantei.ui.screens.onboarding.OnboardingScreen
import br.com.jabsdev.euplantei.ui.screens.result.ResultScreen
import br.com.jabsdev.euplantei.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CAMERA = "camera"
    const val RESULT = "result?imagePath={imagePath}"
    const val GARDEN = "garden"
    const val GARDEN_DETAIL = "garden/{plantId}"
    const val GARDEN_CAMERA = "garden/{plantId}/camera"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val SETTINGS = "settings"

    fun result(imagePath: String): String = "result?imagePath=${Uri.encode(imagePath)}"

    fun historyDetail(id: Long): String = "history/$id"

    fun gardenDetail(id: Long): String = "garden/$id"

    fun gardenCamera(id: Long): String = "garden/$id/camera"
}

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    BottomDestination(Routes.GARDEN, R.string.nav_garden, Icons.Rounded.Yard, Icons.Outlined.Yard),
    BottomDestination(Routes.HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
fun EuPlanteiApp(
    container: AppContainer,
    settings: AppSettings,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    openGardenInitially: Boolean = false,
    onOpenGardenHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    LaunchedEffect(openGardenInitially) {
        if (openGardenInitially && settings.onboardingCompleted) {
            navController.navigate(Routes.GARDEN) {
                launchSingleTop = true
            }
            onOpenGardenHandled()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (settings.onboardingCompleted) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(320),
                    initialOffsetX = { it / 5 },
                ) + fadeIn(tween(220))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(320),
                    targetOffsetX = { -it / 8 },
                ) + fadeOut(tween(180))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(320),
                    initialOffsetX = { -it / 5 },
                ) + fadeIn(tween(220))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(320),
                    targetOffsetX = { it / 8 },
                ) + fadeOut(tween(180))
            },
        ) {
            composable(
                route = Routes.ONBOARDING,
                exitTransition = { fadeOut(tween(220)) },
            ) {
                OnboardingScreen(
                    onFinish = onOnboardingComplete,
                    onConfigureModel = {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onDownloadModel = {
                        container.modelRepository.startDownload()
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.HOME,
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                HomeScreen(
                    container = container,
                    onTakePhoto = { navController.navigate(Routes.CAMERA) },
                    onGalleryReady = { path -> navController.navigate(Routes.result(path)) },
                    onOpenHistory = { navigateToTab(navController, Routes.HISTORY) },
                    onOpenSettings = { navigateToTab(navController, Routes.SETTINGS) },
                    onOpenConsultation = { id -> navController.navigate(Routes.historyDetail(id)) },
                    onResumeDraft = { path -> navController.navigate(Routes.result(path)) },
                )
            }

            composable(Routes.CAMERA) {
                CameraScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoReady = { path ->
                        navController.navigate(Routes.result(path)) {
                            popUpTo(Routes.CAMERA) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.RESULT,
                arguments = listOf(
                    navArgument("imagePath") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val imagePath = entry.arguments?.getString("imagePath").orEmpty()
                ResultScreen(
                    container = container,
                    imagePath = imagePath,
                    onBack = { navController.popBackStack() },
                    onNewConsultation = {
                        navController.navigate(Routes.CAMERA) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenGarden = {
                        navController.navigate(Routes.GARDEN) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }

            composable(Routes.GARDEN) {
                GardenScreen(
                    container = container,
                    onAddPlant = { navController.navigate(Routes.CAMERA) },
                    onOpenPlant = { id -> navController.navigate(Routes.gardenDetail(id)) },
                    onOpenSettings = { navigateToTab(navController, Routes.SETTINGS) },
                )
            }

            composable(
                route = Routes.GARDEN_DETAIL,
                arguments = listOf(
                    navArgument("plantId") { type = NavType.LongType },
                ),
            ) { entry ->
                val plantId = entry.arguments?.getLong("plantId") ?: 0L
                GardenDetailScreen(
                    container = container,
                    plantId = plantId,
                    onBack = { navController.popBackStack() },
                    onAddPhoto = { navController.navigate(Routes.gardenCamera(plantId)) },
                )
            }

            composable(
                route = Routes.GARDEN_CAMERA,
                arguments = listOf(
                    navArgument("plantId") { type = NavType.LongType },
                ),
            ) { entry ->
                val plantId = entry.arguments?.getLong("plantId") ?: 0L
                val detailViewModel: GardenDetailViewModel = viewModel(
                    key = "garden_camera_$plantId",
                    factory = GardenDetailViewModel.factory(container, plantId),
                )
                val scope = rememberCoroutineScope()
                CameraScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoReady = { path ->
                        scope.launch {
                            detailViewModel.addPhoto(path)
                            navController.popBackStack()
                        }
                    },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onTakePhoto = { navController.navigate(Routes.CAMERA) },
                    onOpenDetail = { id -> navController.navigate(Routes.historyDetail(id)) },
                )
            }

            composable(
                route = Routes.HISTORY_DETAIL,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                ),
            ) { entry ->
                HistoryDetailScreen(
                    container = container,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
