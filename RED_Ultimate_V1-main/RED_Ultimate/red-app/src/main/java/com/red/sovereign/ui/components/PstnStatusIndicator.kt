package com.red.sovereign.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.PstnWebRtcManager
import com.red.sovereign.calls.YemeniOperatorDetector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SettingsInputComponent

/**
 * مؤشر حالة PSTN — يعرض حالة الاتصال بالبوابة والمشغل الحالي.
 * يُستخدم عادة في لوحة الاتصال (DialPad) ليعرف المستخدم حالة الخط.
 */
@Composable
fun PstnStatusIndicator(
    targetNumber: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pstnManager = remember { PstnWebRtcManager.incoming(context) }
    val pstnState by pstnManager.stateFlow.collectAsState()

    val operatorInfo = remember(targetNumber) {
        YemeniOperatorDetector.getOperatorInfo(targetNumber)
    }

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. مؤشر حالة الاتصال بالبوابة (WebRTC/SIP)
        val statusColor = when (pstnState) {
            PstnWebRtcManager.PstnCallState.ACTIVE -> Color(0xFF4CAF50) // Green
            PstnWebRtcManager.PstnCallState.REGISTERING,
            PstnWebRtcManager.PstnCallState.BRIDGING -> Color(0xFFFFC107) // Amber
            PstnWebRtcManager.PstnCallState.ERROR -> Color(0xFFF44336) // Red
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )

        // 2. أيقونة واسم المشغل (إن وُجد)
        if (operatorInfo != null) {
            Icon(
                imageVector = Icons.Rounded.NetworkCell,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = operatorInfo.brandColor
            )
            Text(
                text = operatorInfo.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = operatorInfo.brandColor
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.SettingsInputComponent,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "بوابة DINSTAR",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
