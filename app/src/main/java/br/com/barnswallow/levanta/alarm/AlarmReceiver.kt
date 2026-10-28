package br.com.barnswallow.levanta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.barnswallow.levanta.data.AlarmRepository
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        val repo = AlarmRepository.get(context)

        if (isSnooze) {
            repo.removeSnooze(alarmId)
        } else {
            val alarm = repo.find(alarmId) ?: return
            if (!alarm.enabled) return
            if (alarm.isRepeating) {
                // +1 min evita reagendar para este mesmo minuto se o alarme disparar uns ms adiantado.
                AlarmScheduler.schedule(context, alarm, from = ZonedDateTime.now().plusMinutes(1))
            } else {
                repo.upsert(alarm.copy(enabled = false))
            }
        }

        AlarmRingingService.start(context, alarmId, snoozeCount)
    }
}
