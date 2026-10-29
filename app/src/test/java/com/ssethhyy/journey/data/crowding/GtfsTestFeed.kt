package com.ssethhyy.journey.data.crowding

import java.io.ByteArrayOutputStream

/** Builds just enough GTFS Realtime protobuf to exercise the parser. */
internal class Pb {
    val out = ByteArrayOutputStream()
    private fun varint(v: Long) {
        var x = v
        while (x and 0x7F.inv().toLong() != 0L) {
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
        out.write(x.toInt())
    }
    fun int(field: Int, v: Int) = apply { varint((field shl 3).toLong()); varint(v.toLong()) }
    fun str(field: Int, v: String) = apply { bytes(field, v.toByteArray()) }
    fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
    fun msg(field: Int, block: Pb.() -> Unit) = bytes(field, Pb().apply(block).out.toByteArray())
    fun float(field: Int, v: Float) = apply {
        varint(((field shl 3) or 5).toLong())
        val bits = v.toRawBits()
        for (i in 0..3) out.write((bits ushr (8 * i)) and 0xFF)
    }
    fun build(): ByteArray = out.toByteArray()
}

internal fun feed(vararg vehicles: Pb.() -> Unit): ByteArray = Pb().apply {
    msg(1) { str(1, "2.0") }
    vehicles.forEachIndexed { i, v -> msg(2) { str(1, "e$i"); msg(4, v) } }
}.build()

internal fun train(id: String, lat: Float, lon: Float, overall: Int?, cars: List<Int>): Pb.() -> Unit = {
    msg(2) { float(1, lat); float(2, lon) }
    msg(8) { str(1, id) }
    overall?.let { int(9, it) }
    cars.forEachIndexed { i, o -> msg(11) { str(1, "${1000 + i}M"); str(2, "${i + 1}"); int(3, o); int(5, i + 1) } }
}
