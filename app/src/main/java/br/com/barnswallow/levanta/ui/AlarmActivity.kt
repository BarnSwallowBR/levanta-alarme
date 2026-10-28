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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
    var fallbackMath by remember { mutableStateOf(false) }
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

    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "phase",
    ) { target ->
        when (target) {
            Phase.RINGING -> RingingIdle(
                alarm = alarm,
                ring = ring,
                onStartMission = {
                    touch()
                    phase = if (alarm.mission == Mission.QR && qrCode != null && !fallbackMath) Phase.QR else Phase.MATH
                },
                onSnooze = { AlarmRingingService.send(context, AlarmRingingService.ACTION_SNOOZE) },
            )
            Phase.MATH -> MissionColumn {
                if (fallbackMath) {
                    MathMission(MathProblem.FALLBACK_DIFFICULTY, MathProblem.FALLBACK_COUNT, onTouch = touch, onSolved = solved)
                } else {
                    MathMission(alarm.mathDifficulty, alarm.mathCount.coerceAtLeast(1), onTouch = touch, onSolved = solved)
                }
            }
            Phase.QR -> MissionColumn {
                QrMission(
                    expected = qrCode.orEmpty(),
                    onTouch = touch,
                    onSolved = solved,
                    onGiveUp = {
                        touch()
                        fallbackMath = true
                        phase = Phase.MATH
                    },
                )
            }
        }
    }
}

@Composable
private fun MissionColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) { content() }
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
    val reduceMotion = rememberReduceMotion()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = if (reduceMotion) 0.35f else 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow",
    )
    val snoozesLeft = alarm.snoozeLimit - ring.snoozeCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Dawn.sky)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(120.dp)
                        .graphicsLayer { alpha = glow; scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Dawn.Sun.copy(alpha = 0.55f), Dawn.Sun.copy(alpha = 0f)))),
                )
                Icon(LevantaIcons.Sunrise, contentDescription = null, tint = Dawn.Sun, modifier = Modifier.size(56.dp))
            }
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = ClockStyle.copy(fontSize = 104.sp, lineHeight = 108.sp),
                color = Dawn.Ink,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            )
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = MaterialTheme.typography.titleLarge, color = Dawn.Mist)
            }
        }

        AnimatedContent(
            targetState = ring.phrase.ifBlank { "LEVANTA!" },
            transitionSpec = {
                (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }) togetherWith fadeOut(tween(200))
            },
            label = "phrase",
        ) { phrase ->
            Text(
                text = phrase,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
                textAlign = TextAlign.Center,
                color = Dawn.Ink,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            SunriseButton(
                text = "Desligar",
                onClick = onStartMission,
                height = 76.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Missão: ${missionText(alarm)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Dawn.Mist,
            )
            Spacer(Modifier.height(8.dp))
            if (snoozesLeft > 0) {
                TextButton(onClick = onSnooze, modifier = Modifier.height(48.dp)) {
                    val plural = if (snoozesLeft == 1) "resta 1" else "restam $snoozesLeft"
                    Text("Soneca de ${alarm.snoozeMinutes} min ($plural)", style = MaterialTheme.typography.labelLarge, color = Dawn.Sun)
                }
            } else {
                Text("Sem soneca hoje.", style = MaterialTheme.typography.labelLarge, color = Dawn.Mist)
            }
        }
    }
}

private const val MATH_IDLE_MS = 40_000L
private const val QR_IDLE_MS = 120_000L
