package com.red.sovereign.features.lan

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * P2-LAN — رسالة إشارة عبر TCP المباشر (نفس الواي فاي، بلا خادم وسيط).
 *
 * الوسائط نفسها محمية بـ DTLS-SRTP (WebRTC افتراضيا). الحمولة هنا SDP/ICE
 * قصيرة العمر — والهوية تُتحقق بمطابقة redId مع جهات الاتصال (شارة تحقق).
 */
@Serializable
data class LanMsg(
    /** HELLO/OFFER/ANSWER/ICE/BYE/PING */
    val type: String,
    val from: String,
    val to: String,
    val callId: String = "",
    val sdp: String = "",
    /** OFFER فقط: voice/video */
    val media: String = "voice",
    val ice: LanIce? = null,
    val name: String = "",
    /** عنوان المرسل للرد المباشر (احتياطي إن لم يكن مكتشفا عبر NSD بعد). */
    val host: String = "",
    val port: Int = 0,
    /** ختم Signal: true = sdp فارغ والحمولة في enc (Base64). */
    val sealed: Boolean = false,
    val enc: String = "",
    val encType: Int = 0,
    val encDevice: Int = 0
)

@Serializable
data class LanIce(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val sdp: String = ""
)

/**
 * رابط الإشارة: خادم TCP مستمع + إرسال برسائل قصيرة العمر.
 *
 * ملاحظة عزل العميل (AP isolation): إن فشل الاتصال بالنظير رغم ظهوره في
 * الاكتشاف، فالراوتر يمنع جهاز-لجهاز — نبلغ الواجهة بـ [LanLinkError.UNREACHABLE].
 */
class LanLink(
    private val myRedId: String
) {
    companion object {
        /** منفذ الإشارة الثابت — معلن في سجل NSD. */
        const val PORT = 47831
        const val CONNECT_TIMEOUT_MS = 4_000
        const val MAX_FRAME = 256 * 1024
        private const val TAG = "LanLink"
    }

    enum class LanLinkError { UNREACHABLE, TIMEOUT, IO }

    interface Listener {
        fun onFrame(msg: LanMsg)
        fun onSendError(to: LanPeer, error: LanLinkError)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var server: ServerSocket? = null
    var listener: Listener? = null

    /** بدء الاستماع. يعيد المنفذ الفعلي (PORT أو بديل عند الانشغال). */
    fun listen(port: Int = PORT): Int {
        if (server?.isBound == true) return server?.localPort ?: port
        var p = port
        repeat(8) {
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(p))
                    server = this
                }
                return@listen p.also { startAcceptLoop() }
            }
            p++
        }
        return -1
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    fun actualPort(): Int = server?.localPort ?: -1

    /** إرسال رسالة واحدة باتصال قصير العمر (كافٍ لتردد الإشارة). */
    fun send(to: LanPeer, msg: LanMsg) {
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(CONNECT_TIMEOUT_MS.toLong()) {
                        Socket().use { s ->
                            s.tcpNoDelay = true
                            s.connect(InetSocketAddress(to.host, to.port), CONNECT_TIMEOUT_MS)
                            val out = DataOutputStream(s.getOutputStream())
                            val bytes = json.encodeToString(msg).toByteArray(Charsets.UTF_8)
                            require(bytes.size <= MAX_FRAME) { "frame too large" }
                            out.writeInt(bytes.size)
                            out.write(bytes)
                            out.flush()
                        }
                    } ?: throw java.net.SocketTimeoutException("connect timeout")
                    null
                }.exceptionOrNull()?.let {
                    Log.w(TAG, "send ${msg.type} to ${to.redId} failed: ${it.message}")
                    when (it) {
                        is java.net.SocketTimeoutException -> LanLinkError.TIMEOUT
                        is java.net.ConnectException -> LanLinkError.UNREACHABLE
                        else -> LanLinkError.IO
                    }
                }
            }
            if (err != null) listener?.onSendError(to, err)
        }
    }

    private fun startAcceptLoop() {
        val srv = server ?: return
        scope.launch {
            while (!srv.isClosed) {
                val sock = runCatching { srv.accept() }.getOrNull() ?: break
                launch { handleOne(sock) }
            }
        }
    }

    private fun handleOne(sock: Socket) {
        sock.use { s ->
            runCatching {
                s.soTimeout = CONNECT_TIMEOUT_MS
                val input = DataInputStream(s.getInputStream())
                val len = input.readInt()
                if (len <= 0 || len > MAX_FRAME) return
                val bytes = ByteArray(len)
                input.readFully(bytes)
                val msg = json.decodeFromString<LanMsg>(bytes.toString(Charsets.UTF_8))
                if (msg.from.isBlank()) return
                listener?.onFrame(msg)
            }.onFailure { Log.w(TAG, "accept frame failed: ${it.message}") }
        }
    }
}
