package br.com.barnswallow.levanta.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Toca o som de alarme em loop e, por cima, fala uma frase motivacional a cada poucos segundos.
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
    private var tts: TextToSpeech? = null
    private var ttsReady = false
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

    fun start() {
        applyVolume()
        startPlayer()
        tts = TextToSpeech(context, this)
        handler.postDelayed(rampTick, RAMP_STEP_MS)
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
        tts?.stop()
        tts?.shutdown()
        tts = null
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0) }
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (stopped || engine == null || status != TextToSpeech.SUCCESS) return
        engine.setLanguage(Locale.forLanguageTag("pt-BR"))
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(0.95f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                handler.post { afterSpeech() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { afterSpeech() }
            }
        })
        ttsReady = true
        handler.postDelayed(speakNext, FIRST_PHRASE_DELAY_MS)
    }

    private fun speak() {
        if (stopped || !ttsReady || phrases.isEmpty()) return
        val phrase = phrases.filter { it != lastPhrase }.ifEmpty { phrases }.random()
        lastPhrase = phrase
        onPhrase(phrase)
        player?.setVolume(DUCKED, DUCKED)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        val result = tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "levanta-${SystemClock.elapsedRealtime()}")
        if (result != TextToSpeech.SUCCESS) afterSpeech()
    }

    private fun afterSpeech() {
        if (stopped) return
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
        private const val PAUSE_BETWEEN_PHRASES_MS = 5_000L
        private const val DUCKED = 0.15f
    }
}
