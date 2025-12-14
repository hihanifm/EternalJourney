package com.hanifm.eternaljourney.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hanifm.eternaljourney.ui.DeviceSelectionScreen
import com.hanifm.eternaljourney.ui.AudioFilesScreen
import com.hanifm.eternaljourney.ui.PlaybackControlScreen

sealed class Screen(val route: String) {
    object DeviceSelection : Screen("device_selection")
    object AudioFiles : Screen("audio_files")
    object PlaybackControl : Screen("playback_control")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.DeviceSelection.route
    ) {
        composable(Screen.DeviceSelection.route) {
            DeviceSelectionScreen()
        }
        composable(Screen.AudioFiles.route) {
            AudioFilesScreen()
        }
        composable(Screen.PlaybackControl.route) {
            PlaybackControlScreen()
        }
    }
}

