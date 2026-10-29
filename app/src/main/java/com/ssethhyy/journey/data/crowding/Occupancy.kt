/*
 * Feature Module: Crowding
 * File: Occupancy.kt
 * Description: How full a vehicle or carriage is, using the GTFS Realtime OccupancyStatus scale that
 * Victoria's open data feeds report, plus the models the UI shows.
 */

package com.ssethhyy.journey.data.crowding

/** From empty (0) to full (5). [UNKNOWN] means the feed had no figure. */
enum class Occupancy(val gtfsValue: Int, val level: Int, val label: String, val short: String) {
    EMPTY(0, 0, "Empty", "Empty"),
    MANY_SEATS(1, 1, "Plenty of seats", "Quiet"),
    FEW_SEATS(2, 2, "A few seats left", "Getting busy"),
    STANDING_ONLY(3, 3, "Standing room only", "Busy"),
    CRUSHED(4, 4, "Very crowded", "Very busy"),
    FULL(5, 5, "Full", "Full"),
    NOT_ACCEPTING(6, 5, "Not accepting passengers", "Full"),
    UNKNOWN(-1, -1, "No crowding data", "No data"),
    ;

    val hasData: Boolean get() = this != UNKNOWN

    companion object {
        /** GTFS OccupancyStatus: 7 (no data) and anything unexpected become [UNKNOWN]; 8 (not boardable) counts as full. */
        fun fromGtfs(value: Int): Occupancy = when (value) {
            0 -> EMPTY
            1 -> MANY_SEATS
            2 -> FEW_SEATS
            3 -> STANDING_ONLY
            4 -> CRUSHED
            5 -> FULL
            6, 8 -> NOT_ACCEPTING
            else -> UNKNOWN
        }

        /** Some feeds give only a percentage of capacity. */
        fun fromPercentage(percent: Int): Occupancy = when {
            percent < 0 -> UNKNOWN
            percent < 10 -> EMPTY
            percent < 40 -> MANY_SEATS
            percent < 70 -> FEW_SEATS
            percent < 90 -> STANDING_ONLY
            percent < 100 -> CRUSHED
            else -> FULL
        }
    }
}

data class CarriageCrowding(
    /** The carriage's own number, e.g. "1380T". */
    val label: String,
    val occupancy: Occupancy,
    val percentage: Int?,
)

data class VehicleCrowding(
    val overall: Occupancy,
    val percentage: Int?,
    /** Empty when the feed only reports the whole vehicle. */
    val carriages: List<CarriageCrowding>,
    val recordedAtSec: Long?,
) {
    val hasData: Boolean get() = overall.hasData
    val hasCarriageDetail: Boolean get() = carriages.count { it.occupancy.hasData } > 1

    /** Carriages at the busiest level reported, as 1-based positions in train order. */
    val busiestCarriages: List<Int>
        get() {
            val top = carriages.maxOfOrNull { it.occupancy.level } ?: return emptyList()
            if (top < 0) return emptyList()
            return carriages.mapIndexedNotNull { i, c -> if (c.occupancy.level == top) i + 1 else null }
        }

    companion object {
        /** The whole-vehicle figure when the feed has one, otherwise the busiest carriage, otherwise the percentage. */
        fun of(vehicleOccupancy: Occupancy, percentage: Int?, carriages: List<CarriageCrowding>, recordedAtSec: Long?): VehicleCrowding {
            val overall = when {
                vehicleOccupancy.hasData -> vehicleOccupancy
                carriages.any { it.occupancy.hasData } -> carriages.maxBy { it.occupancy.level }.occupancy
                percentage != null -> Occupancy.fromPercentage(percentage)
                else -> Occupancy.UNKNOWN
            }
            return VehicleCrowding(overall, percentage, carriages, recordedAtSec)
        }
    }
}
