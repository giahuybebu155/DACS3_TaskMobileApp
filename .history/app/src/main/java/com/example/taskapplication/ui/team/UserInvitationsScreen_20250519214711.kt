package com.example.taskapplication.ui.team

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskapplication.data.websocket.ConnectionState
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.ui.team.detail.InvitationsState
import com.example.taskapplication.ui.team.detail.components.UserInvitationItem

/**
 * Màn hình hiển thị danh sách lời mời của người dùng
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInvitationsScreen(
    onBackClick: () -> Unit,
    viewModel: UserInvitationsViewModel = hiltViewModel()
) {
    val invitations by viewModel.invitations.collectAsState()
    val invitationsState by viewModel.invitationsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionResult by viewModel.actionResult.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // Show snackbar for action results
    LaunchedEffect(actionResult) {
        actionResult?.let { result ->
            snackbarHostState.showSnackbar(result)
            viewModel.clearActionResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lời mời tham gia team") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshInvitations() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        InvitationsContent(
            invitations = invitations,
            invitationsState = invitationsState,
            onAccept = { viewModel.acceptInvitation(it) },
            onReject = { viewModel.rejectInvitation(it) },
            onRefresh = { viewModel.refreshInvitations() },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Nội dung hiển thị danh sách lời mời
 */
@Composable
private fun InvitationsContent(
    invitations: List<TeamInvitation>,
    invitationsState: InvitationsState,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (invitationsState) {
            is InvitationsState.Loading -> {
                CircularProgressIndicator()
            }

            is InvitationsState.Empty -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "No Invitations",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Bạn không có lời mời nào",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            is InvitationsState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "Lời mời đang chờ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(invitations) { invitation ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300))
                        ) {
                            UserInvitationItem(
                                invitation = invitation,
                                onAccept = { onAccept(invitation.id) },
                                onReject = { onReject(invitation.id) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            is InvitationsState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = invitationsState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thử lại")
                    }
                }
            }
        }
    }
}
