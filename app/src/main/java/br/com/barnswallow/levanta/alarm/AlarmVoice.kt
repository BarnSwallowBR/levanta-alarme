package br.com.barnswallow.levanta.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import br.com.barnswallow.levanta.data.VoiceClips
import br.com.barnswallow.levanta.data.VoiceRepository
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Toca o som de alarme em loop e, por cima, fala uma frase motivacional a cada poucos segundos.
 * Cada frase sai do melhor áudio disponível (ver [VoiceClips]): gravação do usuário, voz neural
 * empacotada ou, por último, o TTS do sistema com a voz/velocidade/tom escolhidos nos ajustes.
 * O volume do canal de alarme começa em 40% e sobe até o máximo; quiet() abaixa por um tempo
 * enquanto a pessoa faz a missão.
 */
class AlarmVoice(
    private val context: Context,
    private val phrases: List<String>,
    private val onPhrase: (String) -> Unit,
) : TextToSpeech.OnInitListener {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    private val originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
    private val quietVolume = max(1, (maxVolume * 0.3f).roundToInt())

    private var currentVolume = max(1, (maxVolume * 0.4f).roundToInt())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var speechPlayer: MediaPlayer? = null
    private var speaking = false
    private var utteranceId: String? = null
    private val clips = VoiceClips.get(context)
    private val voices = VoiceRepository.get(context)
    private var quietUntil = 0L
    private var lastPhrase: String? = null
    private var stopped = false

    private val rampTick = object : Runnable {
        override fun run() {
            if (stopped) return
            if (!isQuiet() && currentVolume < maxVolume) currentVolume++
            applyVolume()
            handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

    private val speakNext = Runnable { speak() }

    // Se o TTS ou o MediaPlayer nunca avisarem que terminaram, as frases não podem parar de vez.
    private val speechTimeout = Runnable { afterSpeech() }

    fun start() {
        applyVolume()
        startPlayer()
        tts = TextToSpeech(context, this)
        handler.postDelayed(rampTick, RAMP_STEP_MS)
        // Áudios prontos não dependem do TTS: a primeira frase não espera o motor de voz carregar.
        handler.postDelayed(speakNext, FIRST_PHRASE_DELAY_MS)
    }

    fun quiet() {
        quietUntil = SystemClock.elapsedRealtime() + QUIET_MS
        applyVolume()
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        releaseSpeechPlayer()
        tone?.release()
        tone = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0) }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            // Pode chegar antes do construtor retornar (sem motor de voz): sobram só os áudios prontos.
            ttsFailed = true
            return
        }
        val engine = tts
        if (stopped || engine == null) return
        engine.setLanguage(Locale.forLanguageTag("pt-BR"))
        voices.systemVoice.value?.let { name ->
            runCatching { engine.voices?.firstOrNull { it.name == name } }.getOrNull()?.let { engine.voice = it }
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(voices.speechRate.value)
        engine.setPitch(voices.pitch.value)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                handler.post { afterUtterance(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { afterUtterance(utteranceId) }
            }
        })
        ttsReady = true
    }

    private fun speak() {
        if (stopped || speaking || phrases.isEmpty()) return
        val candidates = phrases.filter { it != lastPhrase }.ifEmpty { phrases }
        // Enquanto o TTS carrega (ou se ele falhou), só dá para falar frases com áudio pronto.
        val playable = if (ttsReady) candidates else candidates.filter { clips.resolve(it) != null }
        if (playable.isEmpty()) {
            if (!ttsFailed) handler.postDelayed(speakNext, WAIT_TTS_MS)
            return
        }
        val phrase = playable.random()
        lastPhrase = phrase
        onPhrase(phrase)
        speaking = true
        handler.postDelayed(speechTimeout, MAX_SPEECH_MS)
        player?.setVolume(DUCKED, DUCKED)
        val clip = clips.resolve(phrase)
        if (clip == null || !playClip(clip)) speakTts(phrase)
    }

    private fun speakTts(phrase: String) {
        if (!ttsReady) {
            afterSpeech()
            return
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        val id = "levanta-${SystemClock.elapsedRealtime()}"
        utteranceId = id
        val result = tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) afterSpeech()
    }

    // Um aviso atrasado de uma fala antiga não pode cortar o áudio que está tocando agora.
    private fun afterUtterance(id: String?) {
        if (id == utteranceId) afterSpeech()
    }

    /** Toca gravação ou voz empacotada no canal de alarme. Retorna false se nem conseguiu começar. */
    private fun playClip(clip: VoiceClips.Clip): Boolean {
        releaseSpeechPlayer()
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            when (clip) {
                is VoiceClips.Clip.Recorded -> mp.setDataSource(clip.file.absolutePath)
                is VoiceClips.Clip.Packaged -> context.assets.openFd(clip.assetPath).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            mp.setOnCompletionListener { afterSpeech() }
            mp.setOnErrorListener { _, _, _ ->
                afterSpeech()
                true
            }
            mp.prepare()
            mp.setVolume(1f, 1f)
            mp.start()
            speechPlayer = mp
            true
        } catch (e: Exception) {
            mp.release()
            false
        }
    }

    private fun releaseSpeechPlayer() {
        speechPlayer?.let { p ->
            p.setOnCompletionListener(null)
            p.setOnErrorListener(null)
            runCatching { p.stop() }
            p.release()
        }
        speechPlayer = null
    }

    private fun afterSpeech() {
        if (stopped) return
        speaking = false
        utteranceId = null
        handler.removeCallbacks(speechTimeout)
        releaseSpeechPlayer()
        player?.setVolume(1f, 1f)
        handler.removeCallbacks(speakNext)
        handler.postDelayed(speakNext, PAUSE_BETWEEN_PHRASES_MS)
    }

    private fun startPlayer() {
        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        player = candidates.firstNotNullOfOrNull { uri -> createPlayer(uri) }
        // Sem toque disponível (ex.: antes do primeiro desbloqueio): bipe gerado pelo sistema.
        if (player == null) {
            tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
            handler.post(beep)
        }
    }

    private val beep = object : Runnable {
        override fun run() {
            if (stopped) return
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MS)
            handler.postDelayed(this, BEEP_MS * 2L)
        }
    }

    private fun createPlayer(uri: Uri): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            mp
        } catch (e: Exception) {
            mp.release()
            null
        }
    }

    private fun isQuiet() = SystemClock.elapsedRealtime() < quietUntil

    private fun applyVolume() {
        val target = if (isQuiet()) min(currentVolume, quietVolume) else currentVolume
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
    }

    companion object {
        private const val RAMP_STEP_MS = 2_500L
        private const val QUIET_MS = 30_000L
        private const val FIRST_PHRASE_DELAY_MS = 1_500L
        private const val WAIT_TTS_MS = 1_000L
        private const val MAX_SPEECH_MS = 30_000L
        private const val PAUSE_BETWEEN_PHRASES_MS = 5_000L
        private const val DUCKED = 0.15f
        private const val BEEP_MS = 700
    }
}
