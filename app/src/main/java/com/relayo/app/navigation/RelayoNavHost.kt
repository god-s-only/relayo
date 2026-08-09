package com.relayo.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.relayo.feature.voicenotes.VoiceNotesScreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.relayo.feature.messages.MessagesScreen
import com.relayo.feature.meshstatus.MeshStatusScreen

@Composable
fun RelayoNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            RelayoBottomBar(navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RelayoDestination.MeshStatus.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(RelayoDestination.MeshStatus.route) {
                MeshStatusScreen()
            }
            composable(RelayoDestination.Messages.route) {
                MessagesScreen()
            }
            composable(RelayoDestination.VoiceNotes.route) {
                VoiceNotesScreen()
            }
        }
    }
}

@Composable
private fun RelayoBottomBar(navController:androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        RelayoDestination.bottomNavItems.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = when(destination) {
                            RelayoDestination.MeshStatus -> Icons.Filled.Wifi
                            RelayoDestination.Messages -> Icons.Filled.Chat
                            RelayoDestination.VoiceNotes -> Icons.Filled.Mic
                        },
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}