package br.com.barnswallow.levanta.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Escolhas de voz: voz neural empacotada, voz do sistema (TTS), velocidade e tom. */
class VoiceRepository private constructor(context: Context) {
    // Mesmo armazenamento protegido das outras configurações: o alarme lê isso antes do primeiro desbloqueio.
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("levanta_voice", Context.MODE_PRIVATE)

    private val _packagedVoice = MutableStateFlow(
        prefs.getString(KEY_PACKAGED, DEFAULT_PACKAGED)?.takeIf { it == OFF || it in PACKAGED_VOICES } ?: DEFAULT_PACKAGED,
    )

    /** Id da pasta em assets/voice (ex. "antonio") ou [OFF]. */
    val packagedVoice: StateFlow<String> = _packagedVoice.asStateFlow()

    private val _systemVoice = MutableStateFlow(prefs.getString(KEY_SYSTEM, null))

    /** Nome de android.speech.tts.Voice; null = voz padrão do motor em pt-BR. */
    val systemVoice: StateFlow<String?> = _systemVoice.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_RATE, DEFAULT_RATE))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(prefs.getFloat(KEY_PITCH, DEFAULT_PITCH))
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    fun setPackagedVoice(id: String) {
        prefs.edit().putString(KEY_PACKAGED, id).apply()
        _packagedVoice.value = id
    }

    fun setSystemVoice(name: String?) {
        prefs.edit().putString(KEY_SYSTEM, name).apply()
        _systemVoice.value = name
    }

    fun setSpeechRate(rate: Float) {
        val value = rate.coerceIn(MIN_RATE, MAX_RATE)
        prefs.edit().putFloat(KEY_RATE, value).apply()
        _speechRate.value = value
    }

    fun setPitch(pitch: Float) {
        val value = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        prefs.edit().putFloat(KEY_PITCH, value).apply()
        _pitch.value = value
    }

    companion object {
        private const val KEY_PACKAGED = "packaged_voice"
        private const val KEY_SYSTEM = "system_voice"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"

        const val OFF = "off"
        const val DEFAULT_PACKAGED = "antonio"
        const val DEFAULT_RATE = 0.95f
        const val DEFAULT_PITCH = 1f
        const val MIN_RATE = 0.6f
        const val MAX_RATE = 1.4f
        const val MIN_PITCH = 0.7f
        const val MAX_PITCH = 1.3f

        /** Vozes geradas por scripts/gerar-vozes.py (id da pasta -> nome na tela). */
        val PACKAGED_VOICES = linkedMapOf(
            "antonio" to "Antônio (masculina)",
            "francisca" to "Francisca (feminina)",
            "thalita" to "Thalita (feminina)",
        )

        @Volatile
        private var instance: VoiceRepository? = null

        fun get(context: Context): VoiceRepository =
            instance ?: synchronized(this) {
                instance ?: VoiceRepository(context.applicationContext).also { instance = it }
            }
    }
}
