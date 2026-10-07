package ru.filebrowser.mobile

import android.content.Context
import androidx.core.content.edit

class ServerUrlStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString(AppConfig.KEY_SERVER_URL, null)
        ?.takeIf { it.isNotBlank() }

    fun save(url: String) {
        prefs.edit { putString(AppConfig.KEY_SERVER_URL, url) }
    }

    fun clear() {
        prefs.edit { remove(AppConfig.KEY_SERVER_URL) }
    }

    companion object {
        /**
         * Приводит пользовательский ввод к каноничному URL.
         * Возвращает null, если строка не валидна.
         */
        fun normalize(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.contains(' ')) return null

            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }

            return try {
                val uri = android.net.Uri.parse(withScheme)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return null
                val host = uri.host ?: return null
                if (host.isBlank()) return null
                withScheme.trimEnd('/')
            } catch (_: Exception) {
                null
            }
        }
    }
}