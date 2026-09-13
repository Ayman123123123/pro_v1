package com.red.sovereign.media.voice

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * تفريغ الرسائل الصوتية دون اتصال — Vosk على الجهاز.
 *
 * - النموذج العربي الصغير (~50MB) يُنزَّل عند أول طلب عبر Wi-Fi فقط إلى filesDir.
 * - يفك ترميز أي صوت (AAC/m4a/ogg) إلى PCM 16kHz أحادي عبر MediaCodec ثم يمرره لـ Vosk.
 * - لا يغادر الصوت الجهاز أبداً (خصوصية E2EE تبقى سليمة).
 */
object VoskTranscriber {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ar-0.22.zip"
    private const val MODEL_DIR_NAME = "vosk-model-ar"
    private const val SAMPLE_RATE = 16000

    sealed interface TranscribeState {
        data object Idle : TranscribeState
        data class Downloading(val percent: Int) : TranscribeState
        data object Transcribing : TranscribeState
        data class Done(val text: String) : TranscribeState
        data class Failed(val reason: String) : TranscribeState
    }

    @Volatile private var cachedModel: Model? = null

    fun isModelReady(context: Context): Boolean =
        cachedModel != null || File(context.filesDir, MODEL_DIR_NAME).isDirectory

    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    suspend fun transcribeUri(
        context: Context,
        uri: Uri,
        onState: (TranscribeState) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val modelDir = ensureModel(context, onState) ?: run {
                onState(TranscribeState.Failed("تعذر تجهيز نموذج التفريغ"))
                return@withContext
            }
            onState(TranscribeState.Transcribing)
            val pcm = decodeToPcm16(context, uri) ?: run {
                onState(TranscribeState.Failed("تعذر فك ترميز الصوت"))
                return@withContext
            }
            val model = cachedModel ?: Model(modelDir.absolutePath).also { cachedModel = it }
            val rec = Recognizer(model, SAMPLE_RATE.toFloat())
            val chunk = 4096
            var off = 0
            while (off < pcm.size) {
                val len = minOf(chunk, pcm.size - off)
                rec.acceptWaveForm(pcm.copyOfRange(off, off + len), len)
                off += len
            }
            val text = JSONObject(rec.finalResult).optString("text", "").trim()
            rec.close()
            if (text.isBlank()) onState(TranscribeState.Failed("لا كلام واضح في الرسالة"))
            else onState(TranscribeState.Done(text))
        } catch (e: Exception) {
            onState(TranscribeState.Failed(e.message ?: "خطأ غير متوقع"))
        }
    }

    private suspend fun ensureModel(
        context: Context,
        onState: (TranscribeState) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, MODEL_DIR_NAME)
        if (dir.isDirectory && dir.list()?.isNotEmpty() == true) return@withContext dir
        if (!isWifi(context)) return@withContext null
        try {
            val tmp = File(context.cacheDir, "vosk-ar.zip")
            downloadWithProgress(MODEL_URL, tmp) { onState(TranscribeState.Downloading(it)) }
            dir.deleteRecursively()
            dir.mkdirs()
            unzip(tmp, dir)
            tmp.delete()
            // النموذج داخل مجلد فرعي واحد عادة — اعثر على مجلد am/final.mdl
            findModelRoot(dir) ?: run { dir.deleteRecursively(); null }
        } catch (e: Exception) {
            dir.deleteRecursively()
            null
        }
    }

    private fun findModelRoot(dir: File): File? {
        if (File(dir, "am/final.mdl").exists()) return dir
        dir.listFiles()?.filter { it.isDirectory }?.forEach {
            findModelRoot(it)?.let { found -> return found }
        }
        return null
    }

    private fun downloadWithProgress(url: String, out: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
        }
        val total = conn.contentLengthLong.takeIf { it > 0 }
        conn.inputStream.use { input ->
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(32768)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    fos.write(buf, 0, n)
                    done += n
                    if (total != null) {
                        val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                    }
                }
            }
        }
    }

    private fun unzip(zip: File, dest: File) {
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            var entry = zis.nextEntry
            val destPath = dest.canonicalPath
            while (entry != null) {
                val target = File(dest, entry.name)
                require(target.canonicalPath.startsWith(destPath)) { "Zip entry outside target" }
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** فك الترميز إلى PCM 16-bit أحادي 16kHz عبر MediaCodec + إعادة عيّنة خطية. */
    private fun decodeToPcm16(context: Context, uri: Uri): ByteArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            return null
        }
        try {
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return null
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val pcmChunks = mutableListOf<ByteArray>()
            val info = MediaCodec.BufferInfo()
            var eosIn = false
            var eosOut = false
            val tmp = ByteArray(8192)
            while (!eosOut) {
                if (!eosIn) {
                    val inIdx = codec.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx) ?: continue
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosIn = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10000)
                when {
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)
                        if (buf != null && info.size > 0) {
                            val arr = ByteArray(info.size)
                            buf.get(arr)
                            pcmChunks.add(arr)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosOut = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (eosIn) Thread.sleep(5)
                }
                if (pcmChunks.sumOf { it.size } > 20 * 1024 * 1024) break // حد أمان 20MB
            }
            codec.stop(); codec.release()
            val raw = pcmChunks.fold(ByteArray(0)) { acc, b -> acc + b }
            return resampleTo16kMono(raw, srcRate, srcChannels)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun resampleTo16kMono(raw: ByteArray, srcRate: Int, srcChannels: Int): ByteArray {
        if (raw.isEmpty()) return raw
        val src = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = src.remaining() / srcChannels.coerceAtLeast(1)
        if (srcRate == SAMPLE_RATE && srcChannels == 1) {
            val out = ByteArray(frames * 2)
            ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(
                ShortArray(frames) { src.get(it) }
            )
            return out
        }
        val ratio = srcRate.toDouble() / SAMPLE_RATE
        val outFrames = (frames / ratio).roundToInt().coerceAtLeast(1)
        val outShorts = ShortArray(outFrames)
        val tmp = ShortArray(frames * srcChannels) { i -> src.get(i) }
        for (i in 0 until outFrames) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, frames - 1)
            val i1 = (i0 + 1).coerceIn(0, frames - 1)
            val frac = (pos - i0).toFloat()
            var acc = 0f
            for (ch in 0 until srcChannels) {
                val s0 = tmp[i0 * srcChannels + ch].toFloat()
                val s1 = tmp[i1 * srcChannels + ch].toFloat()
                acc += s0 + (s1 - s0) * frac
            }
            outShorts[i] = (acc / srcChannels).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val out = ByteArray(outFrames * 2)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outShorts)
        return out
    }
}
