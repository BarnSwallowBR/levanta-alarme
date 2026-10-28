package br.com.barnswallow.levanta.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Frases faladas no alarme, nomes usados nelas e o QR code da missão. */
class SettingsRepository private constructor(context: Context) {
    // Armazenamento protegido pelo aparelho: legível depois de reiniciar, antes do primeiro desbloqueio.
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("levanta_settings", Context.MODE_PRIVATE)

    private val _phrases = MutableStateFlow(loadPhrases())
    val phrases: StateFlow<List<String>> = _phrases.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER, DEFAULT_USER) ?: DEFAULT_USER)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _personName = MutableStateFlow(prefs.getString(KEY_PERSON, DEFAULT_PERSON) ?: DEFAULT_PERSON)
    val personName: StateFlow<String> = _personName.asStateFlow()

    private val _qrCode = MutableStateFlow(prefs.getString(KEY_QR, null))
    val qrCode: StateFlow<String?> = _qrCode.asStateFlow()

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER, name).apply()
        _userName.value = name
    }

    fun setPersonName(name: String) {
        prefs.edit().putString(KEY_PERSON, name).apply()
        _personName.value = name
    }

    fun setQrCode(code: String?) {
        prefs.edit().putString(KEY_QR, code).apply()
        _qrCode.value = code
    }

    fun setPhrases(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }
        prefs.edit().putString(KEY_PHRASES, JSONArray(clean).toString()).apply()
        _phrases.value = clean
    }

    fun resetPhrases() = setPhrases(DEFAULT_PHRASES)

    /** Troca {nome} e {pessoa} pelos nomes configurados. */
    fun render(phrase: String): String = phrase
        .replace("{nome}", _userName.value.ifBlank { DEFAULT_USER })
        .replace("{pessoa}", _personName.value.ifBlank { DEFAULT_PERSON })

    fun renderedPhrases(): List<String> = _phrases.value.map(::render)

    private fun loadPhrases(): List<String> {
        val raw = prefs.getString(KEY_PHRASES, null) ?: return DEFAULT_PHRASES
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(DEFAULT_PHRASES)
    }

    companion object {
        private const val KEY_PHRASES = "phrases"
        private const val KEY_USER = "user_name"
        private const val KEY_PERSON = "person_name"
        private const val KEY_QR = "qr_code"
        const val DEFAULT_USER = "campeão"
        const val DEFAULT_PERSON = "Geovanna"

        val DEFAULT_PHRASES = listOf(
            "Você não vai ganhar dinheiro na cama. Levanta!",
            "{pessoa} está te esperando, e com você aí deitado não vai adiantar nada.",
            "Ninguém vai fazer por você. Levanta agora, {nome}!",
            "Cama não paga boleto. Levanta!",
            "Seus boletos acordaram antes de você.",
            "Quem você quer ser não fica apertando soneca.",
            "A concorrência já está de pé. E você?",
            "Disciplina é fazer mesmo sem vontade. Bora!",
            "{pessoa} merece a melhor versão de você. Ela não está nessa cama.",
            "O dia de hoje não volta. Bora, {nome}!",
            "Você prometeu pra você mesmo. Cumpre.",
            "Daqui a um ano você vai agradecer por ter levantado hoje.",
            "Sono extra não constrói nada. Ação constrói.",
            "Lembra por que você colocou esse alarme. Levanta!",
            "Seus objetivos não vão se cumprir sozinhos.",
            "Abre o olho, põe o pé no chão e vai.",
            "{pessoa} está contando com você. Não decepciona.",
            "Rico não é quem dorme mais. Levanta, {nome}!",
            "Cinco minutos a mais hoje viram um sonho a menos amanhã.",
            "Enquanto você dorme, alguém está fazendo o que você queria fazer.",
        )

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
