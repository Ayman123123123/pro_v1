package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.RichMessage
import com.red.sovereign.crypto.DecryptedMessage
import java.text.SimpleDateFormat
import java.util.*

data class RecipientDeliveryStatus(
    val recipientId: String,
    val recipientName: String,
    val status: String,
    val timestamp: Long? = null
)

data class MessageInfoWithRecipients(
    val message: DecryptedMessage,
    val isEdited: Boolean = false,
    val recipients: List<RecipientDeliveryStatus> = emptyList(),
    val isGroupMessage: Boolean = false
)

@Composable
fun EnhancedMessageInfoDialog(
    info: MessageInfoWithRecipients,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val richInfo = if (info.message.type == "RICH_TEXT") RichMessage.decode(info.message.plaintext) else null
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("معلومات الرسالة")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DashboardMessageInfoRow(
                            "المرسل",
                            if (info.message.outgoing) "أنت" else info.message.senderRedId
                        )
                        DashboardMessageInfoRow(
                            "النوع",
                            when (info.message.type) {
                                "RICH_TEXT" -> "نص غني"
                                "VOICE" -> "رسالة صوتية"
                                "STICKER" -> "ملصق"
                                "IMAGE" -> "صورة"
                                "VIDEO" -> "فيديو"
                                "AUDIO" -> "صوت"
                                "FILE" -> "ملف"
                                else -> info.message.type
                            }
                        )
                        DashboardMessageInfoRow(
                            "الوقت",
                            dateFormat.format(Date(info.message.timestamp))
                        )
                        DashboardMessageInfoRow(
                            "الحالة",
                            when (info.message.status) {
                                "READ" -> "مقروءة ✓✓"
                                "DELIVERED" -> "وصلت ✓✓"
                                else -> "أُرسلت ✓"
                            }
                        )
                        if (info.isEdited) DashboardMessageInfoRow("تعديل", "نعم")
                        if (richInfo?.forwardOf != null) DashboardMessageInfoRow("إعادة توجيه", "نعم")
                        if (richInfo?.replyTo != null) {
                            DashboardMessageInfoRow("رد على", richInfo.replyTo.take(12))
                        }
                        if (richInfo?.expiresAt != null) DashboardMessageInfoRow("رسالة مؤقتة", "نعم")
                        DashboardMessageInfoRow("المعرّف", info.message.id.take(16))
                    }
                }
                
                if (info.isGroupMessage && info.recipients.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "حالة التسليم لكل مستلم",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            DeliveryStatsBadge(
                                delivered = info.recipients.count { it.status == "DELIVERED" || it.status == "READ" },
                                read = info.recipients.count { it.status == "READ" },
                                total = info.recipients.size
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(info.recipients) { recipient ->
                        RecipientStatusItem(
                            recipient = recipient,
                            dateFormat = dateFormat
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
fun RecipientStatusItem(
    recipient: RecipientDeliveryStatus,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = recipient.recipientName.take(2).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipient.recipientName,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = recipient.recipientId,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (recipient.status) {
                    "READ" -> {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "مقروءة",
                            fontSize = 12.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    "DELIVERED" -> {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "وصلت",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "SENT" -> {
                        Icon(
                            Icons.Rounded.Done,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "أُرسلت",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "قيد الإرسال",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            recipient.timestamp?.let { timestamp ->
                Text(
                    text = dateFormat.format(Date(timestamp)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DeliveryStatsBadge(
    delivered: Int,
    read: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Group,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$delivered/$total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            if (read > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = read.toString(),
                    fontSize = 10.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun MessageInfoTab(
    message: DecryptedMessage,
    recipients: List<RecipientDeliveryStatus>,
    isGroupMessage: Boolean,
    isEdited: Boolean,
    onDismiss: () -> Unit
) {
    var showEnhancedInfo by remember { mutableStateOf(false) }
    
    if (showEnhancedInfo) {
        EnhancedMessageInfoDialog(
            info = MessageInfoWithRecipients(
                message = message,
                isEdited = isEdited,
                recipients = recipients,
                isGroupMessage = isGroupMessage
            ),
            onDismiss = { showEnhancedInfo = false }
        )
    }
    
    Button(
        onClick = { showEnhancedInfo = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("معلومات الرسالة")
    }
}
