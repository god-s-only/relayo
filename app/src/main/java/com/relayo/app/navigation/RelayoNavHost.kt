package com.relayo.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import com.relayo.app.session.AppSessionViewModel
import com.relayo.feature.alerts.AlertsScreen
import com.relayo.feature.bridge.BridgeScreen
import com.relayo.feature.messages.ConversationsScreen
import com.relayo.feature.messages.MessagesScreen
import com.relayo.feature.meshstatus.MeshStatusScreen
import com.relayo.feature.newsfeed.NewsFeedScreen
import com.relayo.feature.qrboards.QrBoardsScreen
import com.relayo.feature.voicenotes.VoiceNoteConversationsScreen
import com.relayo.feature.voicenotes.VoiceNotesScreen

@OptIn(ExperimentalMaterial3Api::class)
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = RelayoDestination.bottomNavItems.find { it.route == currentRoute }
        ?: RelayoDestination.moreItems.find { it.route == currentRoute }
        ?: RelayoDestination.MeshStatus

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Relayo",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = currentDestination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
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
            composable(RelayoDestination.More.route) {
                MoreScreen(
                    onNavigate = { dest -> navController.navigate(dest.route) }
                )
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
private fun MoreScreen(
    onNavigate:(RelayoDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "More",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Additional mesh features",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        RelayoDestination.moreItems.forEach { dest ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate(dest) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = when(dest) {
                            RelayoDestination.Alerts -> Icons.Filled.Warning
                            RelayoDestination.QrBoards -> Icons.Filled.QrCodeScanner
                            RelayoDestination.Bridge -> Icons.Filled.Cloud
                            else -> Icons.Filled.MoreHoriz
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when(dest) {
                                RelayoDestination.Alerts -> "Urgent safety broadcasts"
                                RelayoDestination.QrBoards -> "QR shared boards"
                                RelayoDestination.Bridge -> "Ask the internet via mesh"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayoBottomBar(navController:androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        RelayoDestination.bottomNavItems.forEach { destination ->
            val isSelected = currentRoute == destination.route ||
                (destination == RelayoDestination.More && RelayoDestination.moreItems.any { it.route == currentRoute })
            NavigationBarItem(
                selected = isSelected,
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
                            RelayoDestination.More -> Icons.Filled.MoreHoriz
                            else -> Icons.Filled.MoreHoriz
                        },
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}
