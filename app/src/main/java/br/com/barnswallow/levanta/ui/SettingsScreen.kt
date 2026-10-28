package br.com.barnswallow.levanta.ui

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.data.SettingsRepository
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val phrases by settings.phrases.collectAsStateWithLifecycle()
    val userName by settings.userName.collectAsStateWithLifecycle()
    val personName by settings.personName.collectAsStateWithLifecycle()
    val qrCode by settings.qrCode.collectAsStateWithLifecycle()
    val speak = rememberSpeaker()

    // null = fechado; -1 = frase nova; >= 0 = editando a frase desse índice.
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else Toast.makeText(context, "Precisa da câmera para ler o código", Toast.LENGTH_LONG).show()
    }

    if (scanning) {
        BackHandler { scanning = false }
        RegisterCodeOverlay(
            onError = {
                scanning = false
                Toast.makeText(context, "Não consegui abrir a câmera", Toast.LENGTH_LONG).show()
            },
            onCode = { code ->
                settings.setQrCode(code)
                scanning = false
                Toast.makeText(context, "Código cadastrado!", Toast.LENGTH_SHORT).show()
            },
            onCancel = { scanning = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frases e ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Nomes usados nas frases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = settings::setUserName,
                    label = { Text("Seu nome ou apelido — {nome}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = personName,
                    onValueChange = settings::setPersonName,
                    label = { Text("Quem te espera — {pessoa}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text("Código da missão QR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (qrCode == null) {
                        "Nenhum cadastrado. Pode ser um QR impresso ou o código de barras do shampoo, da pasta de dente… algo que fique longe da cama."
                    } else {
                        "Cadastrado. Para desligar o alarme você vai ter que escanear esse mesmo código."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (granted) scanning = true else cameraLauncher.launch(Manifest.permission.CAMERA)
                }) { Text(if (qrCode == null) "Cadastrar código" else "Trocar código") }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Frases (${phrases.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { editingIndex = -1 }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Nova")
                    }
                }
                Text(
                    "O alarme sorteia uma frase e fala em voz alta, uma depois da outra. Use {nome} e {pessoa} para os nomes acima.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            itemsIndexed(phrases) { index, phrase ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(phrase, modifier = Modifier.weight(1f))
                        IconButton(onClick = { speak(settings.render(phrase)) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Ouvir")
                        }
                        IconButton(onClick = { editingIndex = index }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { settings.setPhrases(phrases.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Apagar")
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restaurar frases padrão")
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text("Se o alarme não tocar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Xiaomi/Redmi: Configurações > Apps > Levanta! > Início automático (ligar) e Economia de bateria (sem restrições).\n" +
                        "Samsung: Configurações > Bateria > Limites de uso em segundo plano > tire o Levanta! de \"Apps em suspensão\".\n" +
                        "A voz usa o motor de fala do celular: se estiver em inglês, instale \"Português (Brasil)\" em Configurações > Idioma > Saída de texto para voz.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editingIndex?.let { index ->
        PhraseDialog(
            initial = if (index >= 0) phrases.getOrElse(index) { "" } else "",
            onDismiss = { editingIndex = null },
            onSave = { text ->
                settings.setPhrases(if (index >= 0) phrases.mapIndexed { i, p -> if (i == index) text else p } else phrases + text)
                editingIndex = null
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restaurar frases?") },
            text = { Text("Suas frases atuais serão trocadas pelas 20 frases padrão.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.resetPhrases()
                    confirmReset = false
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PhraseDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.isEmpty()) "Nova frase" else "Editar frase") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                placeholder = { Text("Ex.: {pessoa} está te esperando. Levanta!") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RegisterCodeOverlay(onError: () -> Unit, onCode: (String) -> Unit, onCancel: () -> Unit) {
    var captured by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Aponte para o QR ou código de barras que vai ficar longe da cama",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            QrScanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(20.dp)),
                onError = onError,
            ) { code ->
                if (!captured) {
                    captured = true
                    onCode(code)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

/** TTS só para ouvir a frase na tela de ajustes (o alarme usa o próprio motor de voz). */
@Composable
private fun rememberSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) holder[0]?.language = Locale.forLanguageTag("pt-BR")
        }
        holder[0] = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            holder[0] = null
        }
    }
    return remember {
        { text: String ->
            holder[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
            Unit
        }
    }
}
