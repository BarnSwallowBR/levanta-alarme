package br.com.barnswallow.levanta.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int) {
    companion object {
        fun random(difficulty: Int): MathProblem = when (difficulty) {
            0 -> {
                val a = Random.nextInt(10, 100)
                val b = Random.nextInt(10, 100)
                MathProblem("$a + $b", a + b)
            }
            1 -> {
                val a = Random.nextInt(11, 30)
                val b = Random.nextInt(3, 10)
                val c = Random.nextInt(10, 60)
                MathProblem("$a × $b + $c", a * b + c)
            }
            else -> {
                val a = Random.nextInt(11, 30)
                val b = Random.nextInt(11, 30)
                val c = Random.nextInt(100, 1000)
                MathProblem("$a × $b + $c", a * b + c)
            }
        }

        val DIFFICULTY_NAMES = listOf("Fácil", "Médio", "Difícil")
    }
}

@Composable
fun MathMission(difficulty: Int, count: Int, onTouch: () -> Unit, onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathProblem.random(difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Conta ${solved + 1} de $count",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { solved / count.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.height(32.dp))
        Text(problem.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                wrong -> "Errou. De novo."
                input.isEmpty() -> "= ?"
                else -> "= $input"
            },
            fontSize = 32.sp,
            color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))
        NumberPad(
            onDigit = { d ->
                onTouch()
                wrong = false
                if (input.length < 6) input += d
            },
            onBackspace = {
                onTouch()
                input = input.dropLast(1)
            },
            onConfirm = {
                onTouch()
                if (input.toIntOrNull() == problem.answer) {
                    solved++
                    input = ""
                    if (solved >= count) onSolved() else problem = MathProblem.random(difficulty)
                } else {
                    wrong = true
                    input = ""
                }
            },
        )
    }
}

@Composable
private fun NumberPad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onConfirm: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val keyModifier = Modifier.weight(1f).height(64.dp)
                    when (key) {
                        "OK" -> Button(onClick = onConfirm, modifier = keyModifier) {
                            Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        "⌫" -> FilledTonalButton(onClick = onBackspace, modifier = keyModifier) {
                            Text("⌫", fontSize = 22.sp)
                        }
                        else -> FilledTonalButton(onClick = { onDigit(key) }, modifier = keyModifier) {
                            Text(key, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrMission(expected: String, onTouch: () -> Unit, onSolved: () -> Unit, onGiveUp: () -> Unit) {
    val context = LocalContext.current
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
    var message by remember { mutableStateOf("Vá até o código cadastrado e aponte a câmera para ele") }
    var lastWrong by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (hasCamera) {
            QrScanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(20.dp)),
                onError = onGiveUp,
            ) { code ->
                if (done) return@QrScanner
                if (code == expected) {
                    done = true
                    onSolved()
                } else if (code != lastWrong) {
                    lastWrong = code
                    onTouch()
                    message = "Esse não é o código cadastrado. Procura o certo!"
                }
            }
        } else {
            Text(
                "Sem acesso à câmera não dá para ler o QR.",
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Permitir câmera")
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onGiveUp) {
            Text("Não acho o QR (5 contas difíceis)")
        }
    }
}
