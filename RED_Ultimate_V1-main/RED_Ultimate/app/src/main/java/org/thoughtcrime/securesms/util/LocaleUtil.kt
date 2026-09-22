package com.red.sovereign.util

import androidx.core.os.LocaleListCompat
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.dynamiclanguage.LanguageString
import java.util.Locale

object LocaleUtil {

  fun getFirstLocale(): Locale {
    return getLocaleDefaults().firstOrNull() ?: Locale.getDefault()
  }

  /**
   * Get a user priority list of locales supported on the device, with the locale set via RED settings
   * as highest priority over system settings.
   */
  fun getLocaleDefaults(): List<Locale> {
    val locales: MutableList<Locale> = mutableListOf()
    val signalLocale: Locale? = LanguageString.parseLocale(REDStore.settings.language)
    val localeList: LocaleListCompat = LocaleListCompat.getDefault()

    if (signalLocale != null) {
      locales += signalLocale
    }

    for (index in 0 until localeList.size()) {
      locales += localeList.get(index) ?: continue
    }

    return locales
  }
}
