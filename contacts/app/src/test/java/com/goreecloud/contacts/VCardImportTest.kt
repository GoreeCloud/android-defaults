package com.goreecloud.contacts

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VCardImportTest {
    private fun card(contents: String): ByteArray =
        ("BEGIN:VCARD\r\nVERSION:3.0\r\n" + contents + "END:VCARD\r\n").toByteArray(Charsets.UTF_8)

    private fun rejects(bytes: ByteArray) {
        try { VCardImport.parse(bytes); fail("Expected vCard rejection") }
        catch (_: IllegalArgumentException) {}
        catch (_: java.nio.charset.CharacterCodingException) {}
    }

    @Test fun parsesSingleCardWithFoldedLinesAndEscapes() {
        val preview = VCardImport.parse(card(
            "FN:Renée Rivera\r\nTEL;TYPE=HOME:+12055550100\r\n" +
                "EMAIL;TYPE=WORK:renee@example.invalid\r\nORG:Goree\\,Cloud\r\nNOTE:ignored\r\n"
        ))
        assertEquals("Renée Rivera", preview.name)
        assertEquals(listOf("+12055550100"), preview.phones)
        assertEquals(listOf("renee@example.invalid"), preview.emails)
        assertEquals("Goree,Cloud", preview.organization)
        assertEquals(1, preview.omittedProperties)
    }

    @Test fun unfoldsRfcStyleContinuation() {
        val preview = VCardImport.parse(card("FN:Long\r\n er Name\r\n"))
        assertEquals("Longer Name", preview.name)
    }

    @Test fun refusesMultipleCardsRatherThanSilentlyDroppingRecords() {
        rejects(card("FN:First\r\n") + card("FN:Second\r\n"))
    }

    @Test fun refusesUnsupportedEncodedAndMalformedFields() {
        rejects(card("FN;ENCODING=QUOTED-PRINTABLE:Alex\r\n"))
        rejects(card("FN;CHARSET=ISO-8859-1:Alex\r\n"))
        rejects(card("FN:Alex\r\nTEL:nope\\q\r\n"))
        rejects(card("FN:Alex\r\nFN:Second\r\n"))
        rejects(card("FN:Alex\r\nBADPROPERTY\r\n"))
        rejects(card("TEL:123\r\n"))
        rejects(card("FN:Alex\r\nORG:One\r\nORG:Two\r\n"))
    }

    @Test fun refusesOverBudgetFileBeforeAllocation() {
        val tooLarge = ByteArray(VCardImport.MAX_FILE_BYTES + 1) { 'x'.code.toByte() }
        try {
            VCardImport.readBounded(ByteArrayInputStream(tooLarge))
            fail("Expected bounded import refusal")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun strictUtf8AndNoRawControls() {
        rejects(byteArrayOf(0xC3.toByte(), 0x28))
        rejects(card("FN:Alex\u0000Rivera\r\n"))
        rejects(card("FN:Alex\rSecond\r\n"))
    }

    @Test fun supportsVCard4AndTelUri() {
        val bytes = "BEGIN:VCARD\nVERSION:4.0\nFN:Casey\nTEL;VALUE=uri:tel:+12055550200\nEND:VCARD\n"
        val preview = VCardImport.parse(bytes.toByteArray(Charsets.UTF_8))
        assertEquals("+12055550200", preview.phones.single())
    }

    @Test fun rejectsExcessiveFields() {
        val many = (1..9).joinToString("") { "TEL:555000$it\r\n" }
        rejects(card("FN:Excess\r\n" + many))
    }

    @Test fun acceptsKnownExportSubsetWithoutDirectWrites() {
        val source = ContactEntry(1, "source", "Alex Rivera", listOf("+1 205 555 0100"),
            listOf("alex@example.invalid"), "GoreeCloud")
        val preview = VCardImport.parse(ContactLogic.vCard(source).toByteArray(Charsets.UTF_8))
        assertEquals(source.name, preview.name)
        assertEquals(source.phones, preview.phones)
        assertEquals(source.emails, preview.emails)
        assertEquals(source.organization, preview.organization)
        assertTrue(preview.omittedProperties == 0)
    }
}
