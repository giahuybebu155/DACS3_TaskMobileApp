package com.example.taskapplication.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.taskapplication.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.taskapplication.ui.personal.PersonalTaskDetailScreen
import com.example.taskapplication.ui.personal.PersonalTasksScreen
import com.example.taskapplication.ui.profile.ProfileScreen
import com.example.taskapplication.ui.team.TeamDetailScreen
import com.example.taskapplication.ui.team.TeamsScreen
import com.example.taskapplication.ui.team.UserInvitationsScreen
import com.example.taskapplication.ui.team.chat.ChatScreen
import com.example.taskapplication.ui.team.detail.TeamRoleHistoryScreen
import com.example.taskapplication.ui.team.document.DocumentsScreen
import com.example.taskapplication.ui.team.document.DocumentDetailScreen
import com.example.taskapplication.ui.team.task.TeamTaskDetailScreen
import com.example.taskapplication.ui.team.task.TeamTasksScreen

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val openInvitationsScreen by viewModel.openInvitationsScreen.collectAsState()

    // Trạng thái kết nối
    val isOnline by viewModel.isOnline.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingChangesCount by viewModel.pendingChangesCount.collectAsState()

    // Handle navigation to invitations screen if needed
    LaunchedEffect(openInvitationsScreen) {
        if (openInvitationsScreen) {
            // Navigate to teams screen first
            navController.navigate("teams") {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }

            // Mark as handled
            viewModel.markInvitationsHandled()
        }
    }

    Scaffold(
        bottomBar = {
            MainBottomNavigation(navController)
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = "personal_tasks",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("personal_tasks") {
                    PersonalTasksScreen(
                        onTaskClick = { taskId ->
                            navController.navigate("personal_tasks/$taskId")
                        }
                    )
                }

                composable("personal_tasks/{taskId}") { backStackEntry ->
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                    PersonalTaskDetailScreen(
                        taskId = taskId,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("teams") {
                    TeamsScreen(
                        onTeamClick = { teamId ->
                            navController.navigate("teams/$teamId")
                        },
                        onViewInvitations = {
                            navController.navigate("user_invitations")
                        }
                    )
                }

                composable("user_invitations") {
                    UserInvitationsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("teams/{teamId}") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    TeamDetailScreen(
                        teamId = teamId,
                        onBackClick = { navController.popBackStack() },
                        onChatClick = { navController.navigate("teams/$teamId/chat") },
                        onTasksClick = { navController.navigate("teams/$teamId/tasks") },
                        onDocumentsClick = { navController.navigate("teams/$teamId/documents") },
                        onViewRoleHistory = { teamId, userId ->
                            if (userId != null) {
                                navController.navigate("teams/$teamId/role_history?userId=$userId")
                            } else {
                                navController.navigate("teams/$teamId/role_history")
                            }
                        }
                    )
                }

                composable("teams/{teamId}/role_history") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    TeamRoleHistoryScreen(
                        teamId = teamId,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("teams/{teamId}/chat") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    ChatScreen(
                        teamId = teamId,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("teams/{teamId}/tasks") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    TeamTasksScreen(
                        teamId = teamId,
                        onBackClick = { navController.popBackStack() },
                        onTaskClick = { taskId ->
                            navController.navigate("teams/$teamId/tasks/$taskId")
                        }
                    )
                }

                composable("teams/{teamId}/tasks/{taskId}") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                    TeamTaskDetailScreen(
                        teamId = teamId,
                        taskId = taskId,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("teams/{teamId}/documents") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    DocumentsScreen(
                        navController = navController,
                        teamId = teamId
                    )
                }

                composable("document_detail/{teamId}/{documentId}") { backStackEntry ->
                    val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                    val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                    DocumentDetailScreen(
                        navController = navController,
                        documentId = documentId
                    )
                }

                composable("profile") {
                    ProfileScreen(
                        onLogoutClick = onLogout
                    )
                }
            }
        }
    }
}

@Composable
fun MainBottomNavigation(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("personal_tasks", stringResource(id = R.string.nav_personal_tasks), Icons.Default.CheckCircle),
        BottomNavItem("teams", stringResource(id = R.string.nav_teams), Icons.Default.List),
        BottomNavItem("profile", stringResource(id = R.string.nav_profile), Icons.Default.Person)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
