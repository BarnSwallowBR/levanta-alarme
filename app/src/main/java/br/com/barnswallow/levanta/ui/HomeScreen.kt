package br.com.barnswallow.levanta.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.alarm.AlarmRingingService
import br.com.barnswallow.levanta.alarm.AlarmScheduler
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAdd: () -> Unit, onEdit: (Int) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AlarmRepository.get(context) }
    val alarms by repo.alarms.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Levanta!", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Frases e ajustes") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo alarme") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionChecklist() }
            item { NextAlarmBanner(alarms) }
            if (alarms.isEmpty()) {
                item {
                    Text(
                        "Nenhum alarme ainda. Toque em \"Novo alarme\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onEdit(alarm.id) },
                    onToggle = { enabled -> AlarmScheduler.save(context, alarm.copy(enabled = enabled)) },
                    onTest = { AlarmRingingService.start(context, alarm.id) },
                )
            }
        }
    }
}

@Composable
private fun NextAlarmBanner(alarms: List<Alarm>) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = ZonedDateTime.now()
        }
    }
    val next = alarms.filter { it.enabled }.minOfOrNull { it.nextTrigger(now) }
    Text(
        text = if (next == null) "Nenhum alarme ligado" else "Próximo alarme em ${formatDuration(Duration.between(now, next))}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

fun formatDuration(d: Duration): String {
    val totalMinutes = (d.toMinutes() + 1).coerceAtLeast(1)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0 && days == 0L) add("${minutes}min")
    }.joinToString(" ")
}

@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onTest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    alarm.timeText(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                val details = listOfNotNull(alarm.label.ifBlank { null }, alarm.daysText(), alarm.mission.label)
                Text(details.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTest) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Testar este alarme agora")
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

private data class Issue(val title: String, val detail: String, val fix: () -> Unit)

/** Lista o que falta liberar para o alarme tocar com o celular bloqueado e em economia de bateria. */
@Composable
private fun PermissionChecklist() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        AlarmScheduler.rescheduleAll(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }
    val issues = remember(refresh) { findIssues(context) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
    if (issues.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "  Falta liberar para o alarme não falhar",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            issues.forEach { issue ->
                Spacer(Modifier.height(12.dp))
                Text(issue.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(issue.detail, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = issue.fix) { Text("Liberar") }
            }
        }
    }
}

private fun findIssues(context: Context, requestNotifications: () -> Unit): List<Issue> {
    val packageUri = Uri.parse("package:${context.packageName}")
    fun open(action: String) {
        val opened = runCatching { context.startActivity(Intent(action, packageUri)) }.isSuccess
        if (!opened) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
    return buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            add(Issue("Notificações", "Sem isso o alarme não aparece na tela.", requestNotifications))
        }
        if (!AlarmScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Issue("Alarmes exatos", "Sem isso o alarme pode tocar atrasado.") { open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) })
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            add(Issue("Tela cheia", "Deixa o alarme abrir por cima da tela bloqueada.") { open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) })
        }
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            add(Issue("Economia de bateria", "Impede o Android de matar o alarme durante a noite.") { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) })
        }
    }
}
