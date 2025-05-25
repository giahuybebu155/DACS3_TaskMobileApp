package com.example.taskapplication.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taskapplication.ui.auth.AuthScreen
import com.example.taskapplication.ui.components.SyncStatusIndicator
import com.example.taskapplication.ui.theme.TaskApplicationTheme
import com.example.taskapplication.workers.InvitationNotificationWorker
import com.example.taskapplication.workers.NotificationWorker
import com.example.taskapplication.workers.SyncWorker
import com.example.taskapplication.workers.TeamSyncWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val syncViewModel: MainSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationWorker.createNotificationChannel(this)
            InvitationNotificationWorker.createNotificationChannel(this)
            TeamSyncWorker.createNotificationChannel(this)
        }

        // Schedule periodic workers
        InvitationNotificationWorker.schedulePeriodicInvitationCheck(this)
        TeamSyncWorker.schedulePeriodicTeamSync(this)
        SyncWorker.schedulePeriodicSync(this)

        // Check for invitation notification intent
        intent?.getBooleanExtra("OPEN_INVITATIONS", false)?.let { openInvitations ->
            if (openInvitations) {
                viewModel.setOpenInvitationsScreen(true)
            }
        }

        setContent {
            TaskApplicationTheme {
                val navController = rememberNavController()
                val isLoggedIn by viewModel.isLoggedIn.collectAsState()

                // Collect sync state
                val syncState by syncViewModel.syncState.collectAsState()
                val webSocketState by syncViewModel.webSocketState.collectAsState()
                val networkAvailable by syncViewModel.networkAvailable.collectAsState()
                val lastSyncTime by syncViewModel.lastSyncTime.collectAsState()

                Scaffold { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Show sync status indicator only when logged in
                            if (isLoggedIn) {
                                SyncStatusIndicator(
                                    syncState = syncState,
                                    webSocketState = webSocketState,
                                    isNetworkAvailable = networkAvailable,
                                    lastSyncTime = lastSyncTime,
                                    onSyncClick = { syncViewModel.syncData() },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            NavHost(
                                navController = navController,
                                startDestination = if (isLoggedIn) "main" else "auth"
                            ) {
                                composable("auth") {
                                    AuthScreen(
                                        onLoginSuccess = {
                                            navController.navigate("main") {
                                                popUpTo("auth") { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable("main") {
                                    MainScreen(
                                        onLogout = {
                                            viewModel.logout()
                                            navController.navigate("auth") {
                                                popUpTo("main") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
