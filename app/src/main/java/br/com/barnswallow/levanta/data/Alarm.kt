package br.com.barnswallow.levanta.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

enum class Mission(val label: String) {
    MATH("Matemática"),
    QR("QR code"),
}

/** Dias da semana seguem java.time.DayOfWeek: 1 = segunda ... 7 = domingo. */
data class Alarm(
    val id: Int,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val enabled: Boolean = true,
    val label: String = "",
    val mission: Mission = Mission.MATH,
    val mathDifficulty: Int = 0,
    val mathCount: Int = 3,
    val snoozeLimit: Int = 1,
    val snoozeMinutes: Int = 5,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    fun nextTrigger(from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var candidate = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(from)) candidate = candidate.plusDays(1)
        if (days.isNotEmpty()) {
            while (candidate.dayOfWeek.value !in days) candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun daysText(): String = when {
        days.isEmpty() -> "Uma vez"
        days.size == 7 -> "Todos os dias"
        days == setOf(1, 2, 3, 4, 5) -> "Dias úteis"
        days == setOf(6, 7) -> "Fim de semana"
        else -> days.sorted().joinToString(" ") { DAY_NAMES[it - 1] }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", JSONArray(days.sorted()))
        .put("enabled", enabled)
        .put("label", label)
        .put("mission", mission.name)
        .put("mathDifficulty", mathDifficulty)
        .put("mathCount", mathCount)
        .put("snoozeLimit", snoozeLimit)
        .put("snoozeMinutes", snoozeMinutes)

    companion object {
        val DAY_NAMES = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
        val DAY_LETTERS = listOf("S", "T", "Q", "Q", "S", "S", "D")

        fun fromJson(json: JSONObject): Alarm {
            val daysJson = json.optJSONArray("days") ?: JSONArray()
            return Alarm(
                id = json.getInt("id"),
                hour = json.optInt("hour", 7),
                minute = json.optInt("minute", 0),
                days = (0 until daysJson.length()).map { daysJson.getInt(it) }.toSet(),
                enabled = json.optBoolean("enabled", true),
                label = json.optString("label", ""),
                mission = runCatching { Mission.valueOf(json.optString("mission")) }
                    .getOrDefault(Mission.MATH),
                mathDifficulty = json.optInt("mathDifficulty", 0),
                mathCount = json.optInt("mathCount", 3),
                snoozeLimit = json.optInt("snoozeLimit", 1),
                snoozeMinutes = json.optInt("snoozeMinutes", 5),
            )
        }
    }
}

data class Snooze(val triggerAt: Long, val count: Int)
