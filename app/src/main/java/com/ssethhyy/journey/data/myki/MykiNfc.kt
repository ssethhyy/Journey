/*
 * Feature Module: myki
 * File: MykiNfc.kt
 * Description: Reads the card number from a physical myki over NFC.
 *
 * myki is a MIFARE DESFire card. Application 0x11F2, file 0x0F holds a small metadata record
 * whose first two little-endian uint32 values are the number prefix (308425) and the next 8 digits;
 * the 15th digit is a Luhn check digit. That file is readable without keys. Balances and trips are
 * in key-protected files, so they cannot be read by third-party apps.
 */

package com.ssethhyy.journey.data.myki

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.io.IOException

sealed interface MykiScan {
    data class Card(val number: String) : MykiScan
    /** A card was read but it is not a myki (or it is a myki we could not read). */
    data object NotMyki : MykiScan
    /** The card moved away before it was read. */
    data object Lost : MykiScan
}

object MykiNfc {
    const val APP_ID = 0x11F2
    const val FILE_ID = 0x0F
    private const val PREFIX = 308_425L

    private const val CMD_SELECT_APP = 0x5A
    private const val CMD_READ_DATA = 0xBD
    private const val CMD_MORE = 0xAF
    private const val STATUS_OK = 0x00
    private const val STATUS_MORE = 0xAF

    /** DESFire native command wrapped in an ISO 7816 APDU (CLA 0x90). */
    fun wrap(command: Int, data: ByteArray = ByteArray(0)): ByteArray =
        if (data.isEmpty()) byteArrayOf(0x90.toByte(), command.toByte(), 0, 0, 0)
        else byteArrayOf(0x90.toByte(), command.toByte(), 0, 0, data.size.toByte()) + data + byteArrayOf(0)

    fun selectApplication(appId: Int = APP_ID): ByteArray =
        wrap(CMD_SELECT_APP, byteArrayOf(appId.toByte(), (appId shr 8).toByte(), (appId shr 16).toByte()))

    /** Read the whole file (offset 0, length 0 = to the end). */
    fun readFile(fileId: Int = FILE_ID): ByteArray = wrap(CMD_READ_DATA, byteArrayOf(fileId.toByte(), 0, 0, 0, 0, 0, 0))

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { acc, i -> acc or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i)) }

    /** The 15-digit number from the metadata file, or null when it is not a myki record. */
    fun parseNumber(file: ByteArray): String? {
        if (file.size < 8) return null
        if (le32(file, 0) != PREFIX) return null
        val rest = le32(file, 4)
        if (rest > 99_999_999L) return null
        val payload = PREFIX.toString() + rest.toString().padStart(8, '0')
        return payload + MykiNumber.luhnDigit(payload)
    }

    /** Runs one DESFire command, following "more data" frames. Null when the card refuses it. */
    private fun exchange(iso: IsoDep, apdu: ByteArray): ByteArray? {
        var response = iso.transceive(apdu)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            if (response.size < 2 || response[response.size - 2] != 0x91.toByte()) return null
            out.write(response, 0, response.size - 2)
            when (response.last().toInt() and 0xFF) {
                STATUS_OK -> return out.toByteArray()
                STATUS_MORE -> response = iso.transceive(wrap(CMD_MORE))
                else -> return null
            }
        }
    }

    fun read(tag: Tag): MykiScan {
        val iso = IsoDep.get(tag) ?: return MykiScan.NotMyki
        return try {
            iso.connect()
            iso.timeout = 1_500
            exchange(iso, selectApplication()) ?: return MykiScan.NotMyki
            val file = exchange(iso, readFile()) ?: return MykiScan.NotMyki
            parseNumber(file)?.let { MykiScan.Card(it) } ?: MykiScan.NotMyki
        } catch (_: IOException) {
            MykiScan.Lost
        } catch (_: SecurityException) {
            MykiScan.Lost
        } finally {
            runCatching { iso.close() }
        }
    }

    fun isSupported(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity) != null
    fun isEnabled(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true

    /** Reader mode: the screen gets every tap first, so Android doesn't show its "no app" sheet. */
    fun enable(activity: Activity, onScan: (MykiScan) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.enableReaderMode(
            activity,
            { tag -> onScan(read(tag)) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    fun disable(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }
}
