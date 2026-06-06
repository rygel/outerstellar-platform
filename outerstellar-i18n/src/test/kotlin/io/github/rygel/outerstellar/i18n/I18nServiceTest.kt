package io.github.rygel.outerstellar.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class I18nServiceTest {
    @Test
    fun `translates resource bundle keys and injects parameters`() {
        val i18n = I18nService.create("test-messages", Locale.ENGLISH)

        assertEquals("Hello Alice", i18n.translate("greeting", "Alice"))
        assertEquals("missing.key", i18n.translate("missing.key"))
        assertEquals("Fallback", i18n.translateOrDefault("missing.key", "Fallback"))
    }

    @Test
    fun `locale switching clears cache and notifies listeners`() {
        val i18n = I18nService.create("test-messages", Locale.ENGLISH)
        var calls = 0
        val listener = Translatable { calls++ }
        i18n.addListener(listener)

        i18n.setLocale(Locale.FRENCH)

        assertEquals(Locale.FRENCH, i18n.getLocale())
        assertEquals("Bonjour Alice", i18n.translate("greeting", "Alice"))
        assertEquals(1, calls)
    }

    @Test
    fun `missing locale uses base bundle instead of default locale`() {
        val previousDefault = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try {
            val i18n = I18nService.create("test-messages", Locale.GERMAN)

            assertEquals("Hello Alice", i18n.translate("greeting", "Alice"))
        } finally {
            Locale.setDefault(previousDefault)
        }
    }

    @Test
    fun `dynamic bundles override resource bundle values`() {
        val i18n = I18nService.create("test-messages", Locale.ENGLISH)
        val override = "greeting=Override {0}\nextra=Extra value\n".byteInputStream()

        i18n.loadFromStream(override)

        assertEquals("Override Alice", i18n.translate("greeting", "Alice"))
        assertEquals("Extra value", i18n.translate("extra"))
        assertTrue(i18n.hasKey("extra"))
        assertTrue("extra" in i18n.getKeys())
    }

    @Test
    fun `parameter injector leaves missing indexes intact`() {
        assertEquals("A one {1}", ParameterInjector.inject("A {0} {1}", "one"))
        assertEquals("A one two", ParameterInjector.inject("A {0} {1}", listOf("one", "two")))
    }

    @Test
    fun `language lookup is based on locale language`() {
        assertTrue(Language.isSupported(Locale.CANADA_FRENCH))
        assertEquals("English", Language.forLocale(Locale.US)?.displayName)
        assertNull(Language.forLocale(Locale.of("es")))
        assertFalse(Language.availableLanguages().isEmpty())
    }
}
