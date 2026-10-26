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
 * Arabic text -> Arabic voice converted to the cloned timbre; English/Chinese -> ZipVoice cloning.
 */
class VoxTtsService : TextToSpeechService() {
    @Volatile private var stopped = false
    private val core get() = VoxCore.get(this)

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        when (lang?.lowercase(Locale.ROOT)) {
            "ara", "ar" -> if (core.arabicReady()) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
            "eng", "en", "zho", "zh", "chi" -> if (core.zipReady()) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }

    override fun onGetLanguage(): Array<String> = arrayOf("ara", "", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetVoices(): List<android.speech.tts.Voice> = listOf(
        android.speech.tts.Voice(VOICE_AR, Locale("ar"), android.speech.tts.Voice.QUALITY_NORMAL, android.speech.tts.Voice.LATENCY_NORMAL, false, emptySet()),
        android.speech.tts.Voice(VOICE_EN, Locale("en"), android.speech.tts.Voice.QUALITY_NORMAL, android.speech.tts.Voice.LATENCY_NORMAL, false, emptySet()),
    )

    override fun onIsValidVoiceName(name: String?): Int =
        if (name == VOICE_AR || name == VOICE_EN) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(name: String?): Int = onIsValidVoiceName(name)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String =
        if (lang?.lowercase(Locale.ROOT) in setOf("eng", "en", "zho", "zh", "chi")) VOICE_EN else VOICE_AR

    override fun onStop() { stopped = true }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val text = request.charSequenceText?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { callback.done(); return }
        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2f)
        try {
            val voice = core.activeVoice()
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
        (if (core.arabicReady()) ok else missing).add("ara")
        (if (core.zipReady()) ok else missing).add("eng")
        setResult(
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
            Intent()
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, ok)
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, missing),
        )
        finish()
    }
}
