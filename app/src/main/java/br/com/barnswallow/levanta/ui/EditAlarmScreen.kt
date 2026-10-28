package br.com.barnswallow.levanta.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.alarm.AlarmScheduler
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import br.com.barnswallow.levanta.data.Mission
import br.com.barnswallow.levanta.data.SettingsRepository
import java.time.Duration
import java.time.ZonedDateTime

private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
private val WEEKEND = setOf(6, 7)
private val EVERY_DAY = (1..7).toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Int?, onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AlarmRepository.get(context) }
    val original = remember(alarmId) { alarmId?.let { repo.find(it) } }
    val base = original ?: Alarm(id = 0)
    val qrCode by SettingsRepository.get(context).qrCode.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    val time = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute, is24Hour = true)
    var days by remember { mutableStateOf(base.days) }
    var label by remember { mutableStateOf(base.label) }
    var mission by remember { mutableStateOf(base.mission) }
    var difficulty by remember { mutableIntStateOf(base.mathDifficulty.coerceIn(0, 2)) }
    var count by remember { mutableIntStateOf(base.mathCount.coerceIn(1, 10)) }
    var snoozeLimit by remember { mutableIntStateOf(base.snoozeLimit) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val alarm = base.copy(
            id = original?.id ?: repo.newId(),
            hour = time.hour,
            minute = time.minute,
            days = days,
            enabled = true,
            label = label.trim(),
            mission = mission,
            mathDifficulty = difficulty,
            mathCount = count,
            snoozeLimit = snoozeLimit,
        )
        AlarmScheduler.save(context, alarm)
        val now = ZonedDateTime.now()
        Toast.makeText(context, "Toca em ${formatDuration(Duration.between(now, alarm.nextTrigger(now)))}", Toast.LENGTH_SHORT).show()
        onDone()
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            ScreenHeader(title = if (original == null) "Novo alarme" else "Editar alarme", onBack = onDone) {
                if (original != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Apagar alarme")
                    }
                }
            }
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SunriseButton(text = "Salvar alarme", onClick = ::save, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.surfaceContainer)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    state = time,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = colors.surfaceContainerHigh,
                        selectorColor = colors.primary,
                        timeSelectorSelectedContainerColor = colors.primaryContainer,
                        timeSelectorSelectedContentColor = colors.primary,
                        timeSelectorUnselectedContainerColor = colors.surfaceContainerHigh,
                    ),
                )
            }

            SectionCard(title = "Repetir", subtitle = if (days.isEmpty()) "Sem dia marcado: toca uma vez só." else Alarm(id = 0, days = days).daysText()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { day ->
                        DayToggle(
                            letter = Alarm.DAY_LETTERS[day - 1],
                            name = Alarm.DAY_NAMES[day - 1],
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChoice("Úteis", days == WEEKDAYS, { days = WEEKDAYS }, Modifier.weight(1f))
                    PillChoice("Todo dia", days == EVERY_DAY, { days = EVERY_DAY }, Modifier.weight(1f))
                    PillChoice("Fim de sem.", days == WEEKEND, { days = WEEKEND }, Modifier.weight(1f))
                }
            }

            SectionCard(title = "Nome do alarme") {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    placeholder = { Text("Ex.: Academia, Trabalho") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Missão para desligar", subtitle = "O alarme só para quando você cumprir a missão.") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceTile(
                        title = "Contas",
                        description = "Resolve continhas rápidas no próprio celular.",
                        selected = mission == Mission.MATH,
                        onClick = { mission = Mission.MATH },
                        icon = LevantaIcons.Math,
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceTile(
                        title = "QR code",
                        description = "Te obriga a levantar e escanear o código cadastrado.",
                        selected = mission == Mission.QR,
                        onClick = { mission = Mission.QR },
                        icon = LevantaIcons.Qr,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (mission == Mission.QR) {
                    Spacer(Modifier.height(12.dp))
                    if (qrCode == null) {
                        Text(
                            "Nenhum código cadastrado ainda. Até cadastrar, o alarme usa as contas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                        )
                        TextButton(onClick = onOpenSettings) { Text("Cadastrar código agora") }
                    } else {
                        Text(
                            "Código cadastrado. Se não achar, dá para trocar por ${MathProblem.FALLBACK_COUNT} contas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            if (mission == Mission.MATH) {
                SectionCard(title = "Dificuldade das contas") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MathProblem.DIFFICULTY_NAMES.forEachIndexed { index, name ->
                            ChoiceTile(
                                title = name,
                                badge = MathProblem.DIFFICULTY_EXAMPLES[index],
                                description = MathProblem.DIFFICULTY_HINTS[index],
                                selected = difficulty == index,
                                onClick = { difficulty = index },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantidade de contas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Stepper(value = count, onChange = { count = it }, range = 1..10)
                    }
                }
            }

            SectionCard(title = "Soneca", subtitle = "Quantas vezes dá para adiar 5 minutos.") {
                PillRow(
                    options = listOf(0 to "Nenhuma", 1 to "1x", 2 to "2x", 3 to "3x"),
                    selected = snoozeLimit,
                    onSelect = { snoozeLimit = it },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar alarme das ${original.timeText()}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    AlarmScheduler.delete(context, original.id)
                    onDone()
                }) { Text("Apagar", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DayToggle(letter: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .border(1.dp, if (selected) Color.Transparent else colors.outlineVariant, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = "$name, ${if (selected) "ligado" else "desligado"}" },
    ) {
        Text(letter, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onPrimary else colors.onSurface)
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", "Menos uma conta", enabled = value > range.first) { onChange(value - 1) }
        Text(
            "$value",
            style = ClockStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize, letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing),
            color = colors.onSurface,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton("+", "Mais uma conta", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = if (enabled) colors.primary else colors.outline)
    }
}
