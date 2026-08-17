package com.red.sovereign.calls

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.CallLogEntity
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CallFilterType(val label: String) {
    ALL("الكل"),
    MISSED("فائتة"),
    INCOMING("واردة"),
    OUTGOING("صادرة"),
    VIDEO("مرئية"),
    DINSTAR("GSM يمني")
}

data class CallStatsSummary(
    val totalCalls: Int,
    val answeredCalls: Int,
    val missedCalls: Int,
    val totalDurationSeconds: Long,
    val videoCallsCount: Int,
    val voiceCallsCount: Int,
    val dinstarCallsCount: Int,
    val successRate: Int,
    val topPeer: Pair<String, Int>?,
    val peakHour: Int?
)

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val repository = LocalRepository(application)
    private val cipher = CallLogCipher()
    private val json = Json { ignoreUnknownKeys = true }
    
    val calls = mutableStateListOf<CallHistoryItem>()
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    var searchQuery by mutableStateOf("")
    var selectedFilter by mutableStateOf(CallFilterType.ALL)

    val filteredCalls by derivedStateOf {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        calls.filter { item ->
            // Search Query Filter
            val matchesQuery = if (q.isEmpty()) true else {
                item.peerId.lowercase(Locale.getDefault()).contains(q) ||
                item.peerLabel.lowercase(Locale.getDefault()).contains(q) ||
                item.route.lowercase(Locale.getDefault()).contains(q) ||
                item.type.lowercase(Locale.getDefault()).contains(q)
            }

            // Category Filter
            val matchesCategory = when (selectedFilter) {
                CallFilterType.ALL -> true
                CallFilterType.MISSED -> item.status.equals("MISSED", ignoreCase = true) || item.status.equals("NO_ANSWER", ignoreCase = true)
                CallFilterType.INCOMING -> item.direction.equals("INCOMING", ignoreCase = true)
                CallFilterType.OUTGOING -> item.direction.equals("OUTGOING", ignoreCase = true)
                CallFilterType.VIDEO -> item.type.equals("VIDEO", ignoreCase = true)
                CallFilterType.DINSTAR -> item.route.equals("DINSTAR", ignoreCase = true) || item.route.equals("PSTN", ignoreCase = true)
            }

            matchesQuery && matchesCategory
        }
    }

    init {
        viewModelScope.launch {
            repository.getCallLogs().collectLatest { entities ->
                calls.clear()
                // نُفك تشفير peerId/label للعرض في الواجهة فقط — DB تبقى مشفرة
                calls.addAll(entities.map { it.toCallHistoryItem() })
            }
        }
        load()
    }

    fun load() = viewModelScope.launch {
        loading = true; error = null
        when (val result = client.request("GET", "/api/calls/history?limit=100")) {
            is ApiResult.Success -> runCatching {
                json.decodeFromString<List<CallHistoryItem>>(result.value)
            }.onSuccess { list ->
                if (list.isNotEmpty()) repository.saveCallLogs(list.map { it.toCallLogEntity() })
            }.onFailure { error = "INVALID_CALL_HISTORY: ${it.message}" }
            is ApiResult.Error -> error = result.message
        }
        loading = false
    }

    fun deleteCall(callId: String) = viewModelScope.launch {
        repository.deleteCallLog(callId)
        calls.removeAll { it.id == callId }
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearCallLogs()
        calls.clear()
    }

    fun getStats(): CallStatsSummary {
        val total = calls.size
        if (total == 0) {
            return CallStatsSummary(
                totalCalls = 0,
                answeredCalls = 0,
                missedCalls = 0,
                totalDurationSeconds = 0L,
                videoCallsCount = 0,
                voiceCallsCount = 0,
                dinstarCallsCount = 0,
                successRate = 100,
                topPeer = null,
                peakHour = null
            )
        }

        val answered = calls.count { it.status.equals("ANSWERED", ignoreCase = true) || it.status.equals("COMPLETED", ignoreCase = true) }
        val missed = calls.count { it.status.equals("MISSED", ignoreCase = true) || it.status.equals("NO_ANSWER", ignoreCase = true) }
        val video = calls.count { it.type.equals("VIDEO", ignoreCase = true) }
        val voice = calls.count { it.type.equals("VOICE", ignoreCase = true) }
        val dinstar = calls.count { it.route.equals("DINSTAR", ignoreCase = true) || it.route.equals("PSTN", ignoreCase = true) }
        val totalDuration = calls.sumOf { it.computedDurationSeconds() }
        val successRate = if (total > 0) ((answered.toDouble() / total.toDouble()) * 100).toInt() else 100

        // Top Peer
        val peerCounts = calls.groupingBy { if (it.peerLabel.isNotBlank()) it.peerLabel else it.peerId }.eachCount()
        val topPeer = peerCounts.maxByOrNull { it.value }?.toPair()

        // Peak Hour
        val hours = calls.mapNotNull { item ->
            parseCallTimestamp(item.startedAt)?.let { ts ->
                val cal = Calendar.getInstance().apply { timeInMillis = ts }
                cal.get(Calendar.HOUR_OF_DAY)
            }
        }
        val peakHour = if (hours.isNotEmpty()) hours.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key else null

        return CallStatsSummary(
            totalCalls = total,
            answeredCalls = answered,
            missedCalls = missed,
            totalDurationSeconds = totalDuration,
            videoCallsCount = video,
            voiceCallsCount = voice,
            dinstarCallsCount = dinstar,
            successRate = successRate,
            topPeer = topPeer,
            peakHour = peakHour
        )
    }

    fun exportCsvString(): String {
        val sb = StringBuilder()
        sb.append("ID,Date,PeerID,PeerName,Direction,Type,Route,Status,DurationSeconds\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (c in calls) {
            val dateStr = parseCallTimestamp(c.startedAt)?.let { sdf.format(Date(it)) } ?: c.startedAt
            val dur = c.computedDurationSeconds()
            sb.append("\"${c.id}\",")
            sb.append("\"$dateStr\",")
            sb.append("\"${c.peerId}\",")
            sb.append("\"${c.peerLabel}\",")
            sb.append("\"${c.direction}\",")
            sb.append("\"${c.type}\",")
            sb.append("\"${c.route}\",")
            sb.append("\"${c.status}\",")
            sb.append("$dur\n")
        }
        return sb.toString()
    }

    fun exportCsvFile(context: Context): File? {
        return runCatching {
            val csvContent = exportCsvString()
            val filename = "red_calls_export_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, filename)
            file.writeText(csvContent, Charsets.UTF_8)
            file
        }.getOrNull()
    }

    private fun CallLogEntity.toCallHistoryItem(): CallHistoryItem = CallHistoryItem(
        id = id,
        peerId = cipher.decryptPeerId(peerId),
        peerLabel = cipher.decryptLabel(peerLabel),
        direction = direction,
        type = type,
        route = route,
        status = status,
        startedAt = timestamp.toString(),
        answeredAt = answeredAt?.toString(),
        endedAt = endedAt?.toString()
    )

    private fun CallHistoryItem.toCallLogEntity(): CallLogEntity = CallLogEntity(
        id = id,
        peerId = cipher.encryptPeerId(peerId),
        peerLabel = cipher.encryptLabel(peerLabel),
        type = type,
        direction = direction,
        route = route,
        status = status,
        timestamp = parseCallTimestamp(startedAt) ?: 0L,
        answeredAt = parseCallTimestamp(answeredAt),
        endedAt = parseCallTimestamp(endedAt)
    )
}
