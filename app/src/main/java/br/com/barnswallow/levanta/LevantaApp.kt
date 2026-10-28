package br.com.barnswallow.levanta

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

class LevantaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            "Alarme tocando",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Abre o alarme em tela cheia"
            // O som e a vibração vêm do próprio serviço, não da notificação.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_ring_v1"
    }
}
