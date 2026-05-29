package ac.stresa.uturn.core

import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.utils.LocaleCompat
import java.io.Serializable
import java.util.Collections
import java.util.Locale
import java.util.Objects

// derived from VoiVista
class Localization @JvmOverloads constructor(
    val languageCode: String,
    private val countryCode: String? = null) : Serializable {

    /**
     * Return a formatted string in the form of: `language-Country`, or
     * just `language` if country is `null`.
     * @return A correctly formatted localizationCode for this localization.
     */
    private val localizationCode: String
        get() = languageCode + (if (countryCode == null) "" else "-$countryCode")

    fun getCountryCode(): String {
        return countryCode ?: ""
    }

    override fun toString(): String {
        return "Localization[$localizationCode]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Localization) return false
        return languageCode == other.languageCode && countryCode == other.getCountryCode()
    }

    override fun hashCode(): Int {
        var result = languageCode.hashCode()
        result = 31 * result + Objects.hashCode(countryCode)
        return result
    }

    companion object {
        
        val DEFAULT: Localization = Localization("en", "GB")

        /**
         * @param localizationCodeList a list of localization code, formatted like [                             ][.getLocalizationCode]
         * @throws IllegalArgumentException If any of the localizationCodeList is formatted incorrectly
         * @return list of Localization objects
         */
        fun listFrom(vararg localizationCodeList: String): List<Localization> {
            val toReturn: MutableList<Localization> = mutableListOf()
            for (localizationCode in localizationCodeList) {
                toReturn.add(fromLocalizationCode(localizationCode) ?: throw IllegalArgumentException("Not a localization code: $localizationCode"))
            }
            return Collections.unmodifiableList(toReturn)
        }

        /**
         * @param localizationCode a localization code, formatted like [.getLocalizationCode]
         * @return A Localization, if the code was valid.
         */
        private fun fromLocalizationCode(localizationCode: String): Localization? {
            return LocaleCompat.forLanguageTag(localizationCode)?.let { fromLocale(it.get()) }
        }

        private fun fromLocale(locale: Locale): Localization {
            return Localization(locale.language, locale.country)
        }

        /**
         * Converts a three letter language code (ISO 639-2/T) to a Locale
         * because limits of Java Locale class.
         *
         * @param code a three letter language code
         * @return the Locale corresponding
         */

        @Throws(ParsingException::class)
        fun getLocaleFromThreeLetterCode(code: String): Locale? {
            val languages = Locale.getISOLanguages()
            val localeMap: MutableMap<String, Locale> = mutableMapOf()
            for (language in languages) {
                val locale = Locale.forLanguageTag(language)
                localeMap[locale.isO3Language] = locale
            }
            if (localeMap.containsKey(code)) return localeMap[code]
            else throw ParsingException("Could not get Locale from this three letter language code$code")
        }

        fun getPreferredLocalization(): org.schabi.newpipe.extractor.localization.Localization {
            return org.schabi.newpipe.extractor.localization.Localization.fromLocale(getPreferredLocale())
        }

        
        private fun getPreferredLocale(): Locale {
            val defaultKey = "system"
//            val languageCode = appPrefs.content_country ?: defaultKey
            val languageCode = defaultKey
            return if (languageCode == defaultKey) Locale.getDefault() else  Locale.forLanguageTag(languageCode)
        }

        
        fun getPreferredContentCountry(): ContentCountry {
//            val contentCountry = appPrefs.content_country ?: "system"
            val contentCountry = "system"
            if (contentCountry == "system") return ContentCountry(Locale.getDefault().country)
            return ContentCountry(contentCountry)
        }
    }
}