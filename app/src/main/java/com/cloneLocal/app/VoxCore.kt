package com.cloneLocal.app

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A saved cloned voice: a 22.05 kHz reference clip, its timbre embedding, and the clip's transcript. */
class Voice(val id: String, val dir: File) {
    val wav get() = File(dir, "ref.wav")
    private val embFile get() = File(dir, "emb.bin")
    private val meta get() = File(dir, "meta.txt")

    val name: String get() = meta.takeIf { it.exists() }?.readText()?.lineSequence()?.firstOrNull().orEmpty().ifBlank { id }
    var refText: String
        get() = meta.takeIf { it.exists() }?.readText()?.substringAfter('\n', "").orEmpty()
        set(v) { meta.writeText("$name\n$v") }
    val seconds: Float get() = WavIO.seconds(wav)

    fun readEmbedding(): FloatArray? {
        if (!embFile.exists()) return null
        val bb = ByteBuffer.wrap(embFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(OpenVoice.EMB) { bb.getFloat() }
    }

    fun writeEmbedding(e: FloatArray) {
        val bb = ByteBuffer.allocate(e.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        e.forEach { bb.putFloat(it) }
        embFile.writeBytes(bb.array())
    }
}

/**
 * Everything that touches models. One instance per process, shared by the app UI and the
 * Android TTS service, so a voice generated for the reader app uses the same loaded engines.
 */
class VoxCore private constructor(private val ctx: Context) {
    val zipDir = File(ctx.filesDir, "models")
    val piperDir = File(ctx.filesDir, "arabic/piper")
    val ovDir = File(ctx.filesDir, "arabic/openvoice")
    val voicesDir = File(ctx.filesDir, "voices").also { it.mkdirs() }

    private var zip: OfflineTts? = null
    private var piper: OfflineTts? = null
    private var ov: OpenVoice? = null

    private val prefs = ctx.getSharedPreferences("vox", Context.MODE_PRIVATE)
    var activeVoiceId: String?
        get() = prefs.getString("activeVoice", null)
        set(v) { prefs.edit().putString("activeVoice", v).apply() }
    var quality: Int
        get() = prefs.getInt("quality", 1)
        set(v) { prefs.edit().putInt("quality", v).apply() }

    // ---------------------------------------------------------------- availability

    private fun files(dir: File) = dir.walkTopDown().filter { it.isFile }.toList()

    private fun pickOnnx(files: List<File>, key: String): String? {
        val c = files.filter { it.extension == "onnx" && it.name.contains(key) }
        return (c.firstOrNull { it.name.contains("int8") } ?: c.firstOrNull())?.absolutePath
    }

    fun zipReady(): Boolean {
        val f = files(zipDir)
        return pickOnnx(f, "encoder") != null && pickOnnx(f, "decoder") != null &&
            pickOnnx(f, "vocos") != null && f.any { it.name == "tokens.txt" }
    }

    private fun piperModel(): File? = files(piperDir).firstOrNull { it.extension == "onnx" }

    fun arabicReady(): Boolean =
        piperModel() != null && files(piperDir).any { it.name == "tokens.txt" } && OpenVoice.filesPresent(ovDir)

    // ---------------------------------------------------------------- voices

    fun listVoices(): List<Voice> =
        (voicesDir.listFiles { f -> f.isDirectory } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { Voice(it.name, it) }
            .filter { it.wav.exists() }

    fun activeVoice(): Voice? = listVoices().let { l -> l.firstOrNull { it.id == activeVoiceId } ?: l.firstOrNull() }

    /** [samples22k] must already be mono 22.05 kHz. */
    fun createVoice(name: String, samples22k: FloatArray): Voice {
        val id = "v${System.currentTimeMillis()}"
        val dir = File(voicesDir, id).also { it.mkdirs() }
        dir.resolve("meta.txt").writeText("$name\n")
        dir.resolve("ref.wav").outputStream().use { WavWriter.write(it, samples22k, OpenVoice.SR) }
        activeVoiceId = id
        return Voice(id, dir)
    }

    fun deleteVoice(v: Voice) {
        v.dir.deleteRecursively()
        if (activeVoiceId == v.id) activeVoiceId = null
    }

    // ---------------------------------------------------------------- engines

    @Synchronized
    private fun zipEngine(): OfflineTts {
        zip?.let { return it }
        val f = files(zipDir)
        val tokens = f.firstOrNull { it.name == "tokens.txt" }?.absolutePath ?: error("tokens.txt missing")
        val lexicon = f.firstOrNull { it.name == "lexicon.txt" }?.absolutePath ?: ""
        val espeak = zipDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }?.absolutePath ?: ""
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                zipvoice = OfflineTtsZipVoiceModelConfig(
                    tokens = tokens,
                    encoder = pickOnnx(f, "encoder") ?: error("encoder missing"),
                    decoder = pickOnnx(f, "decoder") ?: error("decoder missing"),
                    vocoder = pickOnnx(f, "vocos") ?: error("vocoder missing"),
                    lexicon = lexicon,
                    dataDir = espeak,
                ),
                numThreads = 4, debug = false, provider = "cpu",
            ),
        )
        return OfflineTts(config = cfg).also { zip = it }
    }

    @Synchronized
    private fun piperEngine(): OfflineTts {
        piper?.let { return it }
        val f = files(piperDir)
        val model = piperModel()?.absolutePath ?: error("Arabic voice model missing")
        val tokens = f.firstOrNull { it.name == "tokens.txt" }?.absolutePath ?: error("tokens.txt missing")
        val espeak = piperDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }?.absolutePath ?: ""
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = model, tokens = tokens, dataDir = espeak),
                numThreads = 4, debug = false, provider = "cpu",
            ),
        )
        return OfflineTts(config = cfg).also { piper = it }
    }

    @Synchronized
    private fun openVoice(): OpenVoice = ov ?: OpenVoice(ovDir).also { ov = it }

    @Synchronized
    fun release() {
        zip?.release(); zip = null
        piper?.release(); piper = null
        ov?.close(); ov = null
    }

    // ---------------------------------------------------------------- synthesis

    /**
     * Speak [text]. Arabic text goes through the Arabic voice + tone-colour conversion into [voice];
     * everything else uses ZipVoice zero-shot cloning (needs [voice]'s transcript).
     * Returns mono samples and their sample rate.
     */
    fun synthesize(text: String, voice: Voice?, speed: Float = 1f): Pair<FloatArray, Int> {
        val steps = intArrayOf(3, 5, 8)[quality.coerceIn(0, 2)]
        return if (isArabic(text)) {
            if (!arabicReady()) error("Arabic pack is not downloaded")
            val a = piperEngine().generate(text = text, sid = 0, speed = speed)
            val base = Resampler.resample(a.samples, a.sampleRate, OpenVoice.SR)
            if (voice == null) return base to OpenVoice.SR
            val ov = openVoice()
            val emb = voice.readEmbedding() ?: ov.embed(WavIO.read(voice.wav).first).also { voice.writeEmbedding(it) }
            val tau = floatArrayOf(0.2f, 0.3f, 0.4f)[quality.coerceIn(0, 2)]
            ov.convert(base, emb, tau) to OpenVoice.SR
        } else {
            if (!zipReady()) error("English/Chinese pack is not downloaded")
            val v = voice ?: error("Pick a reference voice first")
            val rt = v.refText.trim()
            if (rt.isEmpty()) error("Type the words spoken in the reference clip")
            val (ref, rate) = WavIO.read(v.wav)
            val audio = zipEngine().generateWithConfig(
                text,
                GenerationConfig(referenceAudio = ref, referenceSampleRate = rate, referenceText = rt, numSteps = steps, speed = speed),
            )
            audio.samples to audio.sampleRate
        }
    }

    companion object {
        @Volatile private var instance: VoxCore? = null
        fun get(ctx: Context): VoxCore =
            instance ?: synchronized(this) { instance ?: VoxCore(ctx.applicationContext).also { instance = it } }

        fun isArabic(text: String): Boolean {
            var ar = 0
            var other = 0
            for (c in text) when {
                c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' -> ar++
                c.isLetter() -> other++
            }
            return ar > 0 && ar >= other
        }
    }
}
