package com.red.sovereign.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * قسم في شاشة الإعدادات — عنوان يليه بنوده.
 *
 * تمثيل الأقسام كبيانات (لا نداءات مباشرة) يجعل `LazyColumn` يبني
 * العناصر بتكاسل ويعيد تدويرها، وهو الفارق بين قائمة تعمل وقائمة
 * تبني 45 صفًّا دفعة واحدة في كل إعادة تركيب.
 */
private data class SettingsSection(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<SettingsEntry>
)

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isReadOnly: Boolean = false,
    val isDestructive: Boolean = false
)

/**
 * شاشة إعدادات الجهاز الشاملة، مقسّمة حسب المجال.
 *
 * البنود المعروضة هيكل جاهز للربط: كل بند يستدعي [onNavigate] بمعرّفه
 * حتى تتولّى طبقة التنقّل الوجهة، فلا تُثبَّت المسارات داخل الواجهة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigate: (String) -> Unit = {}
) {
    val sections = rememberSettingsSections()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات الجهاز") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.ObsidianDeep
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEach { section ->
                item(key = "header:${section.title}") {
                    SettingsSectionHeader(
                        title = section.title,
                        icon = section.icon,
                        color = section.color
                    )
                }
                items(
                    count = section.items.size,
                    key = { index -> "${section.title}:${section.items[index].title}" }
                ) { index ->
                    val entry = section.items[index]
                    SettingsItemRow(
                        title = entry.title,
                        subtitle = entry.subtitle,
                        icon = entry.icon,
                        isReadOnly = entry.isReadOnly,
                        isDestructive = entry.isDestructive,
                        onClick = { onNavigate(entry.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSettingsSections(): List<SettingsSection> = listOf(
    SettingsSection(
        title = "الحساب والهوية",
        icon = Icons.Filled.Person,
        color = AqyalGold,
        items = listOf(
            SettingsEntry(
                "الملف الشخصي والهوية",
                "إدارة معرّف يونس والاسم المعروض والصورة",
                Icons.Filled.Person
            ),
            SettingsEntry(
                "هوية الجهاز",
                "شهادات الجهاز والمفاتيح والتصديق",
                Icons.Filled.Fingerprint
            ),
            SettingsEntry(
                "رموز الاسترداد",
                "نسخ احتياطي وإدارة رموز استرداد الحساب",
                Icons.Filled.Key
            ),
            SettingsEntry(
                "الجلسات النشطة",
                "عرض جلسات الدخول النشطة وإبطالها",
                Icons.Filled.MoreVert
            )
        )
    ),
    SettingsSection(
        title = "الهاتف وبوابة DINSTAR",
        icon = Icons.Filled.Call,
        color = YounesEmerald,
        items = listOf(
            SettingsEntry(
                "إعداد الاتصال الهاتفي",
                "بوابة DINSTAR وحالة الشرائح وتوزيع المنافذ",
                Icons.Filled.NetworkCell
            ),
            SettingsEntry(
                "حدود المكالمات والحصص",
                "الحد اليومي وحدود الدقائق وقيود الأرقام",
                Icons.Filled.Call
            ),
            SettingsEntry(
                "تسجيل المكالمات",
                "التسجيل التلقائي والتخزين ومدّة الاحتفاظ",
                Icons.Filled.Mic
            ),
            SettingsEntry(
                "التحويل والبريد الصوتي",
                "التحويل المشروط وإعدادات البريد الصوتي",
                Icons.Filled.CallReceived
            )
        )
    ),
    SettingsSection(
        title = "الشبكة والاتصال",
        icon = Icons.Filled.Wifi,
        color = AqyalGold,
        items = listOf(
            SettingsEntry(
                "عنوان الخادم",
                ServerEndpoint.url(),
                Icons.Filled.NetworkCell,
                isReadOnly = true
            ),
            SettingsEntry(
                "وضع الاتصال",
                "تلقائي / واي فاي فقط / بيانات الجوال / VPN فقط",
                Icons.Filled.Wifi
            ),
            SettingsEntry(
                "إعداد TURN و STUN",
                "خوادم ترحيل الوسائط لـ WebRTC",
                Icons.Filled.SettingsInputComponent
            ),
            SettingsEntry(
                "طابور العمل دون اتصال",
                "حفظ الرسائل والمكالمات عند انقطاع الشبكة",
                Icons.Filled.Sync
            )
        )
    ),
    SettingsSection(
        title = "الأمان والخصوصية",
        icon = Icons.Filled.Security,
        color = Color(0xFFF44336),
        items = listOf(
            SettingsEntry(
                "مفاتيح التشفير",
                "إدارة مفاتيح الجهاز وتدويرها",
                Icons.Filled.Lock
            ),
            SettingsEntry(
                "إشعارات القراءة والكتابة",
                "التحكم في إيصالات القراءة ومؤشّر الكتابة",
                Icons.Filled.Description
            ),
            SettingsEntry(
                "معاينات الروابط",
                "توليد معاينات الروابط داخل المحادثات",
                Icons.Filled.Info
            ),
            SettingsEntry(
                "أمان الشاشة",
                "منع لقطات الشاشة ولوحة مفاتيح متخفّية",
                Icons.Filled.VisibilityOff
            ),
            SettingsEntry(
                "قفل التطبيق",
                "قفل ببصمة أو رمز عند فتح التطبيق",
                Icons.Filled.Fingerprint
            ),
            SettingsEntry(
                "تصدير البيانات وحذفها",
                "تصدير كل البيانات أو حذف الحساب",
                Icons.Filled.Delete,
                isDestructive = true
            )
        )
    ),
    SettingsSection(
        title = "الوسائط والتخزين",
        icon = Icons.Filled.Storage,
        color = Color(0xFF9C27B0),
        items = listOf(
            SettingsEntry(
                "التنزيل التلقائي",
                "الصور والفيديو والمستندات — واي فاي / بيانات / أبدًا",
                Icons.Filled.Download
            ),
            SettingsEntry(
                "استهلاك التخزين",
                "الذاكرة المؤقتة والوسائط وقاعدة البيانات",
                Icons.Filled.Storage
            ),
            SettingsEntry(
                "جودة الوسائط",
                "جودة الصور والفيديو وإعدادات الضغط",
                Icons.Filled.Image
            )
        )
    ),
    SettingsSection(
        title = "الإشعارات",
        icon = Icons.Filled.Notifications,
        color = Color(0xFF673AB7),
        items = listOf(
            SettingsEntry(
                "إشعارات الرسائل",
                "الصوت والاهتزاز والأولوية والفئات",
                Icons.Filled.Notifications
            ),
            SettingsEntry(
                "إشعارات المكالمات",
                "شاشة المكالمة الواردة والاهتزاز والنغمة",
                Icons.Filled.Call
            ),
            SettingsEntry(
                "إشعارات المجموعات والقنوات",
                "الإشارات والردود وتنبيهات المشرفين",
                Icons.Filled.Group
            ),
            SettingsEntry(
                "عدم الإزعاج",
                "الجدولة والاستثناءات والأولوية فقط",
                Icons.Filled.DoNotDisturb
            )
        )
    ),
    SettingsSection(
        title = "المظهر",
        icon = Icons.Filled.BrightnessAuto,
        color = Color(0xFF00BCD4),
        items = listOf(
            SettingsEntry(
                "السمة",
                "النظام / فاتح / داكن / أوليد / مخصّص",
                Icons.Filled.BrightnessAuto
            ),
            SettingsEntry(
                "لون التمييز",
                "اللون الأساسي لعناصر الواجهة",
                Icons.Filled.Palette
            ),
            SettingsEntry(
                "حجم الخط ونمطه",
                "التحجيم وعائلة الخط والوزن",
                Icons.Filled.TextFields
            ),
            SettingsEntry(
                "فقاعات المحادثة",
                "النمط والزوايا والذيول",
                Icons.Filled.Chat
            ),
            SettingsEntry(
                "الحركة والانتقالات",
                "تقليل الحركة وسرعة الانتقالات",
                Icons.Filled.Animation
            )
        )
    ),
    SettingsSection(
        title = "إعدادات المكالمات",
        icon = Icons.Filled.Call,
        color = YounesEmerald,
        items = listOf(
            SettingsEntry(
                "النغمة والاهتزاز",
                "نغمة المكالمة الواردة ونمط الاهتزاز",
                Icons.Filled.Vibration
            ),
            SettingsEntry(
                "الصوت ومكبر الصوت",
                "المكبر التلقائي وإلغاء الضوضاء والصوت عالي الدقة",
                Icons.Filled.VolumeUp
            ),
            SettingsEntry(
                "سجل المكالمات",
                "مدّة الاحتفاظ والتصدير والمزامنة",
                Icons.Filled.History
            ),
            SettingsEntry(
                "الطوارئ SOS",
                "الاتصال السريع ومشاركة الموقع وجهات الطوارئ",
                Icons.Filled.Emergency,
                isDestructive = true
            )
        )
    ),
    SettingsSection(
        title = "متقدّم",
        icon = Icons.Filled.Construction,
        color = Color(0xFF795548),
        items = listOf(
            SettingsEntry(
                "سجلّات التشخيص",
                "تفعيل السجلّات المفصّلة وتصديرها",
                Icons.Filled.BugReport
            ),
            SettingsEntry(
                "تشخيص الشبكة",
                "Ping و traceroute و DNS وإحصاءات WebRTC",
                Icons.Filled.NetworkCheck
            ),
            SettingsEntry(
                "تشخيص WebRTC",
                "مرشّحات ICE و SDP والإحصاءات الداخلية",
                Icons.Filled.DeveloperMode
            ),
            SettingsEntry(
                "الميزات التجريبية",
                "الأعلام التجريبية واختبارات A/B",
                Icons.Filled.Flag
            ),
            SettingsEntry(
                "النسخ الاحتياطي والاستعادة",
                "الإعدادات والمفاتيح والبيانات المحلية",
                Icons.Filled.Backup
            ),
            SettingsEntry(
                "حول التطبيق",
                "الإصدار والبناء والتراخيص",
                Icons.Filled.Info
            )
        )
    )
)

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun SettingsItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isReadOnly: Boolean = false,
    isDestructive: Boolean = false,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    onClick: () -> Unit = {}
) {
    val accent = if (isDestructive) Color(0xFFF44336) else AqyalGold

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReadOnly, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accent
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) {
                    Color(0xFFF44336)
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isReadOnly) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
