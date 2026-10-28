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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.alarm.AlarmScheduler
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import br.com.barnswallow.levanta.data.Mission
import br.com.barnswallow.levanta.data.SettingsRepository
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Int?, onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AlarmRepository.get(context) }
    val original = remember(alarmId) { alarmId?.let { repo.find(it) } }
    val base = original ?: Alarm(id = 0)
    val qrCode by SettingsRepository.get(context).qrCode.collectAsStateWithLifecycle()

    val time = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute, is24Hour = true)
    var days by remember { mutableStateOf(base.days) }
    var label by remember { mutableStateOf(base.label) }
    var mission by remember { mutableStateOf(base.mission) }
    var difficulty by remember { mutableIntStateOf(base.mathDifficulty) }
    var count by remember { mutableIntStateOf(base.mathCount) }
    var snoozeLimit by remember { mutableIntStateOf(base.snoozeLimit) }

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
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Novo alarme" else "Editar alarme") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = {
                            AlarmScheduler.delete(context, original.id)
                            onDone()
                        }) { Icon(Icons.Default.Delete, contentDescription = "Apagar alarme") }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .height(56.dp),
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = time)

            SectionTitle("Repetir")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { day ->
                    DayToggle(
                        letter = Alarm.DAY_LETTERS[day - 1],
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                    )
                }
            }
            Text(
                if (days.isEmpty()) "Sem dia marcado: toca uma vez só." else Alarm(id = 0, days = days).daysText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionTitle("Nome do alarme")
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = { Text("Ex.: Academia") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Missão para desligar")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Mission.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mission == m,
                        onClick = { mission = m },
                        shape = SegmentedButtonDefaults.itemShape(index, Mission.entries.size),
                    ) { Text(m.label) }
                }
            }

            if (mission == Mission.QR) {
                Spacer(Modifier.height(8.dp))
                if (qrCode == null) {
                    Text(
                        "Nenhum código cadastrado ainda. Até cadastrar, o alarme cai na matemática.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onOpenSettings) { Text("Cadastrar código agora") }
                } else {
                    Text(
                        "Você vai ter que levantar e escanear o código cadastrado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                SectionTitle("Dificuldade")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MathProblem.DIFFICULTY_NAMES.forEachIndexed { index, name ->
                        SegmentedButton(
                            selected = difficulty == index,
                            onClick = { difficulty = index },
                            shape = SegmentedButtonDefaults.itemShape(index, MathProblem.DIFFICULTY_NAMES.size),
                        ) { Text(name) }
                    }
                }
                val example = remember(difficulty) { MathProblem.random(difficulty).text }
                Text(
                    "Exemplo: $example",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SectionTitle("Quantidade de contas: $count")
                Slider(
                    value = count.toFloat(),
                    onValueChange = { count = it.roundToInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }

            SectionTitle("Soneca")
            val snoozeOptions = listOf("Nenhuma", "1x", "2x", "3x")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                snoozeOptions.forEachIndexed { index, name ->
                    SegmentedButton(
                        selected = snoozeLimit == index,
                        onClick = { snoozeLimit = index },
                        shape = SegmentedButtonDefaults.itemShape(index, snoozeOptions.size),
                    ) { Text(name) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh, CircleShape)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text(letter, fontWeight = FontWeight.Bold, color = if (selected) colors.onPrimary else colors.onSurface)
    }
}
