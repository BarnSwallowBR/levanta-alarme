package br.com.barnswallow.levanta.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.data.SettingsRepository

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val phrases by settings.phrases.collectAsStateWithLifecycle()
    val userName by settings.userName.collectAsStateWithLifecycle()
    val personName by settings.personName.collectAsStateWithLifecycle()
    val qrCode by settings.qrCode.collectAsStateWithLifecycle()
    val preview = rememberVoicePreview()
    val colors = MaterialTheme.colorScheme

    // Gravação só vale com a tela aberta: sair ou bloquear o celular descarta.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { PhraseRecorder.cancel() }
    DisposableEffect(Unit) { onDispose { PhraseRecorder.cancel() } }

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
        containerColor = colors.background,
        topBar = { ScreenHeader(title = "Ajustes", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Nomes nas frases", subtitle = "Entram no lugar de {nome} e {pessoa}.") {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = settings::setUserName,
                        label = { Text("Seu nome ou apelido · {nome}") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = personName,
                        onValueChange = settings::setPersonName,
                        label = { Text("Quem te espera · {pessoa}") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(20.dp),
                ) { VoiceSettingsSection() }
            }

            item {
                SectionCard(
                    title = "Código da missão QR",
                    subtitle = if (qrCode == null) {
                        "Pode ser um QR impresso ou o código de barras do shampoo, da pasta de dente… algo longe da cama."
                    } else {
                        "Cadastrado. Para desligar, você vai ter que escanear esse mesmo código."
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LevantaIcons.Qr, contentDescription = null, tint = if (qrCode == null) colors.outline else colors.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(14.dp))
                        FilledTonalButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (granted) scanning = true else cameraLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text(if (qrCode == null) "Cadastrar código" else "Trocar código") }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Frases (${phrases.size})", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "O alarme sorteia uma e fala em voz alta, uma depois da outra.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { editingIndex = -1 }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Nova")
                    }
                }
            }

            itemsIndexed(phrases) { index, phrase ->
                val rendered = settings.render(phrase)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(rendered, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 4.dp))
                        IconButton(onClick = { preview.play(rendered) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Ouvir", tint = colors.primary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhraseVoiceControls(rendered, Modifier.weight(1f))
                        IconButton(onClick = { editingIndex = index }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar frase")
                        }
                        IconButton(onClick = { settings.setPhrases(phrases.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Apagar frase")
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Restaurar frases padrão")
                }
            }

            item {
                SectionCard(title = "Se o alarme não tocar") {
                    Text(
                        "Xiaomi/Redmi: Configurações > Apps > Levanta! > Início automático (ligar) e Economia de bateria (sem restrições).\n\n" +
                            "Samsung: Configurações > Bateria > Limites de uso em segundo plano > tire o Levanta! de \"Apps em suspensão\".\n\n" +
                            "Voz do celular em inglês? Instale \"Português (Brasil)\" em Configurações > Idioma > Saída de texto para voz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
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
            text = { Text("Suas frases atuais serão trocadas pelas 20 frases padrão. Gravações das frases que continuarem iguais são mantidas.") },
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
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    placeholder = { Text("Ex.: {pessoa} está te esperando. Levanta!") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Frase nova ou editada usa a sua gravação (se gravar) ou a voz do celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Aponte para o QR ou código de barras que vai ficar longe da cama",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        QrScanner(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.extraLarge)
                .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge),
            onError = onError,
        ) { code ->
            if (!captured) {
                captured = true
                onCode(code)
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.height(52.dp)) { Text("Cancelar") }
    }
}
