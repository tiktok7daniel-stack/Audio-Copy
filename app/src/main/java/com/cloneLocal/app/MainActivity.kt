package com.cloneLocal.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var modelUrl: EditText
    private lateinit var refText: EditText
    private lateinit var inputText: EditText
    private lateinit var refInfo: TextView
    private lateinit var busyButtons: List<Button>

    private var refSamples: FloatArray? = null
    private var refRate = 0
    private var tts: OfflineTts? = null
    private var lastSamples: FloatArray? = null
    private var lastRate = 0
    private var track: AudioTrack? = null

    private val modelRoot get() = File(filesDir, "models")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 15f; setPadding(0, pad, 0, 4) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        col.addView(TextView(this).apply { text = "Clone Local"; textSize = 24f })
        status = TextView(this).apply { text = "Ready."; setPadding(0, pad / 2, 0, pad / 2) }
        col.addView(status)

        col.addView(label("1) Model (downloaded once, ~hundreds of MB). Chinese + English only."))
        modelUrl = EditText(this).apply {
            setText(MODEL_URL)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            textSize = 12f
        }
        col.addView(modelUrl)
        val dl = button("Download model") { downloadModel() }
        col.addView(dl)

        col.addView(label("2) Reference voice: pick an audio or video file (first 15 s are used)"))
        val pick = button("Pick audio / video file") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
                },
                REQ_PICK,
            )
        }
        col.addView(pick)
        refInfo = TextView(this).apply { text = "No reference selected." }
        col.addView(refInfo)
        col.addView(label("Exact words spoken in the reference clip (required):"))
        refText = EditText(this).apply { minLines = 2; gravity = Gravity.TOP }
        col.addView(refText)

        col.addView(label("3) Text to speak in the cloned voice"))
        inputText = EditText(this).apply { minLines = 3; gravity = Gravity.TOP }
        col.addView(inputText)
        val gen = button("Generate") { generate() }
        col.addView(gen)
        val play = button("Play last result") { playLast() }
        col.addView(play)
        val save = button("Save as WAV (Music/CloneLocal)") { saveLast() }
        col.addView(save)

        busyButtons = listOf(dl, pick, gen)
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun setStatus(s: String) {
        ui.post { status.text = s }
    }
    private fun setBusy(b: Boolean) {
        ui.post { busyButtons.forEach { it.isEnabled = !b } }
    }

    // ---- model download -------------------------------------------------

    private fun downloadModel() {
        val url = modelUrl.text.toString().trim()
        setBusy(true)
        thread {
            try {
                modelRoot.mkdirs()
                val archive = File(cacheDir, "model.tar.bz2")
                download(url, archive, "Downloading model")
                setStatus("Extracting model…")
                extractTarBz2(archive, modelRoot)
                archive.delete()
                val vocoder = File(modelRoot, "vocos_24khz.onnx")
                if (!vocoder.exists()) download(VOCODER_URL, vocoder, "Downloading vocoder")
                tts?.release()
                tts = null
                setStatus("Model ready.")
            } catch (e: Exception) {
                setStatus("Download failed: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun download(url: String, dest: File, what: String) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} for $url")
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -1L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) done * 100 / total else done / (1 shl 20)
                        if (pct != lastPct) {
                            lastPct = pct
                            setStatus(if (total > 0) "$what: $pct%" else "$what: ${pct} MB")
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

    // ---- reference audio ------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || uri == null) return
        setBusy(true)
        setStatus("Decoding reference…")
        thread {
            try {
                val (samples, rate) = AudioDecoder.decode(this, uri, 15)
                refSamples = samples
                refRate = rate
                ui.post { refInfo.text = "Reference: %.1f s @ %d Hz".format(samples.size / rate.toFloat(), rate) }
                setStatus("Reference loaded.")
            } catch (e: Exception) {
                setStatus("Could not read file: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    // ---- synthesis ------------------------------------------------------

    private fun loadTts(): OfflineTts {
        tts?.let { return it }
        val files = modelRoot.walkTopDown().filter { it.isFile }.toList()
        fun onnx(key: String): String {
            val c = files.filter { it.extension == "onnx" && it.name.contains(key) }
            return (c.firstOrNull { it.name.contains("int8") } ?: c.firstOrNull())?.absolutePath
                ?: error("Missing '$key' model file. Download the model first.")
        }
        val tokens = files.firstOrNull { it.name == "tokens.txt" }?.absolutePath
            ?: error("Missing tokens.txt. Download the model first.")
        val lexicon = files.firstOrNull { it.name == "lexicon.txt" }?.absolutePath ?: ""
        val espeak = modelRoot.walkTopDown()
            .firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }?.absolutePath ?: ""
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                zipvoice = OfflineTtsZipVoiceModelConfig(
                    tokens = tokens,
                    encoder = onnx("encoder"),
                    decoder = onnx("decoder"),
                    vocoder = onnx("vocos"),
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

    private fun generate() {
        val ref = refSamples
        val text = inputText.text.toString().trim()
        val rt = refText.text.toString().trim()
        if (ref == null) return setStatus("Pick a reference file first.")
        if (rt.isEmpty()) return setStatus("Type the words spoken in the reference clip.")
        if (text.isEmpty()) return setStatus("Type the text to speak.")
        setBusy(true)
        setStatus("Generating… (can take a while on a phone)")
        thread {
            try {
                val engine = loadTts()
                val audio = engine.generateWithConfig(
                    text,
                    GenerationConfig(
                        referenceAudio = ref,
                        referenceSampleRate = refRate,
                        referenceText = rt,
                        numSteps = 5,
                        speed = 1.0f,
                    ),
                )
                lastSamples = audio.samples
                lastRate = audio.sampleRate
                setStatus("Done: %.1f s of audio.".format(audio.samples.size / audio.sampleRate.toFloat()))
                ui.post { playLast() }
            } catch (e: Throwable) {
                setStatus("Generation failed: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun playLast() {
        val s = lastSamples ?: return setStatus("Nothing generated yet.")
        track?.release()
        val pcm = ShortArray(s.size) { (s[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(lastRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also {
                it.write(pcm, 0, pcm.size)
                it.play()
            }
    }

    private fun saveLast() {
        val s = lastSamples ?: return setStatus("Nothing generated yet.")
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "clone_${System.currentTimeMillis()}.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/CloneLocal")
            }
            val uri: Uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            contentResolver.openOutputStream(uri)!!.use { WavWriter.write(it, s, lastRate) }
            setStatus("Saved to Music/CloneLocal.")
        } catch (e: Exception) {
            setStatus("Save failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        track?.release()
        tts?.release()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PICK = 1
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-zh-en-emilia.tar.bz2"
        private const val VOCODER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos_24khz.onnx"
    }
}
