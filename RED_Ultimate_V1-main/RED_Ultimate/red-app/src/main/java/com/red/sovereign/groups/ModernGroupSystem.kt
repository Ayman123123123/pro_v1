package com.red.sovereign.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

/**
 * نظام المجموعات الحديث - أفضل من واتساب وتيليجرام
 * 
 * العمل الأساسي والمتعارف للمجموعات:
 * - مجموعة أشخاص يتواصلون معاً في مكان واحد
 * - لكل عضو دور: مالك، مسؤول، مراقب، عضو
 * - مشفرة بـ Sender Keys (مثل Signal) - لا يستطيع الخادم قراءتها
 * - تدعم كل أنواع الرسائل والمكالمات الجماعية
 */

enum class GroupRoleModern(val label: String, val description: String, val permissions: List<String>) {
    OWNER(
        "مالك",
        "مالك المجموعة - كل الصلاحيات",
        listOf("إضافة/إزالة أعضاء", "ترقية أدوار", "حذف المجموعة", "نقل الملكية", "إعدادات", "بدء مكالمات", "تثبيت رسائل", "حذف رسائل الجميع")
    ),
    ADMIN(
        "مسؤول",
        "مسؤول - يدير الأعضاء والمحتوى",
        listOf("إضافة/إزالة أعضاء", "ترقية مراقب/عضو", "إعدادات", "بدء مكالمات", "تثبيت رسائل", "حذف رسائل")
    ),
    MODERATOR(
        "مراقب",
        "مراقب - يشرف على المحتوى",
        listOf("حذف رسائل", "كتم أعضاء", "تثبيت رسائل")
    ),
    MEMBER(
        "عضو",
        "عضو عادي - يشارك في المحادثة",
        listOf("إرسال رسائل", "تفاعلات", "مشاركة وسائط", "انضمام لمكالمات")
    )
}

enum class GroupPrivacy {
    PUBLIC,     // عامة - يمكن لأي أحد الانضمام عبر رابط
    PRIVATE,    // خاصة - تحتاج موافقة
    SECRET      // سرية - دعوة فقط، لا تظهر في البحث
}

data class ModernGroup(
    val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val privacy: GroupPrivacy = GroupPrivacy.PRIVATE,
    val members: List<ModernGroupMember> = emptyList(),
    val memberCount: Int = members.size,
    val maxMembers: Int = 256,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val isEncrypted: Boolean = true,
    val disappearingDuration: Long? = null,
    val onlyAdminsCanPost: Boolean = false,
    val onlyAdminsCanCall: Boolean = false,
    val inviteLink: String? = null,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val unreadCount: Int = 0
)

data class ModernGroupMember(
    val redId: String,
    val username: String,
    val displayName: String,
    val role: GroupRoleModern = GroupRoleModern.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val avatarUrl: String? = null
)

object ModernGroupSystem {
    
    fun getRoleDescription(role: GroupRoleModern): String {
        return "${role.label}: ${role.description} - الصلاحيات: ${role.permissions.joinToString("، ")}"
    }
    
    fun getPrivacyDescription(privacy: GroupPrivacy): String = when (privacy) {
        GroupPrivacy.PUBLIC -> "عامة: يمكن لأي أحد الانضمام عبر رابط الدعوة، تظهر في البحث"
        GroupPrivacy.PRIVATE -> "خاصة: تحتاج موافقة مالك/مسؤول للانضمام، تظهر في البحث"
        GroupPrivacy.SECRET -> "سرية: دعوة فقط من الأعضاء، لا تظهر في البحث، مشفرة بالكامل"
    }
    
    fun getGroupTypeDescription(): String {
        return """
        المجموعة في RED:
        - العمل الأساسي: مجموعة أشخاص يتواصلون معاً في مكان واحد، مثل مجموعة واتساب
        - المتعارف: كل عضو له دور، مشفرة E2EE، تدعم رسائل ووسائط ومكالمات جماعية ترن الجميع
        - التقنية: Sender Keys (مثل Signal) - كل عضو يولد مفتاح إرسال ويشاركه مشفراً مع الأعضاء
        - المسار: إنشاء → توزيع Sender Keys → تشفير جماعي → /ws/master → MongoDB → الأعضاء
        - الواجهة: اسم المرسل بلون + RED ID كامل، منشن @، فقاعات مجمعة، مؤشر كتابة جماعي، قائمة أعضاء مع حالة
        - المميزات: إضافة/إزالة، أدوار، رابط دعوة، طلبات انضمام، كتم، تثبيت، أرشفة، استطلاعات، مكالمات جماعية 32
        """.trimIndent()
    }
    
    fun canPerformAction(memberRole: GroupRoleModern, action: String): Boolean {
        return when (action) {
            "ADD_MEMBER" -> memberRole in listOf(GroupRoleModern.OWNER, GroupRoleModern.ADMIN)
            "REMOVE_MEMBER" -> memberRole in listOf(GroupRoleModern.OWNER, GroupRoleModern.ADMIN)
            "CHANGE_ROLE" -> memberRole == GroupRoleModern.OWNER
            "DELETE_GROUP" -> memberRole == GroupRoleModern.OWNER
            "TRANSFER_OWNERSHIP" -> memberRole == GroupRoleModern.OWNER
            "EDIT_SETTINGS" -> memberRole in listOf(GroupRoleModern.OWNER, GroupRoleModern.ADMIN)
            "START_CALL" -> true // يمكن للجميع بدء مكالمة إلا إذا onlyAdminsCanCall
            "PIN_MESSAGE" -> memberRole in listOf(GroupRoleModern.OWNER, GroupRoleModern.ADMIN, GroupRoleModern.MODERATOR)
            "DELETE_ANY_MESSAGE" -> memberRole in listOf(GroupRoleModern.OWNER, GroupRoleModern.ADMIN, GroupRoleModern.MODERATOR)
            else -> memberRole == GroupRoleModern.MEMBER
        }
    }
    
    fun getNextRole(current: GroupRoleModern): GroupRoleModern = when (current) {
        GroupRoleModern.MEMBER -> GroupRoleModern.MODERATOR
        GroupRoleModern.MODERATOR -> GroupRoleModern.ADMIN
        GroupRoleModern.ADMIN -> GroupRoleModern.MEMBER
        GroupRoleModern.OWNER -> GroupRoleModern.OWNER // المالك لا يتغير إلا بنقل ملكية
    }
}

@Composable
fun ModernGroupCard(
    group: ModernGroup,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = YounesSurface1),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // أفاتار مجموعة
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    group.name.take(1).uppercase(),
                    color = YounesOnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, maxLines = 1)
                    if (group.isEncrypted) {
                        Icon(Icons.Default.Lock, "مشفرة", tint = YounesPrimary, modifier = Modifier.size(14.dp))
                    }
                    if (group.isPinned) {
                        Icon(Icons.Default.Star, "مثبتة", tint = Color(0xFFF5C842), modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    group.description ?: "${group.memberCount} عضو - ${group.privacy.name}",
                    fontSize = 12.sp,
                    color = YounesMuted,
                    maxLines = 1
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (group.unreadCount > 0) {
                    Surface(shape = CircleShape, color = YounesPrimary) {
                        Text("${group.unreadCount}", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = YounesOnPrimary)
                    }
                }
                if (group.isMuted) {
                    Icon(Icons.Default.NotificationsOff, "مكتومة", tint = YounesMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun ModernGroupMemberCard(
    member: ModernGroupMember,
    isMe: Boolean = false,
    onRoleChange: (GroupRoleModern) -> Unit = {},
    onRemove: () -> Unit = {},
    canManage: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = YounesSurface2.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(member.displayName.take(1).uppercase(), color = YounesOnPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (member.isOnline) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00C98C))
                            .align(Alignment.BottomEnd)
                    )
                }
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(member.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    if (isMe) {
                        Surface(shape = RoundedCornerShape(6.dp), color = YounesPrimary.copy(alpha = 0.2f)) {
                            Text("أنت", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 9.sp, color = YounesPrimary)
                        }
                    }
                }
                Text("@${member.username} • ${member.redId.take(12)}...", fontSize = 10.sp, color = YounesCobalt, maxLines = 1)
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (member.role) {
                    GroupRoleModern.OWNER -> YounesAccent.copy(alpha = 0.2f)
                    GroupRoleModern.ADMIN -> YounesPrimary.copy(alpha = 0.2f)
                    GroupRoleModern.MODERATOR -> YounesCobalt.copy(alpha = 0.2f)
                    else -> YounesSurface1
                }
            ) {
                Text(
                    member.role.label,
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (member.role) {
                        GroupRoleModern.OWNER -> YounesAccent
                        GroupRoleModern.ADMIN -> YounesPrimary
                        GroupRoleModern.MODERATOR -> YounesCobalt
                        else -> YounesMuted
                    }
                )
            }
            
            if (canManage && !isMe && member.role != GroupRoleModern.OWNER) {
                IconButton(onClick = { onRoleChange(ModernGroupSystem.getNextRole(member.role)) }, Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, "إدارة", tint = YounesMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ModernGroupInfoScreen(
    group: ModernGroup,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
    onCall: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(YounesMidnight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // رأس
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
            }
            Text("معلومات المجموعة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "تعديل", tint = Color.White)
            }
        }
        
        // معلومات أساسية
        Card(colors = CardDefaults.cardColors(containerColor = YounesSurface1), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(group.name.take(1).uppercase(), color = YounesOnPrimary, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(group.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        Text("${group.memberCount}/${group.maxMembers} عضو", fontSize = 12.sp, color = YounesMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (group.isEncrypted) {
                                Surface(shape = RoundedCornerShape(8.dp), color = YounesPrimary.copy(alpha = 0.15f)) {
                                    Text("🔒 مشفرة E2EE", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = YounesPrimary)
                                }
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = YounesSurface2) {
                                Text(group.privacy.name, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = YounesMuted)
                            }
                        }
                    }
                }
                if (!group.description.isNullOrBlank()) {
                    Text(group.description, fontSize = 14.sp, color = YounesMuted)
                }
            }
        }
        
        // أزرار إجراءات
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCall(false) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = YounesPrimary)) {
                Icon(Icons.Default.Call, "صوتي")
                Spacer(Modifier.width(6.dp))
                Text("صوتية")
            }
            Button(onClick = { onCall(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = YounesCobalt)) {
                Icon(Icons.Default.Videocam, "فيديو")
                Spacer(Modifier.width(6.dp))
                Text("فيديو")
            }
            OutlinedButton(onClick = onInvite, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Link, "دعوة")
                Spacer(Modifier.width(6.dp))
                Text("دعوة")
            }
        }
        
        // أعضاء
        Text("الأعضاء (${group.members.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        group.members.forEach { member ->
            ModernGroupMemberCard(member = member, isMe = false, canManage = true)
            Spacer(Modifier.height(8.dp))
        }
        
        Spacer(Modifier.weight(1f))
        
        // مغادرة
        OutlinedButton(
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))
        ) {
            Icon(Icons.Default.ExitToApp, "مغادرة")
            Spacer(Modifier.width(8.dp))
            Text("مغادرة المجموعة")
        }
    }
}
