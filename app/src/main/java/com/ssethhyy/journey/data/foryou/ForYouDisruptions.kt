/*
 * Feature Module: For You
 * File: ForYouDisruptions.kt
 * Description: Picks the disruptions the For You Disruptions widget shows.
 *
 * By default only major ones (closures, suspensions, buses replacing trains, changed timetables) that touch a
 * favourite stop or one of the lines the user favourited.
 */

package com.ssethhyy.journey.data.foryou

import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.repository.FavouriteStop

object ForYouDisruptions {
    private val MAJOR_WORDS = listOf(
        "closure", "closed", "suspend", "cancel", "replace", "replacement", "timetable", "not stop", "bypass",
        "not run", "no trains", "no trams", "no buses", "altered", "modified",
    )

    /** Closures, suspensions, replacement buses and changed timetables, whatever PTV filed them under. */
    fun isMajor(d: Disruption): Boolean {
        if (d.severity == DisruptionSeverity.SEVERE || d.severity == DisruptionSeverity.MAJOR) return true
        val text = "${d.type.orEmpty()} ${d.title}".lowercase()
        return MAJOR_WORDS.any { it in text }
    }

    fun matchesLevel(d: Disruption, level: DisruptionLevel): Boolean = when (level) {
        DisruptionLevel.MAJOR -> isMajor(d)
        DisruptionLevel.DELAYS -> isMajor(d) || d.severity == DisruptionSeverity.MINOR
        DisruptionLevel.ALL -> true
    }

    /**
     * Route keys ("routeType:id") the favourites care about: the chosen lines of a favourite limited to some, and
     * every line serving a favourite that shows everything (from its loaded departures).
     */
    fun favouriteRouteKeys(favourites: List<FavouriteStop>, boards: Map<String, DepartureBoard>): Set<String> =
        favourites.flatMap { fav ->
            if (fav.showsEverything) {
                val board = boards[fav.key]
                board?.routes.orEmpty().map { it.key } + board?.departures.orEmpty().mapNotNull { it.route?.key }
            } else {
                fav.lines.map { "${fav.mode.routeType}:${it.routeId}" }
            }
        }.toSet()

    fun select(
        network: List<Disruption>,
        favourites: List<FavouriteStop>,
        boards: Map<String, DepartureBoard>,
        config: DisruptionsConfig,
    ): List<Disruption> {
        val candidates = when (config.scope) {
            DisruptionScope.NETWORK -> network + boards.values.flatMap { it.disruptions.values }
            DisruptionScope.FAVOURITES -> {
                val routeKeys = favouriteRouteKeys(favourites, boards)
                val names = favourites.map { it.name.normalisedStop() }.toSet()
                // Disruptions linked from the departures each favourite actually shows.
                val referenced = favourites.flatMap { fav ->
                    val board = boards[fav.key] ?: return@flatMap emptyList()
                    val ids = fav.filter(board.departures).flatMap { it.disruptionIds }.toSet()
                    board.disruptions.filterKeys { it in ids }.values
                }
                val onRoutes = network.filter { d -> d.routes.any { it.key in routeKeys } }
                val atStops = network.filter { d -> d.stopNames.any { it.normalisedStop() in names } }
                referenced + onRoutes + atStops
            }
        }
        return candidates
            .filter { it.isCurrent && matchesLevel(it, config.level) }
            .distinctBy { it.id }
            .sortedWith(compareBy<Disruption> { it.severity }.thenByDescending { it.publishedOn })
    }

    private fun String.normalisedStop() = lowercase().trim().removeSuffix(" railway station").removeSuffix(" station").trim()
}
