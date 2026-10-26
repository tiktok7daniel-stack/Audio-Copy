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

    // packs
    val pack = Pack()

    // voices
    val voices = mutableStateListOf<Voice>()
    var activeId by mutableStateOf<String?>(null)
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

    private fun post(block: () -> Unit) { ui.post(block) }
    private fun S() = com.cloneLocal.app.ui.Strings(arabic)

    // ------------------------------------------------------------ packs

    fun refreshPacks() { pack.ready = core.anyReady() }

    /** Downloads the base voices (Arabic, English, Chinese) and the voice-conversion models. */
    fun downloadPack() {
        if (pack.busy) return
        pack.busy = true; pack.error = null; pack.progress = 0f
        thread {
            try {
                core.release()
                // (label, weight of the progress bar)
                var done = 0f
                val langWeight = .06f
                for (l in Lang.values()) {
                    if (!core.baseReady(l)) {
                        val archive = File(ctx.cacheDir, "${l.code}.tar.bz2")
                        val from = done
                        download("$TTS_RELEASE/${l.archive}.tar.bz2", archive) { p -> post { pack.progress = from + p * langWeight } }
                        post { pack.extracting = true }
                        extractTarBz2(archive, core.baseDir(l))
                        archive.delete()
                        post { pack.extracting = false }
                    }
                    done += langWeight
                }
                core.ovDir.mkdirs()
                for ((name, span) in listOf("tone_extract.onnx" to .03f, "tone_color.onnx" to (1f - done - .03f))) {
                    val dest = File(core.ovDir, name)
                    if (!dest.exists()) {
                        val tmp = File(core.ovDir, "$name.part")
                        val from = done
                        download("$OPENVOICE_BASE/$name", tmp) { p -> post { pack.progress = from + p * span } }
                        tmp.renameTo(dest)
                    }
                    done += span
                }
                post { pack.progress = 1f; refreshPacks() }
            } catch (e: Exception) {
                post { pack.error = e.message ?: e.javaClass.simpleName }
            } finally {
                post { pack.busy = false; pack.extracting = false; refreshPacks() }
            }
        }
    }

    fun deletePack() { core.release(); core.baseRoot.deleteRecursively(); core.ovDir.deleteRecursively(); refreshPacks() }
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
    }

    fun selectVoice(v: Voice) {
        core.activeVoiceId = v.id
        activeId = v.id
    }

    fun deleteVoice(v: Voice) { core.deleteVoice(v); refreshVoices() }

    /** Decode the picked audio/video, keep the first 15 s as 22.05 kHz mono, and save it as a new voice. */
    fun addVoice(uri: Uri, displayName: String) {
        thread {
            try {
                val (samples, rate) = AudioDecoder.decode(ctx, uri, 15)
                val s22 = Resampler.resample(samples, rate, OpenVoice.SR)
                val base = displayName.substringBeforeLast('.').ifBlank { "Voice" }
                core.createVoice(base, s22)
                post {
                    refreshVoices()
                    if (gen is Gen.Error) gen = Gen.Idle
                }
            } catch (e: Exception) {
                post { gen = Gen.Error("${S().errRead}: ${e.message ?: ""}") }
            }
        }
    }

    /** Writes <name>.voxvoice.zip to Downloads/VoxGate: ref.wav, emb.bin (256 x float32 LE), meta.txt, README.txt. */
    fun exportVoice(v: Voice) {
        thread {
            val msg = try {
                val emb = core.ensureEmbedding(v)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${v.name}.voxvoice.zip")
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/VoxGate")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create file")
                java.util.zip.ZipOutputStream(ctx.contentResolver.openOutputStream(uri)!!).use { z ->
                    fun add(name: String, bytes: ByteArray) {
                        z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(bytes); z.closeEntry()
                    }
                    add("ref.wav", v.wav.readBytes())
                    if (emb != null) add("emb.bin", File(v.dir, "emb.bin").readBytes())
                    add("meta.txt", "${v.name}\n".toByteArray())
                    add(
                        "README.txt",
                        ("VoxGate voice file\n" +
                            "ref.wav  - mono 16-bit WAV, 22050 Hz, up to 15 s of the voice\n" +
                            "emb.bin  - OpenVoice V2 tone-colour embedding, 256 float32 little-endian (shape 1x256x1)\n" +
                            "meta.txt - the voice name\n").toByteArray(),
                    )
                }
                S().voiceExported
            } catch (e: Exception) {
                e.message ?: "export failed"
            }
            post { toast = msg }
        }
    }

    // ------------------------------------------------------------ synthesis

    fun generate() {
        val s = S()
        val text = inputText.trim()
        val voice = activeVoice
        val lang = VoxCore.detect(text)
        when {
            text.isEmpty() -> { gen = Gen.Error(s.errNoText); return }
            !core.baseReady(lang) || !OpenVoice.filesPresent(core.ovDir) -> { gen = Gen.Error(s.errNoPack); return }
            voice == null -> { gen = Gen.Error(s.errNoRef); return }
        }
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
        const val TTS_RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
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
