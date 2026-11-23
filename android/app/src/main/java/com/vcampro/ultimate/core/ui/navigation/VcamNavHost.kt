package com.vcampro.ultimate.core.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vcampro.ultimate.features.aivision.AiVisionScreen
import com.vcampro.ultimate.features.about.AboutScreen
import com.vcampro.ultimate.features.camera.CameraScreen
import com.vcampro.ultimate.features.dashboard.DashboardScreen
import com.vcampro.ultimate.features.devicecenter.DeviceCenterScreen
import com.vcampro.ultimate.features.faceprofile.FaceProfileDetailScreen
import com.vcampro.ultimate.features.faceprofile.FaceProfileScreen
import com.vcampro.ultimate.features.medialibrary.MediaLibraryScreen
import com.vcampro.ultimate.features.onboarding.OnboardingScreen
import com.vcampro.ultimate.features.photostudio.PhotoStudioScreen
import com.vcampro.ultimate.features.processing.ProcessingScreen
import com.vcampro.ultimate.features.security.SecurityScreen
import com.vcampro.ultimate.features.settings.SettingsScreen
import com.vcampro.ultimate.features.splash.SplashScreen
import com.vcampro.ultimate.features.videostudio.VideoStudioScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CAMERA = "camera"
    const val PROFILES = "profiles"
    const val PROFILE_DETAIL = "profile/{profileId}"
    const val AI_VISION = "aivision"
    const val PHOTO_STUDIO = "photo?mediaId={mediaId}"
    const val VIDEO_STUDIO = "video?mediaId={mediaId}"
    const val LIBRARY = "library"
    const val PROCESSING = "processing"
    const val DEVICE_CENTER = "device"
    const val SECURITY = "security"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun profileDetail(id: Long) = "profile/$id"
    fun photoStudio(mediaId: Long) = "photo?mediaId=$mediaId"
    fun videoStudio(mediaId: Long) = "video?mediaId=$mediaId"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomItem(Routes.DASHBOARD, "Tổng quan", Icons.Outlined.Dashboard),
    BottomItem(Routes.LIBRARY, "Thư viện", Icons.Outlined.PhotoLibrary),
    BottomItem(Routes.CAMERA, "Camera", Icons.Outlined.PhotoCamera),
    BottomItem(Routes.PROCESSING, "Tác vụ", Icons.Outlined.Memory),
    BottomItem(Routes.SETTINGS, "Cài đặt", Icons.Outlined.Settings)
)

@Composable
fun VcamNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp
                ) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onReady = { needsOnboarding ->
                        val next = if (needsOnboarding) Routes.ONBOARDING else Routes.DASHBOARD
                        navController.navigate(next) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.CAMERA) {
                CameraScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) }
                )
            }
            composable(Routes.PROFILES) {
                FaceProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { id -> navController.navigate(Routes.profileDetail(id)) }
                )
            }
            composable(
                route = Routes.PROFILE_DETAIL,
                arguments = listOf(navArgument("profileId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("profileId") ?: 0L
                FaceProfileDetailScreen(
                    profileId = id,
                    onBack = { navController.popBackStack() },
                    onOpenPhoto = { mediaId -> navController.navigate(Routes.photoStudio(mediaId)) },
                    onOpenVideo = { mediaId -> navController.navigate(Routes.videoStudio(mediaId)) }
                )
            }
            composable(Routes.AI_VISION) {
                AiVisionScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PHOTO_STUDIO,
                arguments = listOf(navArgument("mediaId") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { entry ->
                PhotoStudioScreen(
                    mediaId = entry.arguments?.getLong("mediaId") ?: -1L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.VIDEO_STUDIO,
                arguments = listOf(navArgument("mediaId") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { entry ->
                VideoStudioScreen(
                    mediaId = entry.arguments?.getLong("mediaId") ?: -1L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.LIBRARY) {
                MediaLibraryScreen(
                    onOpenPhoto = { id -> navController.navigate(Routes.photoStudio(id)) },
                    onOpenVideo = { id -> navController.navigate(Routes.videoStudio(id)) }
                )
            }
            composable(Routes.PROCESSING) {
                ProcessingScreen()
            }
            composable(Routes.DEVICE_CENTER) {
                DeviceCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SECURITY) {
                SecurityScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDeviceCenter = { navController.navigate(Routes.DEVICE_CENTER) },
                    onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                    onOpenAiVision = { navController.navigate(Routes.AI_VISION) }
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
