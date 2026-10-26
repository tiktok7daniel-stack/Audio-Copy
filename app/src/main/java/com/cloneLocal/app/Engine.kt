package com.cloneLocal.app

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.concurrent.thread

class LibItem(val file: File, val seconds: Float, val modified: Long)

sealed interface Gen {
    data object Idle : Gen
    data object Running : Gen
    data object Done : Gen
    class Error(val msg: String) : Gen
}

/** A downloadable model pack with its own progress state. */
class Pack {
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var extracting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** App state for the UI. Model work lives in [VoxCore] so the TTS service can share it. */
class Engine(private val ctx: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("vox", Context.MODE_PRIVATE)
    val core = VoxCore.get(ctx)
    private val libDir get() = File(ctx.filesDir, "library").also { it.mkdirs() }

    // settings
    var arabic by mutableStateOf(prefs.getBoolean("ar", false))
    var quality by mutableIntStateOf(core.quality)
    var modelUrl by mutableStateOf(prefs.getString("modelUrl", MODEL_URL) ?: MODEL_URL)

    // packs
    val arPack = Pack()
    val zipPack = Pack()

    // voices
    val voices = mutableStateListOf<Voice>()
    var activeId by mutableStateOf<String?>(null)
    var refText by mutableStateOf("")
    val activeVoice: Voice? get() = voices.firstOrNull { it.id == activeId }

    // input / generation
    var inputText by mutableStateOf("")
    var gen by mutableStateOf<Gen>(Gen.Idle)
    var resultSeconds by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    private var genToken = 0
    private var lastSamples: FloatArray? = null
    private var lastRate = 0
    private var track: AudioTrack? = null

    val library = mutableStateListOf<LibItem>()

    init {
        refreshPacks()
        refreshVoices()
        refreshLibrary()
    }

    fun changeArabic(v: Boolean) { arabic = v; prefs.edit().putBoolean("ar", v).apply() }
    fun changeQuality(v: Int) { quality = v; core.quality = v }
    fun setModelUrlPref(v: String) { modelUrl = v; prefs.edit().putString("modelUrl", v).apply() }

    private fun post(block: () -> Unit) { ui.post(block) }
    private fun S() = com.cloneLocal.app.ui.Strings(arabic)

    // ------------------------------------------------------------ packs

    fun refreshPacks() {
        arPack.ready = core.arabicReady()
        zipPack.ready = core.zipReady()
    }

    fun downloadArabicPack() {
        if (arPack.busy) return
        arPack.busy = true; arPack.error = null; arPack.progress = 0f
        thread {
            try {
                core.release()
                core.piperDir.mkdirs(); core.ovDir.mkdirs()
                val archive = File(ctx.cacheDir, "piper.tar.bz2")
                download(PIPER_URL, archive) { p -> post { arPack.progress = p * .12f } }
                post { arPack.extracting = true }
                extractTarBz2(archive, core.piperDir)
                archive.delete()
                post { arPack.extracting = false }
                for ((name, from, span) in listOf(Triple("tone_extract.onnx", .12f, .03f), Triple("tone_color.onnx", .15f, .85f))) {
                    val dest = File(core.ovDir, name)
                    if (!dest.exists()) {
                        val tmp = File(core.ovDir, "$name.part")
                        download("$OPENVOICE_BASE/$name", tmp) { p -> post { arPack.progress = from + p * span } }
                        tmp.renameTo(dest)
                    }
                }
                post { arPack.progress = 1f; refreshPacks() }
            } catch (e: Exception) {
                post { arPack.error = e.message ?: e.javaClass.simpleName }
            } finally {
                post { arPack.busy = false; arPack.extracting = false }
            }
        }
    }

    fun downloadZipPack() {
        if (zipPack.busy) return
        zipPack.busy = true; zipPack.error = null; zipPack.progress = 0f
        thread {
            try {
                core.release()
                core.zipDir.mkdirs()
                val archive = File(ctx.cacheDir, "model.tar.bz2")
                download(modelUrl.trim(), archive) { p -> post { zipPack.progress = p * .85f } }
                post { zipPack.extracting = true; zipPack.progress = .88f }
                extractTarBz2(archive, core.zipDir)
                archive.delete()
                val vocoder = File(core.zipDir, "vocos_24khz.onnx")
                if (!vocoder.exists()) download(VOCODER_URL, vocoder) { p -> post { zipPack.progress = .88f + p * .12f } }
                post { zipPack.progress = 1f; refreshPacks() }
            } catch (e: Exception) {
                post { zipPack.error = e.message ?: e.javaClass.simpleName }
            } finally {
                post { zipPack.busy = false; zipPack.extracting = false }
            }
        }
    }

    fun deleteArabicPack() { core.release(); File(ctx.filesDir, "arabic").deleteRecursively(); refreshPacks() }
    fun deleteZipPack() { core.release(); core.zipDir.deleteRecursively(); refreshPacks() }
    fun clearCache() { ctx.cacheDir.deleteRecursively() }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(done.toFloat() / total) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractTarBz2(archive: File, dest: File) {
        val destPath = dest.canonicalPath
        archive.inputStream().buffered().use { raw ->
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(raw),
            ).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val f = File(dest, entry.name)
                    if (!f.canonicalPath.startsWith(destPath)) continue
                    if (entry.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { tar.copyTo(it) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ voices

    fun refreshVoices() {
        val list = core.listVoices()
        voices.clear(); voices.addAll(list)
        activeId = core.activeVoice()?.id
        refText = activeVoice?.refText.orEmpty()
    }

    fun selectVoice(v: Voice) {
        saveRefText()
        core.activeVoiceId = v.id
        activeId = v.id
        refText = v.refText
    }

    fun deleteVoice(v: Voice) { core.deleteVoice(v); refreshVoices() }

    fun saveRefText() { activeVoice?.let { if (it.refText != refText) it.refText = refText } }

    /** Decode the picked audio/video, keep the first 15 s as 22.05 kHz mono, and save it as a new voice. */
    fun addVoice(uri: Uri, displayName: String) {
        thread {
            try {
                val (samples, rate) = AudioDecoder.decode(ctx, uri, 15)
                val s22 = Resampler.resample(samples, rate, OpenVoice.SR)
                val base = displayName.substringBeforeLast('.').ifBlank { "Voice" }
                core.createVoice(base, s22)
                post {
                    saveRefTextSafe()
                    refreshVoices()
                    refText = ""
                    if (gen is Gen.Error) gen = Gen.Idle
                }
            } catch (e: Exception) {
                post { gen = Gen.Error("${S().errRead}: ${e.message ?: ""}") }
            }
        }
    }

    private fun saveRefTextSafe() { /* previous voice's transcript was saved on selection / generate */ }

    // ------------------------------------------------------------ synthesis

    fun generate() {
        val s = S()
        val text = inputText.trim()
        val voice = activeVoice
        val ar = VoxCore.isArabic(text)
        when {
            text.isEmpty() -> { gen = Gen.Error(s.errNoText); return }
            ar && !arPack.ready -> { gen = Gen.Error(s.errNoArPack); return }
            !ar && !zipPack.ready -> { gen = Gen.Error(s.errNoModel); return }
            voice == null && !ar -> { gen = Gen.Error(s.errNoRef); return }
            !ar && refText.isBlank() -> { gen = Gen.Error(s.errNoRefText); return }
        }
        saveRefText()
        stopPlayback()
        gen = Gen.Running
        val token = ++genToken
        thread {
            try {
                val (samples, rate) = core.synthesize(text, voice)
                if (token != genToken) return@thread
                val f = File(libDir, "vox_${System.currentTimeMillis()}.wav")
                f.outputStream().use { WavWriter.write(it, samples, rate) }
                post {
                    lastSamples = samples
                    lastRate = rate
                    resultSeconds = samples.size / rate.toFloat()
                    gen = Gen.Done
                    refreshLibrary()
                    playSamples(samples, rate)
                }
            } catch (e: Throwable) {
                if (token == genToken) post { gen = Gen.Error("${s.errGen}: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun cancelGenerate() { genToken++; gen = Gen.Idle }

    // ------------------------------------------------------------ playback

    fun togglePlayResult() {
        if (playing) { stopPlayback(); return }
        val s = lastSamples ?: return
        playSamples(s, lastRate)
    }

    fun playFile(item: LibItem) {
        if (playing) { stopPlayback(); return }
        thread {
            val (samples, rate) = WavIO.read(item.file)
            post { playSamples(samples, rate) }
        }
    }

    private fun playSamples(samples: FloatArray, rate: Int) {
        stopPlayback()
        val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setNotificationMarkerPosition(pcm.size)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) { if (this@Engine.track === track) playing = false }
            override fun onPeriodicNotification(track: AudioTrack) {}
        })
        track = t
        playing = true
        t.play()
    }

    fun stopPlayback() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        playing = false
    }

    // ------------------------------------------------------------ library

    fun refreshLibrary() {
        val items = (libDir.listFiles { f -> f.extension == "wav" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { LibItem(it, WavIO.seconds(it), it.lastModified()) }
        library.clear()
        library.addAll(items)
    }

    fun delete(item: LibItem) { item.file.delete(); refreshLibrary() }

    fun exportToMusic(item: LibItem) {
        thread {
            val msg = try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, item.file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/VoxGate")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert failed")
                ctx.contentResolver.openOutputStream(uri)!!.use { out -> item.file.inputStream().use { it.copyTo(out) } }
                S().saved
            } catch (e: Exception) {
                e.message ?: "export failed"
            }
            post { toast = msg }
        }
    }

    fun release() { stopPlayback() }

    companion object {
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2"
        const val VOCODER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos_24khz.onnx"
        const val PIPER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-medium-int8.tar.bz2"
        const val OPENVOICE_BASE = "https://huggingface.co/eugenehp/openvoice/resolve/main"
    }
}

object WavIO {
    fun seconds(f: File): Float {
        if (f.length() < 44) return 0f
        val h = f.inputStream().use { it.readNBytes(44) }
        val rate = java.nio.ByteBuffer.wrap(h, 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        return if (rate > 0) ((f.length() - 44) / 2f) / rate else 0f
    }

    fun read(f: File): Pair<FloatArray, Int> {
        val bytes = f.readBytes()
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val rate = bb.getInt(24)
        val n = (bytes.size - 44) / 2
        val out = FloatArray(n) { bb.getShort(44 + it * 2) / 32768f }
        return out to rate
    }
}
