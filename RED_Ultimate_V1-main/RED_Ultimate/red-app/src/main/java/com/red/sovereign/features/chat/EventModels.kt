package com.red.sovereign.features.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * P1-B — نماذج الفعاليات داخل الشات (ملف جديد فقط، لا يعدّل أي ملف قائم).
 */
data class ChatEvent(
    val id: String,
    val title: String,
    val timeMillis: Long = 0L,
    val location: String = "",
    val rsvp: Rsvp? = null
)

enum class Rsvp {
    GOING,
    MAYBE,
    DECLINED
}

fun buildEventHashtag(event: ChatEvent): String = "#event-${event.id}"

@Composable
fun EventCard(
    event: ChatEvent,
    onRsvp: (Rsvp) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = event.title)
            Text(text = event.timeMillis.toString())
            if (event.location.isNotBlank()) {
                Text(text = event.location)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRsvp(Rsvp.GOING) }) { Text("ذاهب") }
                Button(onClick = { onRsvp(Rsvp.MAYBE) }) { Text("ربما") }
                Button(onClick = { onRsvp(Rsvp.DECLINED) }) { Text("معتذر") }
            }
        }
    }
}
