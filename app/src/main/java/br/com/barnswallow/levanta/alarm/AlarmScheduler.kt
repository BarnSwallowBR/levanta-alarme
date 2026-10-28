package br.com.barnswallow.levanta.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import br.com.barnswallow.levanta.data.Snooze
import br.com.barnswallow.levanta.ui.MainActivity
import java.time.ZonedDateTime

object AlarmScheduler {
    const val ACTION_FIRE = "br.com.barnswallow.levanta.FIRE"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SNOOZE_COUNT = "snooze_count"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    fun schedule(context: Context, alarm: Alarm, from: ZonedDateTime = ZonedDateTime.now()) {
        val operation = firePendingIntent(context, alarm.id, snooze = false, snoozeCount = 0)
        alarmManager(context).cancel(operation)
        if (!alarm.enabled) return
        setExact(context, alarm.nextTrigger(from).toInstant().toEpochMilli(), operation)
    }

    fun cancel(context: Context, alarmId: Int) {
        alarmManager(context).cancel(firePendingIntent(context, alarmId, snooze = false, snoozeCount = 0))
        cancelSnooze(context, alarmId)
    }

    fun cancelSnooze(context: Context, alarmId: Int) {
        alarmManager(context).cancel(firePendingIntent(context, alarmId, snooze = true, snoozeCount = 0))
        AlarmRepository.get(context).removeSnooze(alarmId)
    }

    fun snooze(context: Context, alarmId: Int, minutes: Int, snoozeCount: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        AlarmRepository.get(context).putSnooze(alarmId, Snooze(at, snoozeCount))
        setExact(context, at, firePendingIntent(context, alarmId, snooze = true, snoozeCount = snoozeCount))
    }

    /** Recoloca tudo no AlarmManager (o Android apaga os alarmes ao reiniciar ou atualizar o app). */
    fun rescheduleAll(context: Context) {
        val repo = AlarmRepository.get(context)
        repo.alarms.value.forEach { schedule(context, it) }
        val now = System.currentTimeMillis()
        repo.snoozes().forEach { (id, snooze) ->
            if (snooze.triggerAt > now) {
                setExact(context, snooze.triggerAt, firePendingIntent(context, id, snooze = true, snoozeCount = snooze.count))
            } else {
                repo.removeSnooze(id)
            }
        }
    }

    fun save(context: Context, alarm: Alarm) {
        AlarmRepository.get(context).upsert(alarm)
        cancelSnooze(context, alarm.id)
        schedule(context, alarm)
    }

    fun delete(context: Context, alarmId: Int) {
        cancel(context, alarmId)
        AlarmRepository.get(context).delete(alarmId)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    private fun setExact(context: Context, triggerAt: Long, operation: PendingIntent) {
        val manager = alarmManager(context)
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExact(context)) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun firePendingIntent(context: Context, alarmId: Int, snooze: Boolean, snoozeCount: Int): PendingIntent {
        val kind = if (snooze) "snooze" else "main"
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("levanta://alarm/$alarmId/$kind"))
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, snooze)
            .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        val requestCode = alarmId * 2 + if (snooze) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)
}
