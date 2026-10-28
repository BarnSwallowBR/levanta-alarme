package br.com.barnswallow.levanta.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import br.com.barnswallow.levanta.data.VoiceClips
import br.com.barnswallow.levanta.data.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Toca frases na tela de ajustes do mesmo jeito que o alarme (gravação > voz neural > TTS),
 * mas no canal de mídia. Um só motor de TTS para a tela inteira: as linhas de frase dividem
 * a mesma instância via [rememberVoicePreview].
 */
class VoicePreview private constructor(private val context: Context) {

    private val clips = VoiceClips.get(context)
    private val settings = VoiceRepository.get(context)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingText: String? = null
    private var player: MediaPlayer? = null
    private var users = 0
    private var generation = 0

    private val _voices = MutableStateFlow<List<Voice>?>(null)

    /** Vozes em português do motor de TTS; null enquanto o motor carrega. */
    val voices: StateFlow<List<Voice>?> = _voices.asStateFlow()

    /** Toca a frase pela mesma ordem do alarme. */
    fun play(text: String) {
        val clip = clips.resolve(text)
        if (clip == null || !playClip(clip)) speakSystem(text)
    }

    /** Amostra de uma voz empacotada, mesmo que não seja a escolhida. */
    fun playPackaged(voiceId: String, text: String) {
        if (!clips.hasPackaged(text, voiceId) || !playClip(VoiceClips.Clip.Packaged("voice/$voiceId/${VoiceClips.key(text)}.mp3"))) {
            speakSystem(text)
        }
    }

    /** Fala com a voz do sistema escolhida, velocidade e tom atuais. */
    fun speakSystem(text: String) {
        stop()
        val engine = tts ?: return
        if (!ttsReady) {
            pendingText = text
            return
        }
        applySettings(engine)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    fun stop() {
        pendingText = null
        tts?.stop()
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
    }

    private fun applySettings(engine: TextToSpeech) {
        val chosen = settings.systemVoice.value?.let { name -> _voices.value?.firstOrNull { it.name == name } }
        if (chosen != null) engine.voice = chosen else engine.language = Locale.forLanguageTag("pt-BR")
        engine.setSpeechRate(settings.speechRate.value)
        engine.setPitch(settings.pitch.value)
    }

    private fun playClip(clip: VoiceClips.Clip): Boolean {
        stop()
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            when (clip) {
                is VoiceClips.Clip.Recorded -> mp.setDataSource(clip.file.absolutePath)
                is VoiceClips.Clip.Packaged -> context.assets.openFd(clip.assetPath).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            mp.setOnCompletionListener { done ->
                done.release()
                if (player === done) player = null
            }
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            mp.release()
            false
        }
    }

    fun acquire() {
        if (users++ > 0) return
        val mine = ++generation
        // Sem motor de voz o Android chama isto antes do construtor retornar, com tts ainda nulo.
        tts = TextToSpeech(context) { status ->
            if (mine != generation) return@TextToSpeech
            val engine = tts
            if (engine == null || status != TextToSpeech.SUCCESS) {
                _voices.value = emptyList()
                return@TextToSpeech
            }
            ttsReady = true
            _voices.value = portugueseVoices(engine)
            pendingText?.let { speakSystem(it) }
        }
    }

    fun release() {
        if (--users > 0) return
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        generation++
        _voices.value = null
    }

    companion object {
        private var instance: VoicePreview? = null

        fun get(context: Context): VoicePreview =
            instance ?: VoicePreview(context.applicationContext).also { instance = it }

        /** pt-BR primeiro, depois as que funcionam sem internet e as de melhor qualidade. */
        fun portugueseVoices(engine: TextToSpeech): List<Voice> =
            runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
                .filter { it.locale.language == "pt" && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
                .sortedWith(
                    compareByDescending<Voice> { it.locale.country == "BR" }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.name },
                )
    }
}

@Composable
fun rememberVoicePreview(): VoicePreview {
    val context = LocalContext.current
    val preview = remember { VoicePreview.get(context) }
    DisposableEffect(preview) {
        preview.acquire()
        onDispose { preview.release() }
    }
    return preview
}
