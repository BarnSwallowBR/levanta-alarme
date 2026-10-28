package br.com.barnswallow.levanta.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.alarm.AlarmRingingService
import br.com.barnswallow.levanta.data.Alarm
import br.com.barnswallow.levanta.data.AlarmRepository
import br.com.barnswallow.levanta.data.Mission
import br.com.barnswallow.levanta.data.SettingsRepository
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        // Voltar não desliga o alarme: só a missão desliga.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent {
            LevantaTheme { RingingScreen(onFinished = { finish() }) }
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

private enum class Phase { RINGING, MATH, QR }

@Composable
private fun RingingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val ring by AlarmRingingService.state.collectAsStateWithLifecycle()

    // Fecha quando o alarme para (missão cumprida, soneca ou tempo esgotado).
    var sawRinging by remember { mutableStateOf(false) }
    LaunchedEffect(ring) {
        if (ring != null) {
            sawRinging = true
        } else if (sawRinging) {
            onFinished()
        } else {
            delay(2_000)
            if (AlarmRingingService.state.value == null) onFinished()
        }
    }

    val current = ring
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        if (current != null) {
            // Novo estado por alarme: se outro alarme da fila começar, a missão recomeça do zero.
            key(current.alarmId) { RingingContent(context, current) }
        }
    }
}

@Composable
private fun RingingContent(context: Context, ring: AlarmRingingService.RingState) {
    val alarm = remember(ring.alarmId) {
        AlarmRepository.get(context).find(ring.alarmId) ?: Alarm(id = ring.alarmId)
    }
    val qrCode by SettingsRepository.get(context).qrCode.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(Phase.RINGING) }
    var fallbackToHardMath by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }

    val touch: () -> Unit = {
        lastTouch = SystemClock.elapsedRealtime()
        AlarmRingingService.send(context, AlarmRingingService.ACTION_QUIET)
    }
    val solved: () -> Unit = {
        Toast.makeText(context, "Bom dia! Agora vai.", Toast.LENGTH_LONG).show()
        AlarmRingingService.send(context, AlarmRingingService.ACTION_DISMISS)
    }

    // Parou de mexer na missão: volta para a tela do alarme (e o volume volta ao máximo).
    LaunchedEffect(phase, lastTouch) {
        if (phase != Phase.RINGING) {
            delay(if (phase == Phase.QR) QR_IDLE_MS else MATH_IDLE_MS)
            phase = Phase.RINGING
        }
    }

    when (phase) {
        Phase.RINGING -> RingingIdle(
            alarm = alarm,
            ring = ring,
            onStartMission = {
                touch()
                phase = if (alarm.mission == Mission.QR && qrCode != null && !fallbackToHardMath) Phase.QR else Phase.MATH
            },
            onSnooze = { AlarmRingingService.send(context, AlarmRingingService.ACTION_SNOOZE) },
        )
        Phase.MATH -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (fallbackToHardMath) {
                MathMission(difficulty = 2, count = 5, onTouch = touch, onSolved = solved)
            } else {
                MathMission(
                    difficulty = alarm.mathDifficulty,
                    count = alarm.mathCount.coerceAtLeast(1),
                    onTouch = touch,
                    onSolved = solved,
                )
            }
        }
        Phase.QR -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            QrMission(
                expected = qrCode.orEmpty(),
                onTouch = touch,
                onSolved = solved,
                onGiveUp = {
                    touch()
                    fallbackToHardMath = true
                    phase = Phase.MATH
                },
            )
        }
    }
}

@Composable
private fun RingingIdle(
    alarm: Alarm,
    ring: AlarmRingingService.RingState,
    onStartMission: () -> Unit,
    onSnooze: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(5_000)
        }
    }
    val snoozesLeft = alarm.snoozeLimit - ring.snoozeCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 88.sp,
                fontWeight = FontWeight.Black,
            )
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            text = ring.phrase.ifBlank { "LEVANTA!" },
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStartMission,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Text("Desligar", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            if (snoozesLeft > 0) {
                TextButton(onClick = onSnooze) {
                    val plural = if (snoozesLeft == 1) "resta 1" else "restam $snoozesLeft"
                    Text("Soneca de ${alarm.snoozeMinutes} min ($plural)", fontSize = 16.sp)
                }
            } else {
                Text("Sem soneca hoje.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val MATH_IDLE_MS = 40_000L
private const val QR_IDLE_MS = 120_000L
