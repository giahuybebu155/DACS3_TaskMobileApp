package com.example.taskapplication.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskapplication.ui.team.detail.InvitationsState
import com.example.taskapplication.ui.team.detail.InviteState
import com.example.taskapplication.ui.team.detail.RemoveMemberState
import com.example.taskapplication.ui.team.detail.ResendInvitationState
import com.example.taskapplication.ui.team.detail.RoleChangeState
import com.example.taskapplication.ui.team.detail.TeamDetailState
import com.example.taskapplication.ui.team.detail.TeamDetailViewModel
import com.example.taskapplication.ui.team.detail.TeamMembersState
import com.example.taskapplication.ui.team.detail.components.EnhancedInviteMemberDialog
import com.example.taskapplication.ui.team.detail.components.InvitationsList
import com.example.taskapplication.ui.team.detail.components.TeamMemberItem

/**
 * Screen that displays team details and members
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    viewModel: TeamDetailViewModel = hiltViewModel(),
    teamId: String,
    onBackClick: () -> Unit,
    onChatClick: () -> Unit,
    onTasksClick: () -> Unit,
    onDocumentsClick: () -> Unit,
    onViewRoleHistory: (String, String?) -> Unit
) {
    val teamState by viewModel.teamState.collectAsState()
    val membersState by viewModel.membersState.collectAsState()
    val inviteState by viewModel.inviteState.collectAsState()
    val showInviteDialog by viewModel.showInviteDialog.collectAsState()
    val roleChangeState by viewModel.roleChangeState.collectAsState()
    val removeMemberState by viewModel.removeMemberState.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val isCurrentUserAdmin by viewModel.isCurrentUserAdmin.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val suggestedUsers by viewModel.suggestedUsers.collectAsState()
    val suggestedUsersState by viewModel.suggestedUsersState.collectAsState()
    val pendingInvitations by viewModel.pendingInvitations.collectAsState()
    val invitationsState by viewModel.invitationsState.collectAsState()
    val resendInvitationState by viewModel.resendInvitationState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error message in snackbar
    LaunchedEffect(teamState, membersState, roleChangeState, removeMemberState) {
        if (teamState is TeamDetailState.Error) {
            snackbarHostState.showSnackbar(
                message = (teamState as TeamDetailState.Error).message
            )
        } else if (membersState is TeamMembersState.Error) {
            snackbarHostState.showSnackbar(
                message = (membersState as TeamMembersState.Error).message
            )
        } else if (roleChangeState is RoleChangeState.Error) {
            snackbarHostState.showSnackbar(
                message = (roleChangeState as RoleChangeState.Error).message
            )
        } else if (removeMemberState is RemoveMemberState.Error) {
            snackbarHostState.showSnackbar(
                message = (removeMemberState as RemoveMemberState.Error).message
            )
        }
    }

    // Show success messages
    LaunchedEffect(roleChangeState, removeMemberState) {
        if (roleChangeState is RoleChangeState.Success) {
            snackbarHostState.showSnackbar(
                message = "Member role updated successfully"
            )
            viewModel.resetError()
        } else if (removeMemberState is RemoveMemberState.Success) {
            snackbarHostState.showSnackbar(
                message = "Member removed successfully"
            )
            viewModel.resetError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (teamState is TeamDetailState.Success) {
                        Text((teamState as TeamDetailState.Success).team.name)
                    } else {
                        Text("Team Details")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (teamState is TeamDetailState.Success) {
                FloatingActionButton(
                    onClick = { viewModel.showInviteDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Invite Member",
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (teamState) {
                is TeamDetailState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is TeamDetailState.Success -> {
                    val team = (teamState as TeamDetailState.Success).team

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Team info card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = team.name,
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                    }

                                    team.description?.let {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = onChatClick,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Chat")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = onTasksClick,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Task, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tasks")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = onDocumentsClick,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Docs")
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Team members section header
                            Text(
                                text = "Team Members",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Team members list
                        when (membersState) {
                            is TeamMembersState.Loading -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            is TeamMembersState.Empty -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No team members yet. Invite someone to join!",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            is TeamMembersState.Success -> {
                                val members = (membersState as TeamMembersState.Success).members
                                items(members) { member ->
                                    TeamMemberItem(
                                        member = member,
                                        currentUserId = currentUserId ?: "",
                                        isCurrentUserAdmin = isCurrentUserAdmin,
                                        onChangeRole = { userId, newRole ->
                                            viewModel.changeUserRole(userId, newRole)
                                        },
                                        onRemoveMember = { userId ->
                                            viewModel.removeUserFromTeam(userId)
                                        },
                                        onViewRoleHistory = { userId ->
                                            onViewRoleHistory(teamId, userId)
                                        }
                                    )
                                    Divider()
                                }
                            }

                            is TeamMembersState.Error -> {
                                // Error is shown in snackbar
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(onClick = { viewModel.loadTeamMembers() }) {
                                            Text("Retry loading members")
                                        }
                                    }
                                }
                            }
                        }

                        // Pending invitations section
                        if (isCurrentUserAdmin) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))

                                InvitationsList(
                                    invitations = pendingInvitations,
                                    invitationsState = invitationsState,
                                    resendInvitationState = resendInvitationState,
                                    onResend = { invitationId -> viewModel.resendInvitation(invitationId) },
                                    onCancel = { invitationId -> viewModel.cancelInvitation(invitationId) },
                                    onRefresh = { viewModel.refreshInvitations() },
                                    onResetResendState = { viewModel.resetResendInvitationState() }
                                )
                            }
                        }
                    }
                }

                is TeamDetailState.Error -> {
                    // Error is shown in snackbar
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Failed to load team details",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { viewModel.loadTeam() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }

    // Show enhanced invite member dialog
    if (showInviteDialog) {
        EnhancedInviteMemberDialog(
            inviteState = inviteState,
            searchState = searchState,
            searchResults = searchResults,
            suggestedUsersState = suggestedUsersState,
            suggestedUsers = suggestedUsers,
            onDismiss = { viewModel.hideInviteDialog() },
            onInvite = { email -> viewModel.inviteUserToTeam(email) },
            onSearch = { query -> viewModel.searchUsers(query) },
            onClearSearch = { viewModel.clearSearchResults() },
            onRefreshSuggestions = { viewModel.refreshSuggestedUsers() }
        )
    }
}
