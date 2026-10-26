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
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import java.io.File
import kotlin.concurrent.thread

class LibItem(val file: File, val seconds: Float, val modified: Long)

sealed interface Gen {
    data object Idle : Gen
    data object Running : Gen
    data object Done : Gen
    class Error(val msg: String) : Gen
}

/** All app state + the on-device pipeline. UI reads the Compose state fields directly. */
class Engine(private val ctx: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("vox", Context.MODE_PRIVATE)
    private val modelRoot get() = File(ctx.filesDir, "models")
    private val libDir get() = File(ctx.filesDir, "library").also { it.mkdirs() }

    // settings
    var arabic by mutableStateOf(prefs.getBoolean("ar", false))
    var quality by mutableIntStateOf(prefs.getInt("quality", 1))
    var modelUrl by mutableStateOf(prefs.getString("modelUrl", MODEL_URL) ?: MODEL_URL)

    // model
    var modelReady by mutableStateOf(false)
    var modelBusy by mutableStateOf(false)
    var modelProgress by mutableFloatStateOf(0f)
    var modelStatus by mutableStateOf("")
    var modelError by mutableStateOf<String?>(null)

    // inputs
    var refName by mutableStateOf<String?>(null)
    var refSeconds by mutableFloatStateOf(0f)
    var refText by mutableStateOf("")
    var inputText by mutableStateOf("")
    private var refSamples: FloatArray? = null
    private var refRate = 0

    // generation
    var gen by mutableStateOf<Gen>(Gen.Idle)
    var resultSeconds by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    private var genToken = 0
    private var tts: OfflineTts? = null
    private var lastSamples: FloatArray? = null
    private var lastRate = 0
    private var track: AudioTrack? = null

    val library = mutableStateListOf<LibItem>()

    init {
        refreshModel()
        refreshLibrary()
    }

    fun changeArabic(v: Boolean) { arabic = v; prefs.edit().putBoolean("ar", v).apply() }
    fun changeQuality(v: Int) { quality = v; prefs.edit().putInt("quality", v).apply() }
    fun setModelUrlPref(v: String) { modelUrl = v; prefs.edit().putString("modelUrl", v).apply() }

    private fun post(block: () -> Unit) { ui.post(block) }

    // ------------------------------------------------------------ model

    private fun modelFiles() = modelRoot.walkTopDown().filter { it.isFile }.toList()

    private fun pickOnnx(files: List<File>, key: String): String? {
        val c = files.filter { it.extension == "onnx" && it.name.contains(key) }
        return (c.firstOrNull { it.name.contains("int8") } ?: c.firstOrNull())?.absolutePath
    }

    fun refreshModel() {
        val f = modelFiles()
        modelReady = pickOnnx(f, "encoder") != null && pickOnnx(f, "decoder") != null &&
            pickOnnx(f, "vocos") != null && f.any { it.name == "tokens.txt" }
    }

    fun downloadModel() {
        if (modelBusy) return
        modelBusy = true
        modelError = null
        modelProgress = 0f
        thread {
            try {
                modelRoot.mkdirs()
                val archive = File(ctx.cacheDir, "model.tar.bz2")
                download(modelUrl.trim(), archive) { p -> post { modelProgress = p * .85f } }
                post { modelStatus = "extract"; modelProgress = .88f }
                extractTarBz2(archive, modelRoot)
                archive.delete()
                val vocoder = File(modelRoot, "vocos_24khz.onnx")
                if (!vocoder.exists()) download(VOCODER_URL, vocoder) { p -> post { modelProgress = .88f + p * .12f } }
                tts?.release(); tts = null
                post { modelProgress = 1f; refreshModel() }
            } catch (e: Exception) {
                post { modelError = e.message ?: e.javaClass.simpleName }
            } finally {
                post { modelBusy = false; modelStatus = "" }
            }
        }
    }

    fun deleteModel() {
        tts?.release(); tts = null
        modelRoot.deleteRecursively()
        refreshModel()
    }

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

    // ------------------------------------------------------------ reference

    fun loadReference(uri: Uri, displayName: String) {
        thread {
            try {
                val (samples, rate) = AudioDecoder.decode(ctx, uri, 15)
                refSamples = samples
                refRate = rate
                post {
                    refName = displayName
                    refSeconds = samples.size / rate.toFloat()
                    if (gen is Gen.Error) gen = Gen.Idle
                }
            } catch (e: Exception) {
                post { gen = Gen.Error("${S().errRead}: ${e.message ?: ""}") }
            }
        }
    }

    // ------------------------------------------------------------ synthesis

    private fun S() = com.cloneLocal.app.ui.Strings(arabic)

    private fun loadTts(): OfflineTts {
        tts?.let { return it }
        val files = modelFiles()
        val tokens = files.firstOrNull { it.name == "tokens.txt" }?.absolutePath ?: error("tokens.txt missing")
        val lexicon = files.firstOrNull { it.name == "lexicon.txt" }?.absolutePath ?: ""
        val espeak = modelRoot.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }?.absolutePath ?: ""
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                zipvoice = OfflineTtsZipVoiceModelConfig(
                    tokens = tokens,
                    encoder = pickOnnx(files, "encoder") ?: error("encoder missing"),
                    decoder = pickOnnx(files, "decoder") ?: error("decoder missing"),
                    vocoder = pickOnnx(files, "vocos") ?: error("vocoder missing"),
                    lexicon = lexicon,
                    dataDir = espeak,
                ),
                numThreads = 4,
                debug = false,
                provider = "cpu",
            ),
        )
        return OfflineTts(config = cfg).also { tts = it }
    }

    fun generate() {
        val s = S()
        val ref = refSamples
        val text = inputText.trim()
        val rt = refText.trim()
        when {
            !modelReady -> { gen = Gen.Error(s.errNoModel); return }
            ref == null -> { gen = Gen.Error(s.errNoRef); return }
            rt.isEmpty() -> { gen = Gen.Error(s.errNoRefText); return }
            text.isEmpty() -> { gen = Gen.Error(s.errNoText); return }
        }
        stopPlayback()
        gen = Gen.Running
        val token = ++genToken
        val steps = intArrayOf(3, 5, 8)[quality.coerceIn(0, 2)]
        thread {
            try {
                val engine = loadTts()
                val audio = engine.generateWithConfig(
                    text,
                    GenerationConfig(
                        referenceAudio = ref!!,
                        referenceSampleRate = refRate,
                        referenceText = rt,
                        numSteps = steps,
                        speed = 1.0f,
                    ),
                )
                if (token != genToken) return@thread
                val f = File(libDir, "vox_${System.currentTimeMillis()}.wav")
                f.outputStream().use { WavWriter.write(it, audio.samples, audio.sampleRate) }
                post {
                    lastSamples = audio.samples
                    lastRate = audio.sampleRate
                    resultSeconds = audio.samples.size / audio.sampleRate.toFloat()
                    gen = Gen.Done
                    refreshLibrary()
                    playSamples(audio.samples, audio.sampleRate)
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

    fun release() {
        stopPlayback()
        tts?.release(); tts = null
    }

    companion object {
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-zh-en-emilia.tar.bz2"
        const val VOCODER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos_24khz.onnx"
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
