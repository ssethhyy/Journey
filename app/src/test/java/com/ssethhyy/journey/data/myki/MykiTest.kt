package com.ssethhyy.journey.data.myki

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MykiTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun parsesTheNumberFromTheMetadataFile() {
        // 308425 and 12345678 as little-endian uint32s; check digit 0 (Luhn).
        assertEquals("308425123456780", MykiNfc.parseNumber(hex("C9B404004E61BC000000000000000000")))
        // Short leading part is zero padded to 8 digits.
        val n = MykiNfc.parseNumber(hex("C9B4040007000000"))!!
        assertEquals("30842500000007", n.dropLast(1))
        assertTrue(MykiNumber.isValid(n))
    }

    @Test
    fun rejectsOtherCards() {
        assertNull(MykiNfc.parseNumber(hex("01020304")))
        assertNull(MykiNfc.parseNumber(hex("C9B40401" + "4E61BC00")))
        assertNull("second part over 8 digits", MykiNfc.parseNumber(hex("C9B40400FFFFFFFF")))
    }

    @Test
    fun buildsDesfireApdus() {
        assertArrayEquals(hex("905A000003F2110000"), MykiNfc.selectApplication())
        assertArrayEquals(hex("90BD0000070F00000000000000"), MykiNfc.readFile())
        assertArrayEquals(hex("90AF000000"), MykiNfc.wrap(0xAF))
    }

    @Test
    fun validatesAndFormatsNumbers() {
        assertTrue(MykiNumber.isValid("3 08425 1234 5678 0"))
        assertFalse(MykiNumber.isValid("308425123456781"))
        assertFalse(MykiNumber.isValid("408425123456780"))
        assertEquals("3 08425 1234 5678 0", MykiNumber.format("308425123456780"))
        assertEquals("3 0842", MykiNumber.format("30842"))
        assertNull(MykiNumber.problem("308425123456780"))
        assertEquals("myki numbers start with 3 08425", MykiNumber.problem("4"))
        assertEquals("5 more digits", MykiNumber.problem("3084251234"))
        assertEquals("Check the number, the last digit doesn't match", MykiNumber.problem("308425123456789"))
    }

    @Test
    fun ledgerTracksBalanceAndTopUps() {
        val card = MykiCard("a", "308425123456780", "Mine", addedMillis = 1)
        val withBalance = MykiLedger.setBalance(card, 2_616, 10, id = "b1")
        assertEquals(2_616L, withBalance.balanceCents)
        assertNull("first balance has no change", withBalance.activity.single().amountCents)

        val topped = MykiLedger.topUp(withBalance, 2_000, 20, id = "t1")
        assertEquals(4_616L, topped.balanceCents)
        assertEquals(listOf("t1", "b1"), topped.activity.map { it.id })

        val checked = MykiLedger.setBalance(topped, 4_100, 30, id = "b2")
        assertEquals(-516L, checked.activity.first().amountCents)
        assertEquals("same balance only refreshes the time", 3, MykiLedger.setBalance(checked, 4_100, 40).activity.size)

        // Top up with an unknown balance keeps it unknown.
        assertNull(MykiLedger.topUp(card, 1_000, 5).balanceCents)
    }

    @Test
    fun ledgerListOpsAndStorageRoundTrip() {
        val a = MykiCard("a", "308425123456780", "A")
        val list = MykiLedger.add(emptyList(), a)
        assertEquals("duplicate numbers are ignored", list, MykiLedger.add(list, a.copy(id = "z", number = "3 08425 1234 5678 0")))
        val decoded = MykiLedger.decode(MykiLedger.encode(MykiLedger.update(list, "a") { MykiLedger.topUp(it, 500, 1, "t") }))
        assertEquals("t", decoded.single().activity.single().id)
        assertTrue(MykiLedger.remove(decoded, "a").isEmpty())
        assertTrue(MykiLedger.decode("not json").isEmpty())
    }

    @Test
    fun dollars() {
        assertEquals(2_616L, parseDollars("26.16"))
        assertEquals(2_610L, parseDollars("$26.1"))
        assertEquals(2_600L, parseDollars("26"))
        assertEquals(50L, parseDollars(".5"))
        assertNull(parseDollars("26.161"))
        assertNull(parseDollars("abc"))
        assertEquals("$26.16", formatDollars(2_616))
        assertEquals("+$5.00", formatDollars(500, signed = true))
        assertEquals("−$5.16", formatDollars(-516))
    }
}
