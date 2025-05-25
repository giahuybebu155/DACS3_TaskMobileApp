package com.example.taskapplication.ui.team

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.taskapplication.ui.animation.AnimationUtils
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskapplication.ui.team.components.CreateTeamDialog
import com.example.taskapplication.ui.team.components.TeamItem

/**
 * Screen that displays a list of teams the user belongs to
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TeamsScreen(
    viewModel: TeamViewModel = hiltViewModel(),
    onTeamClick: (String) -> Unit,
    onViewInvitations: () -> Unit = {}
) {
    val teamsState by viewModel.teamsState.collectAsState()
    val showCreateTeamDialog by viewModel.showCreateTeamDialog.collectAsState()
    val createTeamState by viewModel.createTeamState.collectAsState()
    val pendingInvitationsCount by viewModel.pendingInvitationsCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error message in snackbar
    LaunchedEffect(teamsState) {
        if (teamsState is TeamsState.Error) {
            snackbarHostState.showSnackbar(
                message = (teamsState as TeamsState.Error).message
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nhóm của bạn",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Invitation button with badge and enhanced styling
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(onClick = onViewInvitations),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingInvitationsCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(
                                            text = if (pendingInvitationsCount > 9) "9+" else pendingInvitationsCount.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Lời mời",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Enhanced floating action button with gradient and shadow
            val fabScale = remember { Animatable(1f) }

            LaunchedEffect(Unit) {
                // Pulsating animation for FAB
                while (true) {
                    fabScale.animateTo(
                        targetValue = 1.05f,
                        animationSpec = tween(
                            durationMillis = 1000,
                            easing = FastOutSlowInEasing
                        )
                    )
                    fabScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 1000,
                            easing = FastOutSlowInEasing
                        )
                    )
                    delay(1000) // Pause between pulses
                }
            }

            // Gradient for FAB
            val fabGradient = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = fabScale.value
                        scaleY = fabScale.value
                    }
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    .clip(CircleShape)
                    .background(fabGradient)
                    .clickable { viewModel.showCreateTeamDialog() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tạo nhóm mới",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            // Loading state with enhanced animation
            AnimatedVisibility(
                visible = teamsState is TeamsState.Loading,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Rotating animation for loading indicator with gradient
                        val rotation = rememberInfiniteTransition().animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "Loading Rotation"
                        )

                        // Pulsating scale for loading indicator
                        val indicatorScale = rememberInfiniteTransition().animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "Indicator Scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .graphicsLayer {
                                    rotationZ = rotation.value
                                    scaleX = indicatorScale.value
                                    scaleY = indicatorScale.value
                                }
                                .shadow(
                                    elevation = 4.dp,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Pulsating text animation with enhanced typography
                        val scale = rememberInfiniteTransition().animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "Text Pulse"
                        )

                        Text(
                            text = "Đang tải danh sách nhóm...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                        )
                    }
                }
            }

            // Empty state with enhanced animation and styling
            AnimatedVisibility(
                visible = teamsState is TeamsState.Empty,
                enter = fadeIn(tween(500)) + expandIn(tween(500), expandFrom = Alignment.Center),
                exit = fadeOut(tween(300)) + shrinkOut(tween(300), shrinkTowards = Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bouncing animation for empty state
                    val bounce = rememberInfiniteTransition().animateFloat(
                        initialValue = 0f,
                        targetValue = 10f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Empty State Bounce"
                    )

                    // Empty state illustration with gradient
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                translationY = bounce.value / 2
                            }
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Bạn chưa có nhóm nào",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer {
                            translationY = bounce.value / 3
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Hãy tạo nhóm mới để bắt đầu làm việc cùng nhau",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.graphicsLayer {
                            translationY = bounce.value / 4
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Enhanced button with gradient background
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = 1f + (bounce.value / 100f)
                                scaleY = 1f + (bounce.value / 100f)
                            }
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                            .clickable { viewModel.showCreateTeamDialog() }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Tạo nhóm mới",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Success state with enhanced team list
            AnimatedVisibility(
                visible = teamsState is TeamsState.Success,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300))
            ) {
                if (teamsState is TeamsState.Success) {
                    val teams = (teamsState as TeamsState.Success).teams

                    // Header with team count
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${teams.size} nhóm",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }

                        // Team list with staggered animation and pull to refresh
                        val refreshState = rememberPullRefreshState(
                            refreshing = teamsState is TeamsState.Loading,
                            onRefresh = { viewModel.refreshTeams() }
                        )

                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pullRefresh(refreshState)
                            ) {
                                itemsIndexed(teams) { index, team ->
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = true,
                                        enter = AnimationUtils.listItemEnterAnimation(index),
                                        exit = AnimationUtils.listItemExitAnimation
                                    ) {
                                        TeamItem(
                                            team = team,
                                            onClick = { onTeamClick(team.id) }
                                        )
                                    }
                                }

                                // Add extra space at bottom for FAB
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }

                            PullRefreshIndicator(
                                refreshing = teamsState is TeamsState.Loading,
                                state = refreshState,
                                modifier = Modifier.align(Alignment.TopCenter),
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Error state with enhanced animation and styling
            AnimatedVisibility(
                visible = teamsState is TeamsState.Error,
                enter = fadeIn(tween(500)) + expandIn(tween(500), expandFrom = Alignment.Center),
                exit = fadeOut(tween(300)) + shrinkOut(tween(300), shrinkTowards = Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shaking animation for error state
                    val shake = rememberInfiniteTransition().animateFloat(
                        initialValue = -5f,
                        targetValue = 5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Error Shake"
                    )

                    // Error illustration
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                translationX = shake.value
                            }
                            .shadow(
                                elevation = 4.dp,
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email, // Sử dụng biểu tượng phù hợp
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Không thể tải danh sách nhóm",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.graphicsLayer {
                            translationX = shake.value / 2
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (teamsState is TeamsState.Error) {
                        Text(
                            text = (teamsState as TeamsState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val pulse = rememberInfiniteTransition().animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Retry Button Pulse"
                    )

                    // Enhanced retry buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Retry button
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pulse.value
                                    scaleY = pulse.value
                                }
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                                .clickable { viewModel.loadTeams() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Thử lại",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Fix display issues button
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pulse.value
                                    scaleY = pulse.value
                                }
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                                .clickable { viewModel.fixTeamDisplayIssues() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sửa lỗi hiển thị",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Force reload button
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pulse.value
                                    scaleY = pulse.value
                                }
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.error,
                                            MaterialTheme.colorScheme.errorContainer
                                        )
                                    )
                                )
                                .clickable { viewModel.forceReloadTeams() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Xóa dữ liệu cục bộ và tải lại",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Show create team dialog with animation
    AnimatedVisibility(
        visible = showCreateTeamDialog,
        enter = AnimationUtils.dialogEnterAnimation,
        exit = AnimationUtils.dialogExitAnimation
    ) {
        CreateTeamDialog(
            createTeamState = createTeamState,
            onDismiss = { viewModel.hideCreateTeamDialog() },
            onCreateTeam = { name, description ->
                viewModel.createTeam(name, description)
            }
        )
    }
}
