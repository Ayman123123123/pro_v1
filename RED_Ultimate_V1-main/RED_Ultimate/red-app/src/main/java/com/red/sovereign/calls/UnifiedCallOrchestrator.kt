package com.red.sovereign.calls

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * منسق المكالمات الموحد - يدير كل أنواع المكالمات بشكل منفصل ومنظم
 * 
 * أفضل من واتساب وتيليجرام:
 * - كل نوع مكالمة له مسار وواجهة منفصلة حسب عمله الأساسي
 * - رنين موثوق يعمل حتى في الخلفية
 * - يعمل على كل الشبكات المحلية وكل الشبكات
 * - أحدث التقنيات WebRTC + SFU + P2P LAN
 * 
 * أنواع المكالمات:
 * 1. فردية صوت/فيديو (P2P WebRTC) - مثل واتساب
 * 2. جماعية (Mesh حتى 8، SFU بعد ذلك) - أفضل من واتساب (32)
 * 3. مؤتمر/Zoom (حتى 100 مشارك) - مثل Zoom
 * 4. بث مباشر (1-to-N) - مثل تيليجرام
 * 5. مساحات صوتية (صوت فقط) - مثل تويتر سبيس
 * 6. هاتف يمني PSTN عبر DINSTAR - حصري
 * 7. محلي P2P (بلا إنترنت) - حصري
 */

enum class CallTypeUnified {
    ONE_TO_ONE_AUDIO,      // فردية صوتية P2P
    ONE_TO_ONE_VIDEO,      // فردية فيديو P2P
    GROUP_AUDIO,           // جماعية صوتية (حتى 32) - Mesh/SFU
    GROUP_VIDEO,           // جماعية فيديو (حتى 32) - Mesh/SFU
    CONFERENCE,            // مؤتمر (حتى 100) - SFU
    LIVE_STREAM,           // بث مباشر 1-to-N
    SPACE_AUDIO,           // مساحة صوتية (صوت فقط)
    PSTN_YEMENI,           // هاتف يمني عبر DINSTAR
    LAN_P2P                // محلي P2P بلا إنترنت
}

data class CallInfo(
    val callId: String,
    val type: CallTypeUnified,
    val peerId: String = "",
    val peerName: String = "",
    val groupId: String? = null,
    val participants: List<String> = emptyList(),
    val isVideo: Boolean = false,
    val isPrivate: Boolean = false,
    val title: String? = null,
    val startedAt: Long = System.currentTimeMillis()
)

sealed class CallStateUnified {
    object Idle : CallStateUnified()
    data class Outgoing(val info: CallInfo, val ringingState: RingingState = RingingState.CONNECTING) : CallStateUnified()
    data class Incoming(val info: CallInfo) : CallStateUnified()
    data class Active(val info: CallInfo, val durationMs: Long = 0, val isHeld: Boolean = false) : CallStateUnified()
    data class Ended(val info: CallInfo, val reason: CallEndReason, val durationMs: Long = 0) : CallStateUnified()
    data class Error(val message: String, val type: CallTypeUnified? = null) : CallStateUnified()
}

enum class RingingState {
    CONNECTING,     // جاري الاتصال
    RINGING,        // يرن على جهاز المستلم
    WAKING_UP,      // جاري إيقاظ الجهاز
    NO_ANSWER,      // لا يوجد رد
    BUSY,           // مشغول
    DECLINED        // مرفوض
}

enum class CallEndReason {
    COMPLETED,      // انتهت بشكل طبيعي
    REJECTED,       // رفضها المستلم
    BUSY,           // المستلم مشغول
    NO_ANSWER,      // لم يرد
    FAILED,         // فشلت
    CANCELLED,      // ألغاها المتصل
    NETWORK_ERROR,  // خطأ شبكة
    PERMISSION_DENIED // إذن مرفوض
}

object UnifiedCallOrchestrator {
    
    private val _state = MutableStateFlow<CallStateUnified>(CallStateUnified.Idle)
    val state: StateFlow<CallStateUnified> = _state.asStateFlow()
    
    private val _activeCalls = MutableStateFlow<List<CallInfo>>(emptyList())
    val activeCalls: StateFlow<List<CallInfo>> = _activeCalls.asStateFlow()
    
    private var currentCallId: String? = null
    
    fun getCallTypeDescription(type: CallTypeUnified): String = when (type) {
        CallTypeUnified.ONE_TO_ONE_AUDIO -> "مكالمة صوتية فردية مشفرة E2EE عبر WebRTC P2P"
        CallTypeUnified.ONE_TO_ONE_VIDEO -> "مكالمة فيديو فردية مشفرة E2EE عبر WebRTC P2P مع مشاركة شاشة"
        CallTypeUnified.GROUP_AUDIO -> "مكالمة جماعية صوتية حتى 32 مشارك - Mesh حتى 8، SFU بعد ذلك، ترن الجميع"
        CallTypeUnified.GROUP_VIDEO -> "مكالمة جماعية فيديو حتى 32 مشارك - شبكية مع SFU، ترن الجميع"
        CallTypeUnified.CONFERENCE -> "مؤتمر فيديو حتى 100 مشارك عبر SFU مع غرف جانبية ورفع يد وتسجيل"
        CallTypeUnified.LIVE_STREAM -> "بث مباشر 1-to-N مع دردشة وتفاعلات وهدايا، عام أو خاص بكلمة سر"
        CallTypeUnified.SPACE_AUDIO -> "مساحة صوتية جماعية - صوت فقط بلا فيديو، مضيف ومستمعون ومتحدثون"
        CallTypeUnified.PSTN_YEMENI -> "هاتف يمني عبر بوابة DINSTAR وشرائح يمن موبايل وسبأفون وYOU والهاتف الثابت"
        CallTypeUnified.LAN_P2P -> "مكالمة محلية P2P بلا إنترنت ولا خادم - نفس الواي فاي، مشفرة DTLS-SRTP"
    }
    
    fun getCallTypeIcon(type: CallTypeUnified): String = when (type) {
        CallTypeUnified.ONE_TO_ONE_AUDIO -> "📞"
        CallTypeUnified.ONE_TO_ONE_VIDEO -> "📹"
        CallTypeUnified.GROUP_AUDIO -> "👥📞"
        CallTypeUnified.GROUP_VIDEO -> "👥📹"
        CallTypeUnified.CONFERENCE -> "🎥"
        CallTypeUnified.LIVE_STREAM -> "🔴"
        CallTypeUnified.SPACE_AUDIO -> "🎙️"
        CallTypeUnified.PSTN_YEMENI -> "☎️🇾🇪"
        CallTypeUnified.LAN_P2P -> "📶"
    }
    
    fun getCallRoute(type: CallTypeUnified): String = when (type) {
        CallTypeUnified.ONE_TO_ONE_AUDIO, CallTypeUnified.ONE_TO_ONE_VIDEO -> "RED WebRTC P2P → TURN → RED ID"
        CallTypeUnified.GROUP_AUDIO, CallTypeUnified.GROUP_VIDEO -> "RED Mesh (<8) / SFU (8-32) → WebRTC → RED IDs"
        CallTypeUnified.CONFERENCE -> "RED SFU (mediasoup) → WebRTC → Room"
        CallTypeUnified.LIVE_STREAM -> "RED SFU 1-to-N → WebRTC → Viewers"
        CallTypeUnified.SPACE_AUDIO -> "RED SFU Audio-Only → WebRTC → Space"
        CallTypeUnified.PSTN_YEMENI -> "Android → Backend Auth → Asterisk AMI → DINSTAR → SIM → Yemen Network"
        CallTypeUnified.LAN_P2P -> "NSD Discovery → DTLS-SRTP P2P → No Server"
    }
    
    fun checkPermissions(context: Context, type: CallTypeUnified): Boolean {
        val audioGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) return false
        
        return when (type) {
            CallTypeUnified.ONE_TO_ONE_VIDEO, CallTypeUnified.GROUP_VIDEO, 
            CallTypeUnified.CONFERENCE, CallTypeUnified.LIVE_STREAM -> {
                val cameraGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                audioGranted && cameraGranted
            }
            else -> audioGranted
        }
    }
    
    fun requestPermissions(context: Context, type: CallTypeUnified): Array<String> {
        return when (type) {
            CallTypeUnified.ONE_TO_ONE_AUDIO, CallTypeUnified.GROUP_AUDIO, 
            CallTypeUnified.SPACE_AUDIO, CallTypeUnified.PSTN_YEMENI, CallTypeUnified.LAN_P2P -> {
                arrayOf(android.Manifest.permission.RECORD_AUDIO)
            }
            CallTypeUnified.ONE_TO_ONE_VIDEO, CallTypeUnified.GROUP_VIDEO,
            CallTypeUnified.CONFERENCE, CallTypeUnified.LIVE_STREAM -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    arrayOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    arrayOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.CAMERA
                    )
                }
            }
        }
    }
    
    fun startCall(context: Context, info: CallInfo) {
        currentCallId = info.callId
        _state.value = CallStateUnified.Outgoing(info, RingingState.CONNECTING)
        
        when (info.type) {
            CallTypeUnified.ONE_TO_ONE_AUDIO -> {
                YounesCallService.start(context, info.peerId, false)
            }
            CallTypeUnified.ONE_TO_ONE_VIDEO -> {
                YounesCallService.start(context, info.peerId, true)
            }
            CallTypeUnified.GROUP_AUDIO -> {
                GroupCallService.startGroupCall(
                    context = context,
                    myUserId = info.peerId, // سيتم تمريره بشكل صحيح من المتصل
                    inviteeIds = info.participants,
                    inviteeNames = info.participants,
                    isVideo = false,
                    hostName = info.peerName,
                    groupId = info.groupId
                )
            }
            CallTypeUnified.GROUP_VIDEO -> {
                GroupCallService.startGroupCall(
                    context = context,
                    myUserId = info.peerId,
                    inviteeIds = info.participants,
                    inviteeNames = info.participants,
                    isVideo = true,
                    hostName = info.peerName,
                    groupId = info.groupId
                )
            }
            CallTypeUnified.CONFERENCE -> {
                ConferenceService.join(context, info.callId, info.peerId, info.isVideo, asHost = true)
            }
            CallTypeUnified.LIVE_STREAM -> {
                LiveStreamService.start(
                    context = context,
                    streamId = info.callId,
                    userId = info.peerId,
                    isBroadcaster = true,
                    title = info.title,
                    isPrivate = info.isPrivate
                )
            }
            CallTypeUnified.SPACE_AUDIO -> {
                ConferenceService.join(context, info.callId, info.peerId, false, asHost = true)
            }
            CallTypeUnified.PSTN_YEMENI -> {
                // يتم عبر AuthViewModel.dialPstn
            }
            CallTypeUnified.LAN_P2P -> {
                // يتم عبر LanCallManager
            }
        }
    }
    
    fun acceptCall(context: Context, callId: String) {
        when (val current = _state.value) {
            is CallStateUnified.Incoming -> {
                when (current.info.type) {
                    CallTypeUnified.ONE_TO_ONE_AUDIO, CallTypeUnified.ONE_TO_ONE_VIDEO -> {
                        YounesCallService.accept(context, callId)
                    }
                    CallTypeUnified.GROUP_AUDIO, CallTypeUnified.GROUP_VIDEO -> {
                        // GroupCallService.accept
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }
    
    fun endCall(context: Context, reason: CallEndReason = CallEndReason.COMPLETED) {
        val current = _state.value
        val info = when (current) {
            is CallStateUnified.Outgoing -> current.info
            is CallStateUnified.Incoming -> current.info
            is CallStateUnified.Active -> current.info
            else -> null
        }
        
        if (info != null) {
            _state.value = CallStateUnified.Ended(info, reason)
            
            when (info.type) {
                CallTypeUnified.ONE_TO_ONE_AUDIO, CallTypeUnified.ONE_TO_ONE_VIDEO -> {
                    YounesCallService.end(context)
                }
                CallTypeUnified.GROUP_AUDIO, CallTypeUnified.GROUP_VIDEO -> {
                    GroupCallService.end(context)
                }
                CallTypeUnified.CONFERENCE, CallTypeUnified.SPACE_AUDIO -> {
                    ConferenceService.leave(context)
                }
                CallTypeUnified.LIVE_STREAM -> {
                    LiveStreamService.stop(context)
                }
                CallTypeUnified.LAN_P2P -> {
                    // LanCallManager.endCall
                }
                else -> {}
            }
            
            // تنظيف بعد 4 ثواني (واتساب)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                _state.value = CallStateUnified.Idle
                currentCallId = null
            }, 4000)
        }
    }
    
    fun updateRingingState(ringingState: RingingState) {
        val current = _state.value
        if (current is CallStateUnified.Outgoing) {
            _state.value = current.copy(ringingState = ringingState)
        }
    }
    
    fun onCallConnected(callId: String, peerId: String) {
        val current = _state.value
        val info = when (current) {
            is CallStateUnified.Outgoing -> current.info
            is CallStateUnified.Incoming -> current.info
            else -> null
        }
        
        if (info != null && info.callId == callId) {
            _state.value = CallStateUnified.Active(info)
        }
    }
    
    fun isInCall(): Boolean {
        return _state.value !is CallStateUnified.Idle && 
               _state.value !is CallStateUnified.Ended &&
               _state.value !is CallStateUnified.Error
    }
    
    fun canMakeNewCall(): Boolean {
        return _state.value is CallStateUnified.Idle || 
               _state.value is CallStateUnified.Ended
    }
}
