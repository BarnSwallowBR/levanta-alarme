package br.com.barnswallow.levanta.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

/**
 * Acha o áudio pronto de uma frase (já com {nome}/{pessoa} trocados), na ordem:
 * gravação do usuário > voz neural empacotada no APK > nada (o chamador usa o TTS do sistema).
 * O áudio é ligado à frase pelo hash do texto, então editar a frase ou mudar o nome invalida o áudio.
 */
class VoiceClips private constructor(context: Context) {

    sealed interface Clip {
        data class Recorded(val file: File) : Clip
        data class Packaged(val assetPath: String) : Clip
    }

    private val appContext = context.applicationContext
    private val voices = VoiceRepository.get(context)

    // Armazenamento protegido pelo aparelho: o alarme toca a gravação mesmo antes do primeiro desbloqueio.
    private val recordingsDir = File(context.createDeviceProtectedStorageContext().filesDir, "recordings")
        .apply {
            mkdirs()
            // Sobras de gravação interrompida (app morto no meio) não servem para nada.
            listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
        }

    private val _recorded = MutableStateFlow(scanRecordings())

    /** Chaves ([key]) das frases que têm gravação. */
    val recorded: StateFlow<Set<String>> = _recorded.asStateFlow()

    private val packagedCache = HashMap<String, Set<String>>()

    fun resolve(text: String): Clip? {
        val k = key(text)
        recordingFile(k).takeIf { it.isFile && it.length() > 0 }?.let { return Clip.Recorded(it) }
        val voice = voices.packagedVoice.value
        if (voice != VoiceRepository.OFF && "$k.mp3" in packaged(voice)) return Clip.Packaged("voice/$voice/$k.mp3")
        return null
    }

    fun hasRecording(text: String) = key(text) in _recorded.value

    fun hasPackaged(text: String, voice: String = voices.packagedVoice.value) =
        voice != VoiceRepository.OFF && "${key(text)}.mp3" in packaged(voice)

    /** Arquivo temporário para o MediaRecorder; só vira a gravação da frase em [commitRecording]. */
    fun newRecordingTemp(): File = File(recordingsDir, "gravando-${System.nanoTime()}.tmp")

    fun commitRecording(text: String, temp: File): Boolean {
        val target = recordingFile(key(text))
        val ok = temp.length() > 0 && (temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = true) }.isSuccess)
        temp.delete()
        _recorded.value = scanRecordings()
        return ok
    }

    fun deleteRecording(text: String) {
        recordingFile(key(text)).delete()
        _recorded.value = scanRecordings()
    }

    private fun recordingFile(k: String) = File(recordingsDir, "$k.m4a")

    private fun scanRecordings(): Set<String> =
        recordingsDir.listFiles { f -> f.name.endsWith(".m4a") && f.length() > 0 }
            ?.map { it.nameWithoutExtension }?.toSet().orEmpty()

    @Synchronized
    private fun packaged(voice: String): Set<String> = packagedCache.getOrPut(voice) {
        runCatching { appContext.assets.list("voice/$voice")?.toSet() }.getOrNull().orEmpty()
    }

    companion object {
        /** Igual a key() em scripts/gerar-vozes.py: NFC, espaços colapsados, SHA-256, 16 primeiros hex. */
        fun key(text: String): String {
            val norm = Normalizer.normalize(text, Normalizer.Form.NFC).trim().split(Regex("\\s+")).joinToString(" ")
            val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(16)
        }

        @Volatile
        private var instance: VoiceClips? = null

        fun get(context: Context): VoiceClips =
            instance ?: synchronized(this) {
                instance ?: VoiceClips(context.applicationContext).also { instance = it }
            }
    }
}
