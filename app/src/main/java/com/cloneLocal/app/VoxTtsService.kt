package com.cloneLocal.app

import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import java.util.Locale

/**
 * Registers VoxGate as a system speech engine, so any reader app can speak with the active cloned voice.
 * Every language: a small base voice speaks, then OpenVoice swaps in the cloned timbre.
 */
class VoxTtsService : TextToSpeechService() {
    @Volatile private var stopped = false
    private val core get() = VoxCore.get(this)

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        when (lang?.lowercase(Locale.ROOT)) {
            "ara", "ar" -> if (core.langReady(Lang.AR)) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
            "eng", "en" -> if (core.langReady(Lang.EN)) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
            "zho", "zh", "chi" -> if (core.langReady(Lang.ZH)) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }

    override fun onGetLanguage(): Array<String> = arrayOf("ara", "", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    /**
     * Every saved cloned voice is offered as a voice of its own, once for Arabic and once for English, so a reader app
     * can give the narrator, the men and the women different ones. Each voice speaks any text: the script of the text
     * picks the language at synthesis time (see [VoxCore.synthesize]).
     */
    override fun onGetVoices(): List<android.speech.tts.Voice> {
        val named = voiceNames()
        val out = ArrayList<android.speech.tts.Voice>()
        for (lang in listOf("ar", "en")) {
            for (name in named.keys) {
                out += android.speech.tts.Voice("$name · $lang", Locale(lang), android.speech.tts.Voice.QUALITY_NORMAL, android.speech.tts.Voice.LATENCY_NORMAL, false, emptySet())
            }
        }
        // Before any voice is saved the two plain voices still answer, with the engine's default sound
        if (named.isEmpty()) {
            out += android.speech.tts.Voice(VOICE_AR, Locale("ar"), android.speech.tts.Voice.QUALITY_NORMAL, android.speech.tts.Voice.LATENCY_NORMAL, false, emptySet())
            out += android.speech.tts.Voice(VOICE_EN, Locale("en"), android.speech.tts.Voice.QUALITY_NORMAL, android.speech.tts.Voice.LATENCY_NORMAL, false, emptySet())
        }
        return out
    }

    /** The saved voices by the name they are offered under: their own name, with a short tag when two share one. */
    private fun voiceNames(): Map<String, Voice> {
        val voices = core.listVoices().sortedBy { it.id }
        val seen = HashMap<String, Int>()
        val out = LinkedHashMap<String, Voice>()
        for (v in voices) {
            val base = v.name.ifBlank { v.id }
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            out[if (n == 1) base else "$base #$n"] = v
        }
        return out
    }

    /** The saved voice a requested name stands for: "<name> · ar", "<name> · en", or null for the plain ones. */
    private fun voiceFor(requested: String?): Voice? {
        if (requested.isNullOrBlank() || requested == VOICE_AR || requested == VOICE_EN) return null
        return voiceNames()[requested.substringBeforeLast(" · ")]
    }

    override fun onIsValidVoiceName(name: String?): Int =
        if (name == VOICE_AR || name == VOICE_EN || voiceFor(name) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(name: String?): Int = onIsValidVoiceName(name)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val en = lang?.lowercase(Locale.ROOT) in setOf("eng", "en", "zho", "zh", "chi")
        val active = core.activeVoice()
        val name = voiceNames().entries.firstOrNull { it.value.id == active?.id }?.key
        return when {
            name != null -> "$name · ${if (en) "en" else "ar"}"
            en -> VOICE_EN
            else -> VOICE_AR
        }
    }

    override fun onStop() { stopped = true }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val text = request.charSequenceText?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { callback.done(); return }
        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2f)
        try {
            // The voice the reader app asked for by name, else the one picked in VoxGate
            val voice = voiceFor(request.voiceName) ?: core.activeVoice()
            callback.start(OpenVoice.SR, AudioFormat.ENCODING_PCM_16BIT, 1)
            for (sentence in text.split(Regex("(?<=[.!?؟۔…\\n])\\s+")).filter { it.isNotBlank() }) {
                if (stopped) return
                val (samples, rate) = core.synthesize(sentence, voice, speed)
                val pcm = if (rate == OpenVoice.SR) samples else Resampler.resample(samples, rate, OpenVoice.SR)
                val bytes = ByteArray(pcm.size * 2)
                for (i in pcm.indices) {
                    val s = (pcm[i].coerceIn(-1f, 1f) * 32767).toInt()
                    bytes[2 * i] = (s and 0xFF).toByte()
                    bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                }
                val max = callback.maxBufferSize
                var off = 0
                while (off < bytes.size && !stopped) {
                    val n = minOf(max, bytes.size - off)
                    callback.audioAvailable(bytes, off, n)
                    off += n
                }
            }
            if (!stopped) callback.done()
        } catch (e: Throwable) {
            callback.error()
        }
    }

    companion object {
        const val VOICE_AR = "voxgate-ar"
        const val VOICE_EN = "voxgate-en"
    }
}

/** Answers CHECK_TTS_DATA so apps know which languages this engine can speak right now. */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val core = VoxCore.get(this)
        val ok = ArrayList<String>()
        val missing = ArrayList<String>()
        (if (core.langReady(Lang.AR)) ok else missing).add("ara")
        (if (core.langReady(Lang.EN)) ok else missing).add("eng")
        (if (core.langReady(Lang.ZH)) ok else missing).add("zho")
        setResult(
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
            Intent()
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, ok)
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, missing),
        )
        finish()
    }
}
