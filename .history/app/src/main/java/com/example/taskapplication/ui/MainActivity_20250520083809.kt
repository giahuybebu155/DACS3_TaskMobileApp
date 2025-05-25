package com.example.taskapplication.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taskapplication.ui.auth.AuthScreen
import com.example.taskapplication.ui.theme.TaskApplicationTheme
import com.example.taskapplication.workers.InvitationNotificationWorker
import com.example.taskapplication.workers.NotificationWorker
import com.example.taskapplication.workers.TeamSyncWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationWorker.createNotificationChannel(this)
            InvitationNotificationWorker.createNotificationChannel(this)
        }

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
