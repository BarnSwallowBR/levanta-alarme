package br.com.barnswallow.levanta.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AlarmRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("levanta_alarms", Context.MODE_PRIVATE)
    private val _alarms = MutableStateFlow(loadAlarms())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun find(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(alarm: Alarm) {
        val list = _alarms.value.filterNot { it.id == alarm.id } + alarm
        saveAlarms(list.sortedBy { it.hour * 60 + it.minute })
    }

    @Synchronized
    fun delete(id: Int) {
        saveAlarms(_alarms.value.filterNot { it.id == id })
    }

    @Synchronized
    fun newId(): Int {
        val next = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, next + 1).apply()
        return next
    }

    @Synchronized
    fun snoozes(): Map<Int, Snooze> {
        val json = JSONObject(prefs.getString(KEY_SNOOZES, "{}") ?: "{}")
        return json.keys().asSequence().associate { key ->
            val item = json.getJSONObject(key)
            key.toInt() to Snooze(item.getLong("at"), item.getInt("count"))
        }
    }

    @Synchronized
    fun putSnooze(alarmId: Int, snooze: Snooze) {
        saveSnoozes(snoozes() + (alarmId to snooze))
    }

    @Synchronized
    fun removeSnooze(alarmId: Int) {
        saveSnoozes(snoozes() - alarmId)
    }

    private fun saveSnoozes(map: Map<Int, Snooze>) {
        val json = JSONObject()
        map.forEach { (id, s) -> json.put(id.toString(), JSONObject().put("at", s.triggerAt).put("count", s.count)) }
        prefs.edit().putString(KEY_SNOOZES, json.toString()).apply()
    }

    private fun loadAlarms(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { Alarm.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveAlarms(list: List<Alarm>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
        _alarms.value = list
    }

    companion object {
        private const val KEY_ALARMS = "alarms"
        private const val KEY_NEXT_ID = "next_id"
        private const val KEY_SNOOZES = "snoozes"

        @Volatile
        private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
    }
}
