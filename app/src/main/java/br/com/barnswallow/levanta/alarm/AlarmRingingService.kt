package br.com.barnswallow.levanta.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.barnswallow.levanta.LevantaApp
import br.com.barnswallow.levanta.R
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import br.com.barnswallow.levanta.data.SettingsRepository
import br.com.barnswallow.levanta.ui.AlarmActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Serviço em primeiro plano que segura o alarme tocando até a missão ser cumprida. */
class AlarmRingingService : Service() {

    data class RingState(val alarmId: Int, val snoozeCount: Int, val phrase: String = "")

    private var voice: AlarmVoice? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging() }
    private val pending = ArrayDeque<Pair<Int, Int>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startRinging(
                    intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
                    intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0),
                )
                // Se o sistema matar o serviço, ele volta e toca de novo.
                return START_REDELIVER_INTENT
            }
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> snooze()
            ACTION_QUIET -> voice?.quiet()
        }
        // Comandos que chegam sem alarme tocando não podem deixar o serviço vivo à toa.
        if (_state.value == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun startRinging(alarmId: Int, snoozeCount: Int) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(alarmId),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        val current = _state.value
        if (current != null) {
            // Outro alarme já está tocando: este entra na fila e toca quando o atual for resolvido.
            if (current.alarmId != alarmId && pending.none { it.first == alarmId }) pending.addLast(alarmId to snoozeCount)
            return
        }
        beginRinging(alarmId, snoozeCount)
    }

    private fun beginRinging(alarmId: Int, snoozeCount: Int) {
        _state.value = RingState(alarmId, snoozeCount)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "levanta:ringing")
            .apply { acquire(MAX_RING_MS) }

        val settings = SettingsRepository.get(this)
        voice = AlarmVoice(this, settings.renderedPhrases()) { phrase ->
            _state.update { it?.copy(phrase = phrase) }
        }.also { it.start() }
        startVibration()
        handler.postDelayed(timeout, MAX_RING_MS)

        // Com a tela desbloqueada o Android pode bloquear isso; a notificação em tela cheia cobre o resto.
        runCatching { startActivity(AlarmActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun snooze() {
        val current = _state.value ?: return
        val alarm = AlarmRepository.get(this).find(current.alarmId) ?: Alarm(id = current.alarmId)
        if (current.snoozeCount >= alarm.snoozeLimit) return
        AlarmScheduler.snooze(this, current.alarmId, alarm.snoozeMinutes, current.snoozeCount + 1)
        stopRinging()
    }

    private fun stopRinging() {
        releaseResources()
        _state.value = null
        val next = pending.removeFirstOrNull()
        if (next != null) {
            beginRinging(next.first, next.second)
            return
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        handler.removeCallbacks(timeout)
        voice?.stop()
        voice = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        pending.clear()
        releaseResources()
        _state.value = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attrs)
        vibrator = v
    }

    private fun buildNotification(alarmId: Int): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            alarmId,
            AlarmActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, LevantaApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Levanta!")
            .setContentText("Cumpra a missão para desligar o alarme")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_START = "br.com.barnswallow.levanta.RING_START"
        const val ACTION_DISMISS = "br.com.barnswallow.levanta.RING_DISMISS"
        const val ACTION_SNOOZE = "br.com.barnswallow.levanta.RING_SNOOZE"
        const val ACTION_QUIET = "br.com.barnswallow.levanta.RING_QUIET"
        private const val NOTIFICATION_ID = 42
        private const val MAX_RING_MS = 30 * 60 * 1000L

        private val _state = MutableStateFlow<RingState?>(null)
        val state: StateFlow<RingState?> = _state.asStateFlow()

        fun start(context: Context, alarmId: Int, snoozeCount: Int = 0) {
            val intent = Intent(context, AlarmRingingService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            runCatching {
                context.startService(Intent(context, AlarmRingingService::class.java).setAction(action))
            }
        }
    }
}
