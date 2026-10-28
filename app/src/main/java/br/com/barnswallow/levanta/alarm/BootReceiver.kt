package br.com.barnswallow.levanta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reinício, atualização do app, mudança de hora/fuso e permissão de alarme exato. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> AlarmScheduler.rescheduleAll(context)
        }
    }
}
