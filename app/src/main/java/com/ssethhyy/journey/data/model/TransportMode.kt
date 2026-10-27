/*
 * Feature Module: Domain Models
 * File: TransportMode.kt
 * Description: PTV route types mapped to transport modes.
 */

package com.ssethhyy.journey.data.model

/**
 * PTV Timetable API v3 route types.
 * 0 = Train (metropolitan), 1 = Tram, 2 = Bus (metro & regional), 3 = V/Line (regional train & coach), 4 = Night Bus.
 */
enum class TransportMode(
    val routeType: Int,
    val label: String,
    val pluralLabel: String,
    /** Key used by the /v3/disruptions response for this mode. */
    val disruptionKeys: List<String>,
) {
    TRAIN(0, "Train", "Trains", listOf("metro_train")),
    TRAM(1, "Tram", "Trams", listOf("metro_tram")),
    BUS(2, "Bus", "Buses", listOf("metro_bus", "regional_bus", "skybus")),
    VLINE(3, "V/Line", "V/Line", listOf("regional_train", "regional_coach")),
    NIGHT_BUS(4, "Night Bus", "Night Buses", listOf("night_bus")),
    ;

    companion object {
        fun fromRouteType(routeType: Int): TransportMode = entries.firstOrNull { it.routeType == routeType } ?: BUS

        fun fromDisruptionKey(key: String): TransportMode? = entries.firstOrNull { key in it.disruptionKeys }
    }
}
