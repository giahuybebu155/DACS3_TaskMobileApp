package com.example.taskapplication.ui.team.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge component để hiển thị trạng thái lời mời
 * Theo design mới từ API documentation
 */
@Composable
fun InvitationStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, displayText) = when (status.lowercase()) {
        "pending" -> Triple(
            Color(0xFFFFF3CD), // Light yellow background
            Color(0xFF856404), // Dark yellow text
            "Đang chờ"
        )
        "accepted" -> Triple(
            Color(0xFFD4EDDA), // Light green background
            Color(0xFF155724), // Dark green text
            "Đã chấp nhận"
        )
        "rejected" -> Triple(
            Color(0xFFF8D7DA), // Light red background
            Color(0xFF721C24), // Dark red text
            "Đã từ chối"
        )
        "cancelled" -> Triple(
            Color(0xFFE2E3E5), // Light gray background
            Color(0xFF383D41), // Dark gray text
            "Đã hủy"
        )
        "expired" -> Triple(
            Color(0xFFFCF8E3), // Light orange background
            Color(0xFF8A6D3B), // Dark orange text
            "Đã hết hạn"
        )
        else -> Triple(
            Color(0xFFE2E3E5), // Default gray
            Color(0xFF383D41),
            status.replaceFirstChar { it.uppercase() }
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Badge component cho role
 */
@Composable
fun InvitationRoleBadge(
    role: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, displayText) = when (role.lowercase()) {
        "manager" -> Triple(
            Color(0xFFE3F2FD), // Light blue background
            Color(0xFF1565C0), // Dark blue text
            "Quản lý"
        )
        "member" -> Triple(
            Color(0xFFF3E5F5), // Light purple background
            Color(0xFF7B1FA2), // Dark purple text
            "Thành viên"
        )
        "owner" -> Triple(
            Color(0xFFFFF3E0), // Light orange background
            Color(0xFFE65100), // Dark orange text
            "Chủ sở hữu"
        )
        else -> Triple(
            Color(0xFFE2E3E5), // Default gray
            Color(0xFF383D41),
            role.replaceFirstChar { it.uppercase() }
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Badge component cho sync status
 */
@Composable
fun InvitationSyncBadge(
    syncStatus: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, displayText) = when (syncStatus.lowercase()) {
        "synced" -> Triple(
            Color(0xFFD4EDDA), // Light green
            Color(0xFF155724), // Dark green
            "Đã đồng bộ"
        )
        "pending_create" -> Triple(
            Color(0xFFFFF3CD), // Light yellow
            Color(0xFF856404), // Dark yellow
            "Chờ tạo"
        )
        "pending_update" -> Triple(
            Color(0xFFE3F2FD), // Light blue
            Color(0xFF1565C0), // Dark blue
            "Chờ cập nhật"
        )
        "pending_delete" -> Triple(
            Color(0xFFF8D7DA), // Light red
            Color(0xFF721C24), // Dark red
            "Chờ xóa"
        )
        "error" -> Triple(
            Color(0xFFF8D7DA), // Light red
            Color(0xFF721C24), // Dark red
            "Lỗi"
        )
        else -> Triple(
            Color(0xFFE2E3E5), // Default gray
            Color(0xFF383D41),
            syncStatus.replaceFirstChar { it.uppercase() }
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InvitationStatusBadgePreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            InvitationStatusBadge("pending")
            InvitationStatusBadge("accepted")
            InvitationStatusBadge("rejected")
            InvitationStatusBadge("cancelled")
            InvitationStatusBadge("expired")
            
            InvitationRoleBadge("manager")
            InvitationRoleBadge("member")
            InvitationRoleBadge("owner")
            
            InvitationSyncBadge("synced")
            InvitationSyncBadge("pending_create")
            InvitationSyncBadge("pending_update")
            InvitationSyncBadge("error")
        }
    }
}
