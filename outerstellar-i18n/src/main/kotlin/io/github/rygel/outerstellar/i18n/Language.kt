package io.github.rygel.outerstellar.i18n

import java.util.Locale

data class Language(val displayName: String, val locale: Locale, val nativeName: String) {
    override fun toString(): String = displayName

    companion object {
        private val languages =
            listOf(
                Language("English", Locale.ENGLISH, "English"),
                Language("Deutsch", Locale.GERMAN, "Deutsch"),
                Language("Français", Locale.FRENCH, "Français"),
            )

        @JvmStatic fun availableLanguages(): List<Language> = ArrayList(languages)

        @JvmStatic fun isSupported(locale: Locale): Boolean = languages.any { it.locale.language == locale.language }

        @JvmStatic fun forLocale(locale: Locale): Language? = languages.find { it.locale.language == locale.language }
    }
}
