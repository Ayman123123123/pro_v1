package com.red.sovereign.calls.sip

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * قناة SIP/UDP واحدة: إرسال/استقبال حزم `DatagramPacket` فوق `DatagramSocket`
 * مربوط بمنفذ محلي **ثابت طوال عمر الجلسة**.
 *
 * ## لماذا هذه الطبقة موجودة أصلًا
 * `WebRtcSipClient` الحالي **لا يستطيع** إرسال حزمة UDP إطلاقًا: نقله هو
 * `okhttp3.WebSocket.send(text)`، أي أنه يحتاج ترقية HTTP `Upgrade: websocket`
 * فوق **TCP**. عند توجيهه إلى `192.168.11.3:5060` (منفذ SIP/UDP عند DINSTAR)
 * يفشل مصافحة TCP حتمًا، ثم يبتلع الكود الفشل عبر `ws?.send(msg)` — وهي
 * safe-call تُرجع `null` بصمت إذا كان `ws` لم يُفتح، وتُرجع `false` إذا أغلق
 * OkHttp المقبس أو امتلأت طابورته (سقف 16 ميغابايت). **قيمة الإرجاع مُهمَلة
 * في كل مواضع الإرسال الثمانية**، وهذا حرفيًا عرَض «`SIP_SEND_FAILED` / استثناء
 * رسالة فارغة»: لا بايت يخرج من الجهاز، ولا رسالة خطأ تصل الواجهة.
 *
 * هنا كل إرسال يُرجع [SendOutcome] ويُحقَّن في [SendFailure] برسالة **لا تكون
 * `null` أبدًا** (`message` يشتق من `toString()` عند غياب `message`).
 *
 * ## الإحكام مقابل التسليم
 * `receive()` يستيقظ كل [POLL_MS] ليفحص `isActive` — بلا ذلك يبقى خيط الاستماع
 * محجوزًا بعد `close()` ولا يرى المتلقي إلغاء المهمة (نمط تسرّب خيوط شائع في
 * عملاء SIP المكتوبين يدويًا).
 */
class UdpSipTransport(
    private val bindPort: Int = 0,
    private val bindAddress: InetAddress? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    data class Inbound(val message: SipMessage, val from: InetSocketAddress, val bytes: Int)

    sealed interface SendOutcome {
        /** بايتات مُسلَّمة لمُحرّك الشبكة فعلًا. */
        data class Sent(val bytes: Int) : SendOutcome
        /** تعذّر الإرسال — السبب مضمون غير فارغ، ولا استثناء صامت. */
        data class Failed(val reason: String, val cause: Throwable?) : SendOutcome
    }

    private val stats = AtomicLong()
    private val receiveBuffer = ByteArray(SipMessage.MAX_DATAGRAM)
    private val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)

    @Volatile private var socket: DatagramSocket? = null

    /** مُغلق عند تعطل القناة؛ يُقرأ قبل أي إرسال لرفض العمل على جثة المقبس. */
    @Volatile private var failure: String? = null
    @Volatile private var stopped = false

    private val inbound = Channel<Inbound>(Channel.BUFFERED)
    private var reader: Job? = null

    val localPort: Int get() = socket?.localPort ?: -1
    val isHealthy: Boolean get() = socket?.isClosed == false && failure == null && !stopped

    /** عنوان يُستخدم في `Contact` بحيث يستطيع الدجَّار إرجاع الطلبات إلينا. */
    fun localSocketAddress(): InetSocketAddress? = socket?.let { InetSocketAddress(it.localAddress, it.localPort) }

    fun start(scope: CoroutineScope) {
        if (reader?.isActive == true) return
        val s = try {
            val created = if (bindAddress != null) DatagramSocket(bindPort, bindAddress) else DatagramSocket(bindPort)
            created.soTimeout = POLL_MS
            // حزمة SIP/UDP نموذجية < 1400 بايت؛ تكبير المخزن يمتصّ دفعة 401 + 100 Trying.
            runCatching { created.receiveBufferSize = 262_144 }
            runCatching { created.broadcast = false }
            created
        } catch (t: Throwable) {
            failure = "UDP_BIND_FAILED: ${t.descriptiveMessage()} (${t.javaClass.simpleName})"
            throw UdpTransportException(failure!!, t)
        }
        socket = s
        reader = scope.launch(dispatcher) { readLoop() }
    }

    private suspend fun readLoop() {
        while (isActive && !stopped) {
            val s = socket ?: return
            try {
                packet.setData(receiveBuffer, 0, receiveBuffer.size)
                s.receive(packet)
                val len = packet.length
                if (len <= 0) continue
                val from = InetSocketAddress(packet.address, packet.port)
                val raw = receiveBuffer.copyOfRange(0, len)
                val msg = SipMessage.parse(raw, len)
                if (msg == null) {
                    // ليس SIP (ردّ HTTP/400، ضجيج STUN، حزمة اقتطعها MTU).
                    // لا نبتلعه: يُرفع كحدث تشخيصي عبر [skippedChannel].
                    skippedPackets.trySend(SkippedInput(from, len, raw.take(48).toByteArray().toString(Charsets.ISO_8859_1)))
                    continue
                }
                stats.incrementAndGet()
                inbound.send(Inbound(msg, from, len))
            } catch (_: SocketTimeoutException) {
                // لا شيء في النافذة — أعد الفحص مع `isActive`/`stopped`.
                continue
            } catch (t: Throwable) {
                if (stopped || s.isClosed) return
                failure = "UDP_RECEIVE_FAILED: ${t.descriptiveMessage()}"
                inbound.close(UdpTransportException(failure!!, t))
                return
            }
        }
    }

    fun outgoing(): Channel<Inbound> = inbound

    data class SkippedInput(val from: InetSocketAddress, val bytes: Int, val preview: String)

    /**
     * حزم واردة لم تُفهم كـ SIP — نافذة تشخيص بلا تأثير على حالة التسجيل.
     * `DROP_OLDEST` حتى لا يُبطئ طابور تشخيص الواجهة مسار الرسائل الحرج.
     */
    val skippedPackets: Channel<SkippedInput> =
        Channel(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun send(bytes: ByteArray, to: InetSocketAddress): SendOutcome {
        val s = socket
        if (s == null || s.isClosed) {
            return SendOutcome.Failed("SOCKET_NOT_OPEN (transport started=${s != null}, failure=${failure ?: "none"})", null)
        }
        failure?.let { return SendOutcome.Failed("TRANSPORT_DEGRADED: $it", null) }
        return try {
            s.send(DatagramPacket(bytes, bytes.size, to))
            SendOutcome.Sent(bytes.size)
        } catch (t: Throwable) {
            // أشهر حالة على Android: `EADDRNOTAVAIL`/`ENETUNREACH` عند انقطاع
            // واجهة Wi-Fi أو غياب روتين إلى الشبكة 192.168.11.0/24.
            SendOutcome.Failed("UDP_SEND_FAILED → ${to.hostString}:${to.port}: ${t.descriptiveMessage()}", t)
        }
    }

    /**
     * A `Throwable.message` is **nullable** and `SocketException()` often has none —
     * which is exactly how the old code produced the "silent null-message exception".
     * Every error path here funnels through this helper, so a message always exists.
     */
    private fun Throwable.descriptiveMessage(): String =
        message?.takeIf { it.isNotBlank() }
            ?: (javaClass.simpleName + (cause?.let { " caused by ${it.javaClass.simpleName}: ${it.message}" } ?: ""))

    fun close() {
        stopped = true
        runCatching { reader?.cancel() }
        reader = null
        runCatching { socket?.close() }
        socket = null
        inbound.close()
        runCatching { skippedPackets.close() }
    }

    private companion object {
        /** نافذة `soTimeout`: قصيرة كي لا يتأخر الإغلاق، وبعيدة عن استهلاك CPU. */
        const val POLL_MS = 150
    }
}

class UdpTransportException(message: String, cause: Throwable?) : RuntimeException(message, cause)
