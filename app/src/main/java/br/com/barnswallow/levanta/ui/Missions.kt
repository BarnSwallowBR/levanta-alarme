package br.com.barnswallow.levanta.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int) {
    companion object {
        /** 0 = 1 dígito + 1 dígito; 1 = soma/subtração de 2 dígitos; 2 = tabuada. */
        fun random(difficulty: Int): MathProblem = when (difficulty) {
            0 -> {
                val a = Random.nextInt(2, 10)
                val b = Random.nextInt(2, 10)
                MathProblem("$a + $b", a + b)
            }
            1 -> if (Random.nextBoolean()) {
                val a = Random.nextInt(11, 50)
                val b = Random.nextInt(11, 50)
                MathProblem("$a + $b", a + b)
            } else {
                val a = Random.nextInt(30, 100)
                val b = Random.nextInt(11, a - 5)
                MathProblem("$a − $b", a - b)
            }
            else -> if (Random.nextInt(3) == 0) {
                val a = Random.nextInt(11, 16)
                val b = Random.nextInt(2, 6)
                MathProblem("$a × $b", a * b)
            } else {
                val a = Random.nextInt(3, 10)
                val b = Random.nextInt(3, 10)
                MathProblem("$a × $b", a * b)
            }
        }

        val DIFFICULTY_NAMES = listOf("Fácil", "Médio", "Difícil")
        val DIFFICULTY_EXAMPLES = listOf("7 + 5", "23 + 18", "7 × 8")
        val DIFFICULTY_HINTS = listOf("Soma de 1 dígito", "Soma ou subtração", "Tabuada")

        /** Missão de reserva quando a pessoa não acha o QR. */
        const val FALLBACK_DIFFICULTY = 1
        const val FALLBACK_COUNT = 5
    }
}

@Composable
fun MathMission(difficulty: Int, count: Int, onTouch: () -> Unit, onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathProblem.random(difficulty)) }
    var input by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf(Feedback.NONE) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val colors = MaterialTheme.colorScheme

    val cardColor by animateColorAsState(
        when (feedback) {
            Feedback.RIGHT -> Dawn.Mint.copy(alpha = 0.22f)
            Feedback.WRONG -> Dawn.Coral.copy(alpha = 0.22f)
            Feedback.NONE -> colors.surfaceContainer
        },
        animationSpec = tween(180),
        label = "feedback",
    )
    LaunchedEffect(feedback) {
        if (feedback != Feedback.NONE) {
            delay(450)
            feedback = Feedback.NONE
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Resolva para desligar", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        ProgressDots(done = solved, total = count)
        Spacer(Modifier.height(20.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value }
                .clip(MaterialTheme.shapes.extraLarge)
                .background(cardColor)
                .padding(vertical = 28.dp, horizontal = 16.dp),
        ) {
            Text(problem.text, style = ClockStyle.copy(fontSize = 56.sp, lineHeight = 60.sp), color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    feedback == Feedback.WRONG -> "Quase! Tenta de novo"
                    feedback == Feedback.RIGHT -> "Isso!"
                    input.isEmpty() -> "= ?"
                    else -> "= $input"
                },
                style = ClockStyle.copy(fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
                color = when (feedback) {
                    Feedback.WRONG -> Dawn.Coral
                    Feedback.RIGHT -> Dawn.Mint
                    Feedback.NONE -> colors.primary
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        NumberPad(
            onDigit = { d ->
                onTouch()
                if (input.length < 4) input += d
            },
            onBackspace = {
                onTouch()
                input = input.dropLast(1)
            },
            onConfirm = {
                onTouch()
                if (input.isNotEmpty()) {
                    if (input.toIntOrNull() == problem.answer) {
                        solved++
                        input = ""
                        feedback = Feedback.RIGHT
                        if (solved >= count) onSolved() else problem = MathProblem.random(difficulty)
                    } else {
                        input = ""
                        feedback = Feedback.WRONG
                        if (!reduceMotion) {
                            scope.launch {
                                for (x in listOf(-22f, 22f, -14f, 14f, -6f, 0f)) shake.animateTo(x, tween(40))
                            }
                        }
                    }
                }
            },
        )
    }
}

private enum class Feedback { NONE, RIGHT, WRONG }

@Composable
private fun ProgressDots(done: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val current = i == done
            Box(
                Modifier
                    .size(if (current) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            i < done -> Dawn.Mint
                            current -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

@Composable
private fun NumberPad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onConfirm: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val isOk = key == "OK"
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(shape)
                            .then(if (isOk) Modifier.background(Dawn.sunrise, shape) else Modifier.background(colors.surfaceContainerHigh, shape))
                            .clickable(role = Role.Button) {
                                when (key) {
                                    "OK" -> onConfirm()
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key)
                                }
                            },
                    ) {
                        Text(
                            key,
                            style = ClockStyle.copy(fontSize = if (isOk) 22.sp else 30.sp, letterSpacing = 0.sp),
                            color = if (isOk) Dawn.OnSun else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QrMission(expected: String, onTouch: () -> Unit, onSolved: () -> Unit, onGiveUp: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    var message by remember { mutableStateOf("Levanta e vai até o código cadastrado") }
    var wrong by remember { mutableStateOf(false) }
    var lastWrong by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = if (wrong) Dawn.Coral else colors.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        if (hasCamera) {
            QrScanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .border(3.dp, if (wrong) Dawn.Coral else colors.primary, MaterialTheme.shapes.extraLarge),
                onError = onGiveUp,
            ) { code ->
                if (done) return@QrScanner
                if (code == expected) {
                    done = true
                    onSolved()
                } else if (code != lastWrong) {
                    lastWrong = code
                    wrong = true
                    onTouch()
                    message = "Esse não é o código cadastrado"
                }
            }
        } else {
            Text("Sem acesso à câmera não dá para ler o código.", color = Dawn.Coral, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onGiveUp, modifier = Modifier.height(52.dp)) {
            Text("Não acho o código (${MathProblem.FALLBACK_COUNT} contas)")
        }
    }
}
