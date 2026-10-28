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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.LevantaApp
import br.com.barnswallow.levanta.alarm.AlarmRingingService
import br.com.barnswallow.levanta.alarm.AlarmScheduler
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import br.com.barnswallow.levanta.data.Mission
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(onAdd: () -> Unit, onEdit: (Int) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AlarmRepository.get(context) }
    val alarms by repo.alarms.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenHeader(title = "Levanta!") {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Frases, voz e ajustes")
                }
            }
        },
        floatingActionButton = {
            SunriseButton(
                text = "Novo alarme",
                onClick = onAdd,
                icon = Icons.Default.Add,
                modifier = Modifier.navigationBarsPadding().width(200.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NextAlarmHero(alarms, onAdd) }
            item { PermissionChecklist() }
            if (alarms.isNotEmpty()) {
                item {
                    Text(
                        "Seus alarmes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onEdit(alarm.id) },
                    onToggle = { enabled -> AlarmScheduler.save(context, alarm.copy(enabled = enabled)) },
                    onTest = { AlarmRingingService.start(context, alarm.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** Card de destaque com o próximo alarme, no céu do amanhecer. */
@Composable
private fun NextAlarmHero(alarms: List<Alarm>, onAdd: () -> Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = ZonedDateTime.now()
        }
    }
    val next = alarms.filter { it.enabled }.minByOrNull { it.nextTrigger(now) }
    val shape = MaterialTheme.shapes.extraLarge

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dawn.sky, shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LevantaIcons.Sunrise, contentDescription = null, tint = Dawn.Sun, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Próximo alarme", style = MaterialTheme.typography.labelLarge, color = Dawn.Mist)
        }
        Spacer(Modifier.height(8.dp))
        if (next == null) {
            Text("Nenhum alarme ligado", style = MaterialTheme.typography.headlineMedium, color = Dawn.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Crie um alarme e escolha a missão que vai te tirar da cama.",
                style = MaterialTheme.typography.bodyMedium,
                color = Dawn.Mist,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAdd) { Text("Criar alarme") }
        } else {
            val at = next.nextTrigger(now)
            Text(next.timeText(), style = ClockStyle.copy(fontSize = 72.sp, lineHeight = 76.sp), color = Dawn.Ink)
            val day = at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"))
            Text(
                "Toca em ${formatDuration(Duration.between(now, at))} · $day",
                style = MaterialTheme.typography.titleMedium,
                color = Dawn.Sun,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(next.label.ifBlank { null }, "Missão: ${missionText(next)}").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Dawn.Mist,
            )
        }
    }
}

fun missionText(alarm: Alarm): String = when (alarm.mission) {
    Mission.QR -> "QR code"
    Mission.MATH -> "${alarm.mathCount} ${if (alarm.mathCount == 1) "conta" else "contas"} ${MathProblem.DIFFICULTY_NAMES[alarm.mathDifficulty.coerceIn(0, 2)].lowercase()}"
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
private fun AlarmCard(
    alarm: Alarm,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer, shape)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.55f)) {
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Text(alarm.timeText(), style = ClockStyle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alarm.isRepeating) {
                    DayDots(alarm.days, alarm.enabled)
                } else {
                    Text("Uma vez", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    if (alarm.mission == Mission.QR) LevantaIcons.Qr else LevantaIcons.Math,
                    contentDescription = alarm.mission.label,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .clickable(onClick = onTest),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Testar este alarme agora", tint = colors.primary)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.onPrimary,
            ),
        )
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

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.errorContainer)
            .padding(20.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                "Libere ${if (issues.size == 1) "1 item" else "${issues.size} itens"} para o alarme não falhar",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onErrorContainer,
            )
        }
        issues.forEach { issue ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(issue.title, fontWeight = FontWeight.SemiBold, color = colors.onErrorContainer)
                    Text(issue.detail, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = issue.fix) { Text("Liberar") }
            }
        }
    }
}

private fun findIssues(context: Context, requestNotifications: () -> Unit): List<Issue> {
    val packageUri = Uri.parse("package:${context.packageName}")
    fun open(intent: Intent) {
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
    fun open(action: String) = open(Intent(action, packageUri))
    val notifications = context.getSystemService(NotificationManager::class.java)
    return buildList {
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (permissionMissing) {
            add(Issue("Notificações", "Sem isso o alarme não aparece na tela.", requestNotifications))
        } else if (!notifications.areNotificationsEnabled()) {
            add(Issue("Notificações desligadas", "Sem isso o alarme não aparece na tela.") {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
        } else {
            val channel = notifications.getNotificationChannel(LevantaApp.CHANNEL_ALARM)
            if (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
                add(Issue("Aviso \"Alarme tocando\" silenciado", "Deixe como \"Urgente\"/\"Pop-up\" para o alarme abrir em tela cheia.") {
                    open(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, LevantaApp.CHANNEL_ALARM),
                    )
                })
            }
        }
        if (!AlarmScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Issue("Alarmes exatos", "Sem isso o alarme pode tocar atrasado.") { open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) })
        }
        if (Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()
        ) {
            add(Issue("Tela cheia", "Deixa o alarme abrir por cima da tela bloqueada.") { open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) })
        }
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            add(Issue("Economia de bateria", "Impede o Android de matar o alarme durante a noite.") { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) })
        }
    }
}
