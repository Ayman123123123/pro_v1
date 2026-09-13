package com.red.sovereign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.red.sovereign.R
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.core.outbox.OutboxRetryWorker
import com.red.sovereign.ui.theme.AqyalGold

/**
 * تفكيك RedDashboard الوحش (~3970 سطر): بانر الطابور دون اتصال — نُقل كاملاً من
 * RedDashboard.kt (كان private OfflineOutboxBanner) إلى هنا بصيغة Dashboard* الداخلية.
 * RedDashboard يحتفظ بممرر OfflineOutboxBanner(onOpenQueue) ينادي هذه الدالة فقط.
 *
 * السلوك: يراقب outbox_messages عبر OutboxDao.observePendingCount ويظهر فقط عند
 * وجود رسائل معلقة — نص عربي من strings.xml (offline_banner_text) + زر عرض
 * يفتح OfflineQueueScreen + زر إعادة المحاولة يجدول OutboxRetryWorker الحقيقي.
 */
@Composable
internal fun DashboardOfflineOutboxBanner(onOpenQueue: () -> Unit = {}) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val dao = remember(appContext) { RedDatabase.getInstance(appContext).outboxDao() }
    val pending by dao.observePendingCount().collectAsState(initial = 0)
    if (pending > 0) {
        Surface(
            color = AqyalGold.copy(alpha = 0.16f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.offline_banner_text, pending),
                    style = MaterialTheme.typography.labelLarge,
                    color = AqyalGold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenQueue) {
                    Text("عرض", color = AqyalGold, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { OutboxRetryWorker.schedule(appContext) }) {
                    Text(stringResource(R.string.offline_retry_now), color = AqyalGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
