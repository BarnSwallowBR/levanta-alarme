package br.com.barnswallow.levanta.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.data.SettingsRepository
import br.com.barnswallow.levanta.data.VoiceRepository
import java.util.Locale

/**
 * Ajustes de voz: qual voz neural do app usar, qual voz do celular usar quando não houver áudio
 * pronto, e velocidade/tom dessa voz. Bloco independente para encaixar na tela de ajustes.
 */
@Composable
fun VoiceSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { VoiceRepository.get(context) }
    val preview = rememberVoicePreview()
    val packaged by repo.packagedVoice.collectAsStateWithLifecycle()
    val systemVoice by repo.systemVoice.collectAsStateWithLifecycle()
    val savedRate by repo.speechRate.collectAsStateWithLifecycle()
    val savedPitch by repo.pitch.collectAsStateWithLifecycle()
    val voices by preview.voices.collectAsStateWithLifecycle()
    val sample = SettingsRepository.DEFAULT_PHRASES.first()

    Column(modifier) {
        Text("Voz das frases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Cada frase usa a melhor voz que tiver: sua gravação, depois a voz natural do app, depois a voz do celular. Tudo funciona sem internet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Text("Voz natural do app", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Já vem gravada para as 20 frases padrão. Frase editada ou nome trocado usa sua gravação ou a voz do celular.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            VoiceRepository.PACKAGED_VOICES.forEach { (id, label) ->
                ChoiceRow(
                    label = label,
                    selected = packaged == id,
                    onSelect = { repo.setPackagedVoice(id) },
                    onListen = { preview.playPackaged(id, sample) },
                )
            }
            ChoiceRow(
                label = "Desligada (só voz do celular)",
                selected = packaged == VoiceRepository.OFF,
                onSelect = { repo.setPackagedVoice(VoiceRepository.OFF) },
                onListen = null,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Voz do celular", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Usada nas frases sem áudio pronto. Prefira uma marcada \"sem internet\": às 6h o Wi-Fi pode estar desligado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        SystemVoicePicker(
            voices = voices,
            selected = systemVoice,
            onSelect = repo::setSystemVoice,
        )

        var rate by remember(savedRate) { mutableFloatStateOf(savedRate) }
        var pitch by remember(savedPitch) { mutableFloatStateOf(savedPitch) }
        Spacer(Modifier.height(8.dp))
        Text("Velocidade: ${"%.2f".format(Locale.forLanguageTag("pt-BR"), rate)}x", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = rate,
            onValueChange = { rate = it },
            onValueChangeFinished = { repo.setSpeechRate(rate) },
            valueRange = VoiceRepository.MIN_RATE..VoiceRepository.MAX_RATE,
        )
        Text("Tom: ${"%.2f".format(Locale.forLanguageTag("pt-BR"), pitch)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = pitch,
            onValueChange = { pitch = it },
            onValueChangeFinished = { repo.setPitch(pitch) },
            valueRange = VoiceRepository.MIN_PITCH..VoiceRepository.MAX_PITCH,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { preview.speakSystem(sample) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(" Ouvir")
            }
            OutlinedButton(onClick = {
                repo.setSpeechRate(VoiceRepository.DEFAULT_RATE)
                repo.setPitch(VoiceRepository.DEFAULT_PITCH)
            }) { Text("Padrão") }
            TextButton(onClick = {
                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure { Toast.makeText(context, "Abra Configurações > Idioma > Saída de texto para voz", Toast.LENGTH_LONG).show() }
            }) { Text("Baixar vozes") }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, onListen: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (onListen != null) {
            IconButton(onClick = onListen) { Icon(Icons.Default.PlayArrow, contentDescription = "Ouvir $label") }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SystemVoicePicker(voices: List<Voice>?, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = voices?.firstOrNull { it.name == selected }
    val title = when {
        voices == null -> "Carregando vozes…"
        selected == null -> "Padrão do celular (português)"
        current == null -> "Voz escolhida não está mais instalada"
        else -> voiceLabel(current)
    }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = voices != null, modifier = Modifier.fillMaxWidth()) {
            Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Padrão do celular (português)") },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            voices.orEmpty().forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voiceLabel(voice)) },
                    onClick = {
                        onSelect(voice.name)
                        open = false
                    },
                )
            }
        }
    }
    if (voices != null && voices.isEmpty()) {
        Text(
            "Nenhuma voz em português instalada no celular. Toque em \"Baixar vozes\" e instale Português (Brasil).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Ex.: "pt-BR · afs · muito boa · sem internet". */
private fun voiceLabel(voice: Voice): String {
    val tag = voice.locale.toLanguageTag()
    // Nomes do Google são como "pt-br-x-afs-local": o trecho do meio identifica a voz.
    val id = voice.name.removePrefix(tag.lowercase()).removePrefix("-x-").substringBefore("-").ifBlank { voice.name }
    val quality = when {
        voice.quality >= Voice.QUALITY_VERY_HIGH -> "muito boa"
        voice.quality >= Voice.QUALITY_HIGH -> "boa"
        voice.quality >= Voice.QUALITY_NORMAL -> "normal"
        else -> "baixa"
    }
    val net = if (voice.isNetworkConnectionRequired) "precisa de internet" else "sem internet"
    return "$tag · $id · $quality · $net"
}
