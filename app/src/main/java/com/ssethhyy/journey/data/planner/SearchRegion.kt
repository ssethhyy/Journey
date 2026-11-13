/*
 * Feature Module: Journey planner
 * File: SearchRegion.kt
 * Description: Where place search and the Map tab are limited to: Victoria (the default, with a little margin for
 * border towns like Albury and Mount Gambier) or all of Australia (for interstate trips).
 */

package com.ssethhyy.journey.data.planner

enum class SearchRegion(
    val label: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    VICTORIA("Victoria", -39.4, 140.6, -33.6, 150.3),
    AUSTRALIA("Australia", -44.5, 112.0, -9.0, 154.5),
    ;

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    /** Melbourne, where results are ranked from when no location is known. */
    val searchBias: GeoPoint get() = GeoPoint(-37.8136, 144.9631)
}
