/*
 * Feature Module: Crowding
 * File: GtfsRealtime.kt
 * Description: A small reader for the parts of the GTFS Realtime protocol buffer that the vehicle positions feeds
 * use: where each vehicle is, and how full it and its carriages are. Written by hand so the app doesn't need
 * the protobuf runtime for one feed. It is tolerant: unknown fields are skipped and a damaged message returns
 * whatever was read before the damage.
 *
 * Field numbers follow https://gtfs.org/documentation/realtime/reference/ (FeedMessage.entity = 2,
 * FeedEntity.vehicle = 4, VehiclePosition.occupancy_status = 9, multi_carriage_details = 11).
 */

package com.ssethhyy.journey.data.crowding

data class RtCarriage(
    val id: String?,
    val label: String?,
    val occupancy: Occupancy,
    val percentage: Int?,
    val sequence: Int?,
)

data class RtVehicle(
    val id: String?,
    val label: String?,
    val licensePlate: String?,
    val tripId: String?,
    val routeId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val bearing: Float?,
    val timestampSec: Long?,
    val occupancy: Occupancy,
    val percentage: Int?,
    val carriages: List<RtCarriage>,
) {
    /** Every name this vehicle goes by, normalised, for matching against PTV's vehicle id. */
    val allIds: Set<String>
        get() = buildSet {
            listOf(id, label, licensePlate).forEach { addAll(CrowdingMatcher.tokens(it)) }
            carriages.forEach { c ->
                addAll(CrowdingMatcher.tokens(c.id))
                addAll(CrowdingMatcher.tokens(c.label))
            }
        }
}

object GtfsRealtime {
    private class Reader(private val bytes: ByteArray, start: Int = 0, private val end: Int = bytes.size) {
        var pos = start
        val hasMore: Boolean get() = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= end || shift > 63) throw IllegalStateException("truncated varint")
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun fixed32(): Int {
            if (pos + 4 > end) throw IllegalStateException("truncated fixed32")
            val v = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }

        fun float(): Float = Float.fromBits(fixed32())

        fun fixed64(): Long {
            val low = fixed32().toLong() and 0xFFFFFFFFL
            val high = fixed32().toLong() and 0xFFFFFFFFL
            return low or (high shl 32)
        }

        fun double(): Double = Double.fromBits(fixed64())

        /** Length-delimited payload as a sub-reader. */
        fun message(): Reader {
            val length = varint().toInt()
            if (length < 0 || pos + length > end) throw IllegalStateException("truncated message")
            val sub = Reader(bytes, pos, pos + length)
            pos += length
            return sub
        }

        fun string(): String {
            val length = varint().toInt()
            if (length < 0 || pos + length > end) throw IllegalStateException("truncated string")
            val s = String(bytes, pos, length, Charsets.UTF_8)
            pos += length
            return s
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> varint()
                1 -> pos += 8
                2 -> {
                    val length = varint().toInt()
                    if (length < 0) throw IllegalStateException("negative length")
                    pos += length
                }
                5 -> pos += 4
                else -> throw IllegalStateException("unsupported wire type $wireType")
            }
            if (pos > end) throw IllegalStateException("skipped past the end")
        }
    }

    /** Parses a FeedMessage and returns its vehicle entities. */
    fun parseVehicles(bytes: ByteArray): List<RtVehicle> {
        val vehicles = mutableListOf<RtVehicle>()
        runCatching {
            val feed = Reader(bytes)
            while (feed.hasMore) {
                val tag = feed.varint().toInt()
                if (tag ushr 3 == 2 && tag and 7 == 2) {
                    runCatching { entity(feed.message()) }.getOrNull()?.let(vehicles::add)
                } else {
                    feed.skip(tag and 7)
                }
            }
        }
        return vehicles
    }

    private fun entity(r: Reader): RtVehicle? {
        var vehicle: RtVehicle? = null
        while (r.hasMore) {
            val tag = r.varint().toInt()
            if (tag ushr 3 == 4 && tag and 7 == 2) vehicle = vehiclePosition(r.message()) else r.skip(tag and 7)
        }
        return vehicle
    }

    private fun vehiclePosition(r: Reader): RtVehicle {
        var id: String? = null
        var label: String? = null
        var plate: String? = null
        var tripId: String? = null
        var routeId: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var bearing: Float? = null
        var timestamp: Long? = null
        var occupancy = Occupancy.UNKNOWN
        var percentage: Int? = null
        val carriages = mutableListOf<RtCarriage>()
        while (r.hasMore) {
            val tag = r.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 2 -> r.message().let { t ->
                    while (t.hasMore) {
                        val tt = t.varint().toInt()
                        when {
                            tt ushr 3 == 1 && tt and 7 == 2 -> tripId = t.string()
                            tt ushr 3 == 5 && tt and 7 == 2 -> routeId = t.string()
                            else -> t.skip(tt and 7)
                        }
                    }
                }
                field == 2 && wire == 2 -> r.message().let { p ->
                    while (p.hasMore) {
                        val pt = p.varint().toInt()
                        when {
                            pt ushr 3 == 1 && pt and 7 == 5 -> lat = p.float().toDouble()
                            pt ushr 3 == 2 && pt and 7 == 5 -> lon = p.float().toDouble()
                            pt ushr 3 == 3 && pt and 7 == 5 -> bearing = p.float()
                            else -> p.skip(pt and 7)
                        }
                    }
                }
                field == 5 && wire == 0 -> timestamp = r.varint()
                field == 8 && wire == 2 -> r.message().let { v ->
                    while (v.hasMore) {
                        val vt = v.varint().toInt()
                        when {
                            vt ushr 3 == 1 && vt and 7 == 2 -> id = v.string()
                            vt ushr 3 == 2 && vt and 7 == 2 -> label = v.string()
                            vt ushr 3 == 3 && vt and 7 == 2 -> plate = v.string()
                            else -> v.skip(vt and 7)
                        }
                    }
                }
                field == 9 && wire == 0 -> occupancy = Occupancy.fromGtfs(r.varint().toInt())
                field == 10 && wire == 0 -> percentage = r.varint().toInt()
                field == 11 && wire == 2 -> carriages += carriage(r.message())
                else -> r.skip(wire)
            }
        }
        return RtVehicle(id, label, plate, tripId, routeId, lat, lon, bearing, timestamp, occupancy, percentage, carriages)
    }

    private fun carriage(r: Reader): RtCarriage {
        var id: String? = null
        var label: String? = null
        var occupancy = Occupancy.UNKNOWN
        var percentage: Int? = null
        var sequence: Int? = null
        while (r.hasMore) {
            val tag = r.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 2 -> id = r.string()
                field == 2 && wire == 2 -> label = r.string()
                field == 3 && wire == 0 -> occupancy = Occupancy.fromGtfs(r.varint().toInt())
                field == 4 && wire == 0 -> percentage = r.varint().toInt()
                field == 5 && wire == 0 -> sequence = r.varint().toInt()
                else -> r.skip(wire)
            }
        }
        return RtCarriage(id, label, occupancy, percentage?.takeIf { it >= 0 }, sequence)
    }
}
