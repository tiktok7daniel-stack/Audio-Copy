package com.cloneLocal.app

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Languages the app can speak. Each has a small base voice (Piper) whose timbre is then swapped for the cloned one. */
enum class Lang(val code: String, val archive: String) {
    AR("ar", "vits-piper-ar_JO-kareem-medium-int8"),
    EN("en", "vits-piper-en_US-lessac-medium-int8"),
    ZH("zh", "vits-piper-zh_CN-huayan-medium-int8"),
}

/** A saved cloned voice: a 22.05 kHz reference clip, its timbre embedding, and a name. No transcript is needed. */
class Voice(val id: String, val dir: File) {
    val wav get() = File(dir, "ref.wav")
    private val embFile get() = File(dir, "emb.bin")
    private val meta get() = File(dir, "meta.txt")

    val name: String get() = meta.takeIf { it.exists() }?.readText()?.lineSequence()?.firstOrNull().orEmpty().ifBlank { id }
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
 * Android TTS service, so a voice generated for another app uses the same loaded engines.
 *
 * Pipeline for every language: base voice speaks the text, then OpenVoice swaps in the cloned timbre.
 */
class VoxCore private constructor(private val ctx: Context) {
    val baseRoot = File(ctx.filesDir, "base")
    val ovDir = File(ctx.filesDir, "arabic/openvoice")
    val voicesDir = File(ctx.filesDir, "voices").also { it.mkdirs() }

    private val base = HashMap<Lang, OfflineTts>()
    private var ov: OpenVoice? = null

    private val prefs = ctx.getSharedPreferences("vox", Context.MODE_PRIVATE)
    var activeVoiceId: String?
        get() = prefs.getString("activeVoice", null)
        set(v) { prefs.edit().putString("activeVoice", v).apply() }
    var quality: Int
        get() = prefs.getInt("quality", 1)
        set(v) { prefs.edit().putInt("quality", v).apply() }

    // ---------------------------------------------------------------- availability

    fun baseDir(l: Lang) = File(baseRoot, l.code)

    private fun files(dir: File) = dir.walkTopDown().filter { it.isFile }.toList()

    private fun baseModel(l: Lang): File? = files(baseDir(l)).firstOrNull { it.extension == "onnx" }

    fun baseReady(l: Lang): Boolean =
        baseModel(l) != null && files(baseDir(l)).any { it.name == "tokens.txt" }

    /** A language can be spoken when its base voice and the voice-conversion models are installed. */
    fun langReady(l: Lang): Boolean = baseReady(l) && OpenVoice.filesPresent(ovDir)

    fun anyReady(): Boolean = Lang.values().any { langReady(it) }

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

    /** Timbre embedding of [v], computed once and cached; null until the voice-conversion models are installed. */
    fun ensureEmbedding(v: Voice): FloatArray? {
        v.readEmbedding()?.let { return it }
        if (!OpenVoice.filesPresent(ovDir)) return null
        return openVoice().embed(WavIO.read(v.wav).first).also { v.writeEmbedding(it) }
    }

    // ---------------------------------------------------------------- engines

    @Synchronized
    private fun baseEngine(l: Lang): OfflineTts {
        base[l]?.let { return it }
        val dir = baseDir(l)
        val f = files(dir)
        val model = baseModel(l)?.absolutePath ?: error("${l.code} voice model missing")
        val tokens = f.firstOrNull { it.name == "tokens.txt" }?.absolutePath ?: error("tokens.txt missing")
        val espeak = dir.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }?.absolutePath ?: ""
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = model, tokens = tokens, dataDir = espeak),
                numThreads = 4, debug = false, provider = "cpu",
            ),
        )
        return OfflineTts(config = cfg).also { base[l] = it }
    }

    @Synchronized
    private fun openVoice(): OpenVoice = ov ?: OpenVoice(ovDir).also { ov = it }

    @Synchronized
    fun release() {
        base.values.forEach { it.release() }
        base.clear()
        ov?.close(); ov = null
    }

    // ---------------------------------------------------------------- synthesis

    /**
     * Speak [text] in whatever language it is written in, with [voice]'s timbre.
     * With no voice the plain base voice answers. Returns mono samples and their sample rate.
     */
    fun synthesize(text: String, voice: Voice?, speed: Float = 1f): Pair<FloatArray, Int> {
        val lang = detect(text)
        if (!baseReady(lang)) error("The ${lang.code} voice pack is not downloaded")
        val a = baseEngine(lang).generate(text = text, sid = 0, speed = speed)
        val plain = Resampler.resample(a.samples, a.sampleRate, OpenVoice.SR)
        if (voice == null) return plain to OpenVoice.SR
        if (!OpenVoice.filesPresent(ovDir)) error("The voice-conversion models are not downloaded")
        val ov = openVoice()
        val emb = ensureEmbedding(voice) ?: error("Voice embedding unavailable")
        val tau = floatArrayOf(0.2f, 0.3f, 0.4f)[quality.coerceIn(0, 2)]
        return ov.convert(plain, emb, tau) to OpenVoice.SR
    }

    companion object {
        @Volatile private var instance: VoxCore? = null
        fun get(ctx: Context): VoxCore =
            instance ?: synchronized(this) { instance ?: VoxCore(ctx.applicationContext).also { instance = it } }

        /** Arabic letters win if present and dominant; CJK means Chinese; everything else is English. */
        fun detect(text: String): Lang {
            var ar = 0
            var cjk = 0
            var other = 0
            for (c in text) when {
                c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' -> ar++
                c in '一'..'鿿' -> cjk++
                c.isLetter() -> other++
            }
            return when {
                ar > 0 && ar >= cjk && ar >= other -> Lang.AR
                cjk > 0 && cjk >= other -> Lang.ZH
                else -> Lang.EN
            }
        }
    }
}
