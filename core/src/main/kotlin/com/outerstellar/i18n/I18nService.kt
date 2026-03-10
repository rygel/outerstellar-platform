package com.outerstellar.i18n

import java.util.Locale
import java.util.ResourceBundle
import java.text.MessageFormat

class I18nService private constructor(private val bundleName: String) {
    private var locale: Locale = Locale.getDefault()
    private var bundle: ResourceBundle = ResourceBundle.getBundle(bundleName, locale)

    fun setLocale(newLocale: Locale) {
        this.locale = newLocale
        this.bundle = ResourceBundle.getBundle(bundleName, locale)
    }

    fun translate(key: String, vararg args: Any?): String {
        return try {
            val pattern = bundle.getString(key)
            if (args.isEmpty()) {
                pattern
            } else {
                MessageFormat.format(pattern, *args)
            }
        } catch (e: Exception) {
            key
        }
    }

    companion object {
        fun create(bundleName: String): I18nService = I18nService(bundleName)
        
        fun fromResourceBundle(bundleName: String): I18nService = create(bundleName)
    }
}
