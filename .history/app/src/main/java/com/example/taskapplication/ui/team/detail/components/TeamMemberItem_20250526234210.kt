package com.example.taskapplication.ui.team.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.example.taskapplication.domain.model.TeamMember
import com.example.taskapplication.ui.components.ConfirmationDialog
import com.example.taskapplication.ui.team.detail.EnhancedTeamMember

/**
 * Item that displays team member information
 */
@Composable
fun TeamMemberItem(
    enhancedMember: EnhancedTeamMember,
    currentUserId: String,
    isCurrentUserAdmin: Boolean,
    onChangeRole: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onViewRoleHistory: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val member = enhancedMember.member
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (enhancedMember.userName.isNotEmpty()) {
                    Text(
                        text = enhancedMember.userName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Member info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // ✅ DISPLAY USER NAME INSTEAD OF ID
                Text(
                    text = if (enhancedMember.userName.isNotEmpty() && enhancedMember.userName != "Unknown User") {
                        enhancedMember.userName
                    } else if (enhancedMember.userEmail.isNotEmpty() && enhancedMember.userEmail != "unknown@email.com") {
                        enhancedMember.userEmail
                    } else {
                        member.invitedBy?.let { "Invited by $it" } ?: "Pending Invitation"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                // Show email as secondary text if we have both name and email
                if (enhancedMember.userName.isNotEmpty() &&
                    enhancedMember.userName != "Unknown User" &&
                    enhancedMember.userEmail.isNotEmpty() &&
                    enhancedMember.userEmail != "unknown@email.com") {
                    Text(
                        text = enhancedMember.userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = member.role.capitalize(Locale.current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status indicator for pending invitations
            if (member.syncStatus.contains("pending")) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Menu for admin actions (only visible for admins and not for themselves)
            if (isCurrentUserAdmin && member.userId != currentUserId) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Change role option
                        val newRole = if (member.role == "admin") "member" else "admin"
                        val roleText = if (member.role == "admin") "Demote to Member" else "Promote to Admin"

                        DropdownMenuItem(
                            text = { Text(roleText) },
                            onClick = {
                                if (member.role == "admin") {
                                    // Show confirmation dialog for demoting admin
                                    val displayName = if (enhancedMember.userName.isNotEmpty() && enhancedMember.userName != "Unknown User") {
                                        enhancedMember.userName
                                    } else {
                                        enhancedMember.userEmail
                                    }
                                    confirmTitle = "Demote Admin"
                                    confirmMessage = "Are you sure you want to demote $displayName from admin to member? This will remove their administrative privileges."
                                    confirmAction = { onChangeRole(member.userId, newRole) }
                                    showConfirmDialog = true
                                } else {
                                    // Promote to admin without confirmation
                                    onChangeRole(member.userId, newRole)
                                }
                                showMenu = false
                            }
                        )

                        // View role history option
                        if (onViewRoleHistory != null) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("View Role History")
                                    }
                                },
                                onClick = {
                                    onViewRoleHistory(member.userId)
                                    showMenu = false
                                }
                            )
                        }

                        // Remove member option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Remove from Team",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            onClick = {
                                // Show confirmation dialog for removing member
                                val displayName = if (enhancedMember.userName.isNotEmpty() && enhancedMember.userName != "Unknown User") {
                                    enhancedMember.userName
                                } else {
                                    enhancedMember.userEmail
                                }
                                confirmTitle = "Remove Member"
                                confirmMessage = "Are you sure you want to remove $displayName from the team? This action cannot be undone."
                                confirmAction = { onRemoveMember(member.userId) }
                                showConfirmDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Show confirmation dialog if needed
    if (showConfirmDialog && confirmAction != null) {
        ConfirmationDialog(
            title = confirmTitle,
            message = confirmMessage,
            confirmButtonText = "Confirm",
            onConfirm = {
                confirmAction?.invoke()
                showConfirmDialog = false
            },
            onDismiss = {
                showConfirmDialog = false
            }
        )
    }
}
