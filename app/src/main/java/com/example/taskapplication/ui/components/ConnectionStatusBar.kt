package com.example.taskapplication.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.taskapplication.data.network.ConnectionStateManager

/**
 * Hiển thị thanh trạng thái kết nối
 */
@Composable
fun ConnectionStatusBar(
    isOnline: Boolean,
    syncState: ConnectionStateManager.SyncState,
    pendingChangesCount: Int,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline || syncState == ConnectionStateManager.SyncState.ERROR || pendingChangesCount > 0,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val backgroundColor = when {
            !isOnline -> Color(0xFFB71C1C) // Red
            syncState == ConnectionStateManager.SyncState.ERROR -> Color(0xFFF57F17) // Amber
            pendingChangesCount > 0 -> Color(0xFF1565C0) // Blue
            else -> Color.Transparent
        }
        
        val message = when {
            !isOnline -> "Offline - Thay đổi sẽ được đồng bộ khi có kết nối"
            syncState == ConnectionStateManager.SyncState.ERROR -> "Lỗi đồng bộ hóa - Nhấn để thử lại"
            syncState == ConnectionStateManager.SyncState.SYNCING -> "Đang đồng bộ hóa..."
            pendingChangesCount > 0 -> "Có $pendingChangesCount thay đổi chưa đồng bộ - Nhấn để đồng bộ ngay"
            else -> ""
        }
        
        val icon = when {
            !isOnline -> Icons.Default.CloudOff
            syncState == ConnectionStateManager.SyncState.ERROR -> Icons.Default.Refresh
            syncState == ConnectionStateManager.SyncState.SYNCING -> Icons.Default.Sync
            pendingChangesCount > 0 -> Icons.Default.Sync
            else -> null
        }
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(
                    enabled = isOnline && (syncState == ConnectionStateManager.SyncState.ERROR || pendingChangesCount > 0),
                    onClick = onSyncClick
                )
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
