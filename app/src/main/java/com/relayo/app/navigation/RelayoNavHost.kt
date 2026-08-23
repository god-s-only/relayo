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
import com.relayo.feature.qrboards.QrBoardsScreen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import com.relayo.feature.alerts.AlertsScreen
import androidx.compose.material.icons.filled.Warning
import com.relayo.feature.voicenotes.VoiceNotesScreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.getValue
import com.relayo.feature.newsfeed.NewsFeedScreen
import com.relayo.feature.bridge.BridgeScreen
import androidx.compose.material.icons.filled.Cloud
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.relayo.feature.messages.ConversationsScreen
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.relayo.feature.messages.MessagesScreen
import com.relayo.feature.meshstatus.MeshStatusScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.relayo.app.session.AppSessionViewModel
import com.relayo.feature.voicenotes.VoiceNoteConversationsScreen

@Composable
fun RelayoNavHost(
    sessionViewModel:AppSessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val requiredPermissions = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if(results.values.all { it }) sessionViewModel.onPermissionsGranted()
    }

    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if(allGranted) {
            sessionViewModel.onPermissionsGranted()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

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
            composable(RelayoDestination.VoiceNotes.route) {
                VoiceNoteConversationsScreen(
                    onConversationSelected = { peerId ->
                        navController.navigate(RelayoDestination.voiceDetailRoute(peerId))
                    }
                )
            }
            composable(
                route = RelayoDestination.VOICE_DETAIL_ROUTE,
                arguments = listOf(navArgument("peerId") { type = NavType.StringType })
            ) {
                VoiceNotesScreen()
            }
            composable(RelayoDestination.NewsFeed.route) {
                NewsFeedScreen()
            }
            composable(RelayoDestination.Alerts.route) {
                AlertsScreen()
            }
            composable(RelayoDestination.QrBoards.route) {
                QrBoardsScreen()
            }
            composable(RelayoDestination.Bridge.route) {
                BridgeScreen()
            }
            composable(RelayoDestination.Messages.route) {
                ConversationsScreen(
                    onConversationSelected = { peerId ->
                        navController.navigate(RelayoDestination.chatDetailRoute(peerId))
                    }
                )
            }
            composable(
                route = RelayoDestination.CHAT_DETAIL_ROUTE,
                arguments = listOf(navArgument("peerId") { type = NavType.StringType })
            ) {
                MessagesScreen()
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
                            RelayoDestination.NewsFeed -> Icons.Filled.Campaign
                            RelayoDestination.Alerts -> Icons.Filled.Warning
                            RelayoDestination.QrBoards -> Icons.Filled.QrCodeScanner
                            RelayoDestination.Bridge -> Icons.Filled.Cloud
                        },
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}