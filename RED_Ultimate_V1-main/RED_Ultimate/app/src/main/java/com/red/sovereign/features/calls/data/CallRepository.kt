package com.red.sovereign.features.calls.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ─── REST Data Models ──────────────────────────────────────────────────────

data class IceServerDto(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class IceConfigurationDto(
    val expiresAt: Long,
    val iceServers: List<IceServerDto>
)

data class PstnCallRequest(@SerializedName("number") val number: String)

data class PstnCallResponseDto(
    val callId: String,
    val status: String,
    val number: String,
    val usedToday: Int,
    val dailyLimit: Int,
    val slot: Int = -1
)

data class CreateConferenceRequest(
    val roomId: String = "",
    val title: String = "",
    val isSpace: Boolean = false,
    val isPrivate: Boolean = false,
    val password: String? = null
)

data class ConferenceRoomDto(
    val roomId: String,
    val title: String,
    val hostName: String,
    val hostRedId: String,
    val isSpace: Boolean,
    val isPrivate: Boolean,
    val participantCount: Int,
    val inviteLink: String
)

data class JoinConferenceRequest(val password: String? = null)

data class JoinConferenceResponse(
    val authorized: Boolean,
    val roomId: String,
    val title: String = "",
    val isSpace: Boolean = false,
    val hostName: String = "",
    val errorMessage: String? = null
)

data class InviteMembersRequest(val memberIds: List<String>)

data class CreateStreamRequest(
    val streamId: String = "",
    val title: String = "",
    val isPrivate: Boolean = false,
    val password: String? = null
)

data class LiveStreamDto(
    val streamId: String,
    val title: String,
    val broadcasterName: String,
    val broadcasterRedId: String,
    val isPrivate: Boolean,
    val viewerCount: Int,
    val inviteLink: String
)

data class SfuTicketDto(
    val token: String,
    val expiresInSeconds: Long,
    val roomId: String,
    val role: String,
    val canProduce: Boolean
)

data class CallHistoryItemDto(
    val id: String,
    val peerId: String,
    val peerLabel: String,
    val direction: String,
    val type: String,
    val route: String,
    val status: String,
    val startedAt: String,
    val answeredAt: String?,
    val endedAt: String?
)

data class CallTelemetryDto(
    val callId: String,
    val type: String,
    val route: String,
    val durationMs: Long,
    val avgRttMs: Long,
    val maxPacketLoss: Double,
    val qualityAtEnd: String,
    val wasRecorded: Boolean,
    val wasHeld: Int
)

// ─── Retrofit Service Interface ────────────────────────────────────────────

interface RedCallApiService {
    // ICE/TURN credentials
    @GET("/api/calls/ice-servers")
    suspend fun getIceServers(): IceConfigurationDto

    // Call history
    @GET("/api/calls/history")
    suspend fun getCallHistory(@Query("limit") limit: Int = 50): List<CallHistoryItemDto>

    // Telemetry
    @POST("/api/calls/telemetry")
    suspend fun uploadTelemetry(@Body event: CallTelemetryDto): Map<String, String>

    // PSTN calls
    @POST("/api/pstn/calls")
    suspend fun dialPstn(@Body request: PstnCallRequest): PstnCallResponseDto

    @POST("/api/pstn/calls/{callId}/hangup")
    suspend fun hangupPstn(
        @Path("callId") callId: String,
        @Body body: Map<String, Int>? = null
    ): Map<String, Any>

    @GET("/api/pstn/status")
    suspend fun getPstnStatus(): Map<String, Any>

    // SFU Tickets
    @GET("/api/sfu/groups/{groupId}/ticket")
    suspend fun getSfuGroupTicket(@Path("groupId") groupId: String): SfuTicketDto

    @GET("/api/sfu/groups/rooms/{roomId}/ticket")
    suspend fun getSfuRoomTicket(@Path("roomId") roomId: String): SfuTicketDto

    // Conference
    @POST("/api/conference/create")
    suspend fun createConference(@Body request: CreateConferenceRequest): ConferenceRoomDto

    @GET("/api/conference/public")
    suspend fun listPublicConferences(
        @Query("query") query: String? = null,
        @Query("isSpace") isSpace: Boolean = false
    ): List<ConferenceRoomDto>

    @POST("/api/conference/{roomId}/join")
    suspend fun joinConference(
        @Path("roomId") roomId: String,
        @Body request: JoinConferenceRequest = JoinConferenceRequest()
    ): JoinConferenceResponse

    @POST("/api/conference/{roomId}/leave")
    suspend fun leaveConference(@Path("roomId") roomId: String): Map<String, Any>

    @POST("/api/conference/{roomId}/close")
    suspend fun closeConference(@Path("roomId") roomId: String): Map<String, Any>

    @POST("/api/conference/{roomId}/invite")
    suspend fun inviteToConference(
        @Path("roomId") roomId: String,
        @Body request: InviteMembersRequest
    ): Map<String, Any>

    // Live stream
    @POST("/api/livestream/create")
    suspend fun createLiveStream(@Body request: CreateStreamRequest): LiveStreamDto

    @GET("/api/livestream/public")
    suspend fun listPublicStreams(@Query("query") query: String? = null): List<LiveStreamDto>

    @POST("/api/livestream/{streamId}/join")
    suspend fun joinLiveStream(@Path("streamId") streamId: String): Map<String, Any>

    @POST("/api/livestream/{streamId}/leave")
    suspend fun leaveLiveStream(@Path("streamId") streamId: String): Map<String, Any>

    @POST("/api/livestream/{streamId}/stop")
    suspend fun stopLiveStream(@Path("streamId") streamId: String): Map<String, Any>

    @POST("/api/livestream/{streamId}/invite")
    suspend fun inviteToLiveStream(
        @Path("streamId") streamId: String,
        @Body request: InviteMembersRequest
    ): Map<String, Any>
}

// ─── Sealed Result type ────────────────────────────────────────────────────

sealed class CallResult<out T> {
    data class Success<T>(val data: T) : CallResult<T>()
    data class Error(val message: String, val code: Int = -1) : CallResult<Nothing>()
}

// ─── Repository ────────────────────────────────────────────────────────────

@Singleton
class CallRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "red_sovereign_identity"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val authToken: String get() = prefs.getString("AUTH_TOKEN", "") ?: ""
    private val serverBaseUrl: String get() {
        // Read from shared prefs (set during registration/login)
        val host = prefs.getString("SERVER_HOST", "https://red.sovereign.local") ?: "https://red.sovereign.local"
        return host.trimEnd('/')
    }

    private val api: RedCallApiService by lazy { buildApi() }

    private fun buildApi(): RedCallApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = authToken
                val req = if (token.isNotBlank()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else chain.request()
                chain.proceed(req)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("$serverBaseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RedCallApiService::class.java)
    }

    // ── ICE/TURN ──────────────────────────────────────────────────────

    suspend fun getIceServers(): CallResult<IceConfigurationDto> = safeCall {
        api.getIceServers()
    }

    // ── PSTN ──────────────────────────────────────────────────────────

    suspend fun dialPstn(number: String): CallResult<PstnCallResponseDto> = safeCall {
        api.dialPstn(PstnCallRequest(number))
    }

    suspend fun hangupPstn(callId: String, slot: Int? = null): CallResult<Map<String, Any>> = safeCall {
        val body = if (slot != null && slot >= 0) mapOf("port" to slot) else null
        api.hangupPstn(callId, body)
    }

    // ── SFU Ticket ────────────────────────────────────────────────────

    suspend fun getSfuTicketForGroup(groupId: String): CallResult<SfuTicketDto> = safeCall {
        api.getSfuGroupTicket(groupId)
    }

    suspend fun getSfuTicketForRoom(roomId: String): CallResult<SfuTicketDto> = safeCall {
        api.getSfuRoomTicket(roomId)
    }

    // ── Conference ────────────────────────────────────────────────────

    suspend fun createConference(
        title: String,
        roomId: String = "",
        isSpace: Boolean = false,
        isPrivate: Boolean = false,
        password: String? = null
    ): CallResult<ConferenceRoomDto> = safeCall {
        api.createConference(CreateConferenceRequest(roomId, title, isSpace, isPrivate, password))
    }

    suspend fun joinConference(roomId: String, password: String? = null): CallResult<JoinConferenceResponse> = safeCall {
        api.joinConference(roomId, JoinConferenceRequest(password))
    }

    suspend fun leaveConference(roomId: String): CallResult<Map<String, Any>> = safeCall {
        api.leaveConference(roomId)
    }

    suspend fun closeConference(roomId: String): CallResult<Map<String, Any>> = safeCall {
        api.closeConference(roomId)
    }

    suspend fun inviteToConference(roomId: String, memberIds: List<String>): CallResult<Map<String, Any>> = safeCall {
        api.inviteToConference(roomId, InviteMembersRequest(memberIds))
    }

    suspend fun listPublicConferences(query: String? = null, isSpace: Boolean = false): CallResult<List<ConferenceRoomDto>> = safeCall {
        api.listPublicConferences(query, isSpace)
    }

    // ── Live Stream ───────────────────────────────────────────────────

    suspend fun createLiveStream(title: String, streamId: String = "", isPrivate: Boolean = false, password: String? = null): CallResult<LiveStreamDto> = safeCall {
        api.createLiveStream(CreateStreamRequest(streamId, title, isPrivate, password))
    }

    suspend fun stopLiveStream(streamId: String): CallResult<Map<String, Any>> = safeCall {
        api.stopLiveStream(streamId)
    }

    suspend fun inviteToLiveStream(streamId: String, friendIds: List<String>): CallResult<Map<String, Any>> = safeCall {
        api.inviteToLiveStream(streamId, InviteMembersRequest(friendIds))
    }

    // ── Call History ──────────────────────────────────────────────────

    suspend fun getCallHistory(limit: Int = 50): CallResult<List<CallHistoryItemDto>> = safeCall {
        api.getCallHistory(limit)
    }

    // ── Telemetry ─────────────────────────────────────────────────────

    suspend fun uploadTelemetry(event: CallTelemetryDto): CallResult<Map<String, String>> = safeCall {
        api.uploadTelemetry(event)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private suspend fun <T> safeCall(block: suspend () -> T): CallResult<T> =
        withContext(Dispatchers.IO) {
            try {
                CallResult.Success(block())
            } catch (e: retrofit2.HttpException) {
                CallResult.Error(
                    message = e.response()?.errorBody()?.string() ?: e.message(),
                    code = e.code()
                )
            } catch (e: Exception) {
                CallResult.Error(message = e.message ?: "Unknown error")
            }
        }
}
