package dev.nurislam.tv.settings

import android.content.SharedPreferences
import dev.nurislam.tv.domain.DisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class BoardThemeId { ELEGANT, CLASSIC }
enum class BoardLanguage { BOTH, TATAR, RUSSIAN }

data class AppSettings(
    val theme: BoardThemeId = BoardThemeId.ELEGANT,
    val displayMode: DisplayMode = DisplayMode.AUTO,
    val textScale: Float = 1f,
    val language: BoardLanguage = BoardLanguage.RUSSIAN,
    val mosqueName: String = "Нур Ислам",
    val location: String = "Казан · Казань",
) {
    init {
        require(textScale in .85f..1.15f)
        require(mosqueName.isNotBlank() && mosqueName.length <= 60 && mosqueName.none { it.isISOControl() })
        require(location.isNotBlank() && location.length <= 80 && location.none { it.isISOControl() })
    }
}

class SettingsRepository(private val preferences: SharedPreferences) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, default.name)!!) }.getOrDefault(default)
    private fun read(): AppSettings = runCatching {
        AppSettings(
            enum("theme", BoardThemeId.ELEGANT), enum("mode", DisplayMode.AUTO),
            preferences.getFloat("scale", 1f), enum("language", BoardLanguage.RUSSIAN),
            preferences.getString("name", "Нур Ислам")!!, preferences.getString("location", "Казан · Казань")!!,
        )
    }.getOrDefault(AppSettings())

    suspend fun update(change: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) { mutex.withLock {
        val settings = change(mutable.value)
        check(preferences.edit().putString("theme", settings.theme.name).putString("mode", settings.displayMode.name)
            .putFloat("scale", settings.textScale).putString("language", settings.language.name)
            .putString("name", settings.mosqueName).putString("location", settings.location).commit()) { "Не удалось сохранить настройки" }
        mutable.value = settings
    } }
}
