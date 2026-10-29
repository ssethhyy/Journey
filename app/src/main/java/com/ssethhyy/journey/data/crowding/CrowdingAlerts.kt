/*
 * Feature Module: Crowding
 * File: CrowdingAlerts.kt
 * Description: When to tell the user a service they are tracking has become crowded, and how to describe
 * air-conditioning problems. Pure logic, no Android types.
 */

package com.ssethhyy.journey.data.crowding

import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.VehicleInfo

/** How crowded a tracked service must be before Journey sends an alert. */
enum class CrowdingAlertLevel(val label: String, val minimum: Occupancy) {
    BUSY("Standing room only", Occupancy.STANDING_ONLY),
    VERY_BUSY("Very crowded", Occupancy.CRUSHED),
    FULL("Full", Occupancy.FULL),
}

object CrowdingAlertPolicy {
    data class Decision(val alert: Boolean, val lastAlerted: Occupancy?)

    /**
     * Alerts once when the service reaches [threshold], and again only if it gets worse. Dropping back below the
     * threshold re-arms it. Missing data changes nothing.
     */
    fun next(lastAlerted: Occupancy?, current: Occupancy, threshold: Occupancy): Decision {
        if (!current.hasData) return Decision(false, lastAlerted)
        if (current.level < threshold.level) return Decision(false, null)
        val worse = lastAlerted == null || current.level > lastAlerted.level
        return Decision(worse, current)
    }

    /** "Standing room only. Cars 3 and 4 are fullest." */
    fun message(crowding: VehicleCrowding): String {
        val base = crowding.overall.label
        val busiest = crowding.busiestCarriages
        if (!crowding.hasCarriageDetail || busiest.isEmpty() || busiest.size == crowding.carriages.size) return base
        val cars = when (busiest.size) {
            1 -> "Car ${busiest[0]} is"
            2 -> "Cars ${busiest[0]} and ${busiest[1]} are"
            else -> "Cars ${busiest.dropLast(1).joinToString(", ")} and ${busiest.last()} are"
        }
        return "$base. $cars fullest."
    }
}

sealed interface AirConditioning {
    /** The fleet data says it has air conditioning. */
    data object Available : AirConditioning

    /** The fleet data says it doesn't. */
    data object NotFitted : AirConditioning

    /** An active alert mentions an air-conditioning fault on this route. */
    data class FaultReported(val alerts: List<Disruption>) : AirConditioning

    data object Unknown : AirConditioning
}

object AirConditioningCheck {
    private val FAULT = Regex("(air[\\s-]?con(ditioning)?|a/c|heating\\s+and\\s+cooling|climate\\s+control)", RegexOption.IGNORE_CASE)

    /** Alerts whose title or description is about air conditioning. */
    fun faultAlerts(disruptions: List<Disruption>): List<Disruption> =
        disruptions.filter { FAULT.containsMatchIn(it.title) || FAULT.containsMatchIn(it.description) }

    /**
     * PTV publishes no live air-conditioning status. What can be said is what the fleet data lists, plus any
     * alert that mentions a fault. A fault alert wins over the fleet data.
     */
    fun check(vehicle: VehicleInfo?, disruptions: List<Disruption>): AirConditioning {
        val faults = faultAlerts(disruptions)
        return when {
            faults.isNotEmpty() -> AirConditioning.FaultReported(faults)
            vehicle?.airConditioned == true -> AirConditioning.Available
            vehicle?.airConditioned == false -> AirConditioning.NotFitted
            else -> AirConditioning.Unknown
        }
    }
}
