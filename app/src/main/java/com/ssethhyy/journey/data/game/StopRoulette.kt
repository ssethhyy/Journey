/*
 * Feature Module: Game
 * File: StopRoulette.kt
 * Description: Stop roulette, from the MelbourneTrainGame app: roll a random stop (a train station by default)
 * within a search radius, travel there, and optionally take on a challenge on the way. Pure logic, unit tested.
 */

package com.ssethhyy.journey.data.game

import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.utils.GeoUtils
import kotlin.random.Random

/** A small twist for the trip, as in MelbourneTrainGame. */
data class TravelChallenge(val title: String, val description: String)

/** What a roll landed on. */
data class RollResult(
    val stop: Stop,
    /** Straight-line distance from where the roll was made. */
    val distanceMeters: Double,
    val challenge: TravelChallenge?,
)

object StopRoulette {
    /** Stops closer than this are skipped when "skip nearby" is on: you could just walk there. */
    const val NEARBY_METERS = 1_500.0

    val CHALLENGES = listOf(
        TravelChallenge("No phone", "Travel to the destination without looking at your phone."),
        TravelChallenge("Ride the way", "Grab a share bike or scooter and ride to another stop off the path."),
        TravelChallenge("Coffee hunter", "Find a local cafe near your destination and buy a drink before heading back."),
        TravelChallenge("Aesthetic explorer", "Take three interesting photos of architecture or art around the stop."),
        TravelChallenge("Step master", "Walk to the next closest station after you arrive."),
        TravelChallenge("Mystery transfer", "Get off one stop early and walk the rest of the way."),
        TravelChallenge("The scenic route", "Take a tram or bus for part of the journey instead of the train."),
    )

    /** The stops a roll can land on: inside the radius and, if asked, not within walking distance. */
    fun eligible(stops: List<Stop>, latitude: Double, longitude: Double, radiusMeters: Int, skipNearby: Boolean): List<Pair<Stop, Double>> =
        stops.distinctBy { it.key }
            .map { it to GeoUtils.haversineMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, d) -> d <= radiusMeters && (!skipNearby || d > NEARBY_METERS) }

    fun roll(candidates: List<Pair<Stop, Double>>, withChallenge: Boolean, random: Random = Random.Default): RollResult? {
        if (candidates.isEmpty()) return null
        val (stop, distance) = candidates[random.nextInt(candidates.size)]
        return RollResult(stop, distance, if (withChallenge) CHALLENGES[random.nextInt(CHALLENGES.size)] else null)
    }
}
