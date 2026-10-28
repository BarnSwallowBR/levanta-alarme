package br.com.barnswallow.levanta.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.barnswallow.levanta.data.VoiceClips
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Uma gravação por vez no app inteiro; a frase gravando aparece em [recordingText]. */
object PhraseRecorder {
    private var recorder: MediaRecorder? = null
    private var temp: File? = null
    private val _recordingText = MutableStateFlow<String?>(null)
    val recordingText: StateFlow<String?> = _recordingText.asStateFlow()

    fun start(context: Context, text: String): Boolean {
        stopAndSave(context)
        val clips = VoiceClips.get(context)
        val file = clips.newRecordingTemp()
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96_000)
            mr.setAudioSamplingRate(44_100)
            mr.setMaxDuration(MAX_RECORDING_MS)
            mr.setOutputFile(file.absolutePath)
            val appContext = context.applicationContext
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndSave(appContext)
            }
            mr.prepare()
            mr.start()
            recorder = mr
            temp = file
            _recordingText.value = text
            true
        } catch (e: Exception) {
            mr.release()
            file.delete()
            false
        }
    }

    /** Descarta a gravação em andamento (saiu da tela, bloqueou o celular). */
    fun cancel() {
        val mr = recorder ?: return
        runCatching { mr.stop() }
        mr.release()
        recorder = null
        temp?.delete()
        temp = null
        _recordingText.value = null
    }

    /** Para e guarda como a voz da frase. Gravação curta demais (stop falha) é descartada. */
    fun stopAndSave(context: Context): Boolean {
        val mr = recorder ?: return false
        val file = temp
        val text = _recordingText.value
        val stopped = runCatching { mr.stop() }.isSuccess
        mr.release()
        recorder = null
        temp = null
        _recordingText.value = null
        if (!stopped || file == null || text == null) {
            file?.delete()
            return false
        }
        return VoiceClips.get(context).commitRecording(text, file)
    }

    private const val MAX_RECORDING_MS = 20_000
}

/**
 * Gravar / parar / apagar a voz de uma frase. [text] é a frase já com {nome}/{pessoa} trocados,
 * a mesma que o alarme vai procurar em [VoiceClips].
 */
@Composable
fun PhraseVoiceControls(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clips = VoiceClips.get(context)
    val recordedKeys by clips.recorded.collectAsStateWithLifecycle()
    val recordingText by PhraseRecorder.recordingText.collectAsStateWithLifecycle()
    val preview = rememberVoicePreview()
    val hasRecording = VoiceClips.key(text) in recordedKeys
    val isRecording = recordingText == text

    fun startRecording() {
        preview.stop()
        if (!PhraseRecorder.start(context, text)) {
            Toast.makeText(context, "Não consegui usar o microfone", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else Toast.makeText(context, "Precisa do microfone para gravar", Toast.LENGTH_LONG).show()
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            when {
                isRecording -> "Gravando… (até 20 s)"
                hasRecording -> "Sua voz"
                clips.hasPackaged(text) -> "Voz natural"
                else -> "Voz do celular"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (isRecording) {
            TextButton(onClick = {
                if (PhraseRecorder.stopAndSave(context)) {
                    Toast.makeText(context, "Gravação salva", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gravação muito curta, tente de novo", Toast.LENGTH_SHORT).show()
                }
            }) { Text("■ Parar") }
        } else {
            TextButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (hasRecording) "● Regravar" else "● Gravar") }
            if (hasRecording) {
                TextButton(onClick = { clips.deleteRecording(text) }) { Text("Apagar") }
            }
        }
    }
}
