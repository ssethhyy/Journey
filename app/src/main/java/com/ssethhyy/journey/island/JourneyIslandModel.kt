/*
 * Feature Module: Island
 * File: JourneyIslandModel.kt
 * Description: Pure logic for Journey's Essentials island provider: which items to show, their
 * compact/line content, severity colours, and when a line peek is worthwhile. No Android types,
 * so it is unit tested directly; JourneyIslandService turns the result into an IslandPayload.
 */

package com.ssethhyy.journey.island

import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.IslandIconStyle
import com.ssethhyy.journey.data.repository.IslandSeverity
import com.ssethhyy.journey.data.repository.IslandStyle
import com.ssethhyy.journey.tracking.TrackingStatus
import com.ssethhyy.journey.tracking.TrackingTarget
import com.sameerasw.island.sdk.IslandAction
import com.sameerasw.island.sdk.IslandActionType
import com.sameerasw.island.sdk.IslandCellPayload
import com.sameerasw.island.sdk.IslandExpandedPayload
import com.sameerasw.island.sdk.IslandInteractionsPayload
import com.sameerasw.island.sdk.IslandItemPayload
import com.sameerasw.island.sdk.IslandLinePayload
import com.sameerasw.island.sdk.IslandPayload
import com.sameerasw.island.sdk.IslandPlacement
import com.sameerasw.island.sdk.IslandTone

object JourneyIsland {
    const val ITEM_TRACKING = "tracking"
    const val ITEM_DISRUPTIONS = "disruptions"

    const val ACTION_STOP_TRACKING = "stop_tracking"

    // Item priorities (lower wins). The user's order in Island settings overrides these.
    const val PRIORITY_TRACKING = 24
    const val PRIORITY_TRACKING_URGENT = 12
    const val PRIORITY_DISRUPTIONS = 60
    const val PRIORITY_DISRUPTIONS_URGENT = 14

    // Payload limits enforced by Essentials' PayloadValidator.
    const val MAX_CELL_TEXT = 32
    const val MAX_LINE_TEXT = 120

    /** Icon ids sent alongside the payload; bitmaps are drawn by [IslandIcons]. */
    object Icons {
        fun alert(severity: DisruptionSeverity) = "alert_${severity.name.lowercase()}"
        fun mode(mode: TransportMode?, style: IslandIconStyle) =
            "mode_${(mode ?: TransportMode.TRAIN).name.lowercase()}_${if (style == IslandIconStyle.PICTOGRAM) "picto" else "mono"}"
        const val JOURNEY = "journey_mono"
    }

    /** Severity colours for the alert icon (ARGB). Drawn as-is with tone "none". */
    fun severityColor(severity: DisruptionSeverity): Int = when (severity) {
        DisruptionSeverity.SEVERE -> 0xFFFF5449.toInt()
        DisruptionSeverity.MAJOR -> 0xFFFF7A45.toInt()
        DisruptionSeverity.MINOR -> 0xFFFFC23D.toInt()
        DisruptionSeverity.PLANNED -> 0xFF7AB7FF.toInt()
        DisruptionSeverity.INFO -> 0xFFB8BCC4.toInt()
    }

    fun severityLabel(severity: DisruptionSeverity): String = when (severity) {
        DisruptionSeverity.SEVERE -> "Suspended"
        DisruptionSeverity.MAJOR -> "Major delays"
        DisruptionSeverity.MINOR -> "Minor delays"
        DisruptionSeverity.PLANNED -> "Planned works"
        DisruptionSeverity.INFO -> "Service info"
    }

    /** Disruptions that pass the user's minimum-severity setting, most serious first. */
    fun filterDisruptions(all: List<Disruption>, minimum: IslandSeverity): List<Disruption> =
        all.filter { d ->
            when (minimum) {
                IslandSeverity.ALL -> true
                IslandSeverity.DELAYS -> d.severity <= DisruptionSeverity.MINOR
                IslandSeverity.MAJOR -> d.severity <= DisruptionSeverity.MAJOR
            }
        }.sortedBy { it.severity }

    fun worstSeverity(disruptions: List<Disruption>): DisruptionSeverity? = disruptions.minOfOrNull { it.severity }

    fun clip(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    /** Short label for what is being tracked, e.g. "Hurstbridge line" or "To Home". */
    fun trackingLabel(target: TrackingTarget): String = when (target) {
        is TrackingTarget.Service -> target.routeLabel
        is TrackingTarget.Trip -> "To ${target.toName}"
    }

    /**
     * Builds the island payload.
     * - Tracking item: icon + countdown; line "Hurstbridge line · 4 min · Westgarth".
     * - Disruptions item: severity-coloured alert icon + count; line with the most serious title.
     */
    fun payload(
        settings: AppSettings,
        target: TrackingTarget?,
        status: TrackingStatus?,
        disruptions: List<Disruption>,
    ): IslandPayload {
        if (!settings.islandEnabled) return IslandPayload(items = emptyList())
        val items = buildList {
            if (settings.islandTracking && target != null) add(trackingItem(settings, target, status))
            if (settings.islandDisruptions) {
                val shown = filterDisruptions(disruptions, settings.islandMinSeverity)
                if (shown.isNotEmpty()) add(disruptionsItem(settings, shown))
            }
        }
        return IslandPayload(items = items)
    }

    fun trackingItem(settings: AppSettings, target: TrackingTarget, status: TrackingStatus?): IslandItemPayload {
        val mode = status?.mode ?: (target as? TrackingTarget.Service)?.mode
        val iconId = Icons.mode(mode, settings.islandIconStyle)
        // Pictograms are multi-colour (tone none); monochrome glyphs take the island accent.
        val iconTone = if (settings.islandIconStyle == IslandIconStyle.PICTOGRAM) IslandTone.NONE else IslandTone.ACCENT
        val chip = status?.chip ?: "…"
        val urgent = status != null && !status.finished && TrackingPeekPolicy.minutesFromChip(chip)?.let { it <= 2 } == true
        val lineEnd = buildList {
            add(chip)
            status?.detail?.substringBefore(" · ")?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
        return IslandItemPayload(
            key = ITEM_TRACKING,
            compact = listOf(
                IslandCellPayload(key = "$ITEM_TRACKING.icon", iconId = iconId, tone = iconTone),
                IslandCellPayload(key = "$ITEM_TRACKING.time", text = clip(chip, MAX_CELL_TEXT), tone = IslandTone.NONE),
            ),
            priority = PRIORITY_TRACKING,
            priorityOverride = if (urgent) PRIORITY_TRACKING_URGENT else null,
            placement = if (settings.islandPinned) IslandPlacement.PINNED else IslandPlacement.DYNAMIC,
            line = IslandLinePayload(
                start = clip(trackingLabel(target), MAX_LINE_TEXT),
                end = clip(lineEnd, MAX_LINE_TEXT),
                iconId = iconId,
                tone = iconTone,
            ),
            expanded = IslandExpandedPayload(heightDp = 220),
            interactions = IslandInteractionsPayload(onLongPress = IslandAction(IslandActionType.OPEN_APP)),
        )
    }

    fun disruptionsItem(settings: AppSettings, shown: List<Disruption>): IslandItemPayload {
        val worst = worstSeverity(shown) ?: DisruptionSeverity.INFO
        val count = shown.size
        val serious = worst <= DisruptionSeverity.MAJOR
        return IslandItemPayload(
            key = ITEM_DISRUPTIONS,
            compact = listOf(
                IslandCellPayload(key = "$ITEM_DISRUPTIONS.icon", iconId = Icons.alert(worst), tone = IslandTone.NONE),
                IslandCellPayload(key = "$ITEM_DISRUPTIONS.count", text = if (count > 99) "99+" else count.toString(), tone = IslandTone.NONE),
            ),
            priority = PRIORITY_DISRUPTIONS,
            priorityOverride = if (serious && settings.islandAlerts) PRIORITY_DISRUPTIONS_URGENT else null,
            line = IslandLinePayload(
                start = clip(if (count == 1) "1 service update" else "$count service updates", MAX_LINE_TEXT),
                end = clip(shown.first().title, MAX_LINE_TEXT),
                iconId = Icons.alert(worst),
                tone = IslandTone.NONE,
            ),
            expanded = IslandExpandedPayload(heightDp = (140 + 64 * minOf(count, 4)).coerceIn(80, 640)),
            interactions = IslandInteractionsPayload(onLongPress = IslandAction(IslandActionType.OPEN_APP)),
        )
    }

    /** Every icon id the payload can reference, so the service can render them up front. */
    fun iconIds(payload: IslandPayload): Set<String> =
        payload.items.flatMap { item -> item.compact.mapNotNull { it.iconId } + listOfNotNull(item.line?.iconId) }.toSet()
}

/**
 * Decides when a line peek is worth it. Essentials allows at most one peek every 10 s (8 s long),
 * so Journey only peeks at meaningful moments.
 */
object TrackingPeekPolicy {
    private val MILESTONES = listOf(5L, 2L, 0L)

    fun minutesFromChip(chip: String): Long? = when {
        chip.equals("Now", ignoreCase = true) -> 0L
        else -> Regex("""^(\d+) min$""").find(chip.trim())?.groupValues?.get(1)?.toLongOrNull()
    }

    /** Peek for tracking: on start, at the 5 / 2 / 0 minute marks, and when it departs or arrives. */
    fun shouldPeekTracking(previous: TrackingStatus?, current: TrackingStatus, style: IslandStyle): Boolean {
        if (style != IslandStyle.LINE) return false
        if (previous == null) return true
        if (current.finished && !previous.finished) return true
        // A new phase of a journey (e.g. "On Hurstbridge…" after boarding) changes the wording.
        if (headline(previous.title) != headline(current.title)) return true
        val before = minutesFromChip(previous.chip) ?: return false
        val now = minutesFromChip(current.chip) ?: return false
        return MILESTONES.any { mark -> before > mark && now <= mark }
    }

    /** Peek for disruptions: when something major or worse appears that wasn't there before. */
    fun shouldPeekDisruptions(previousIds: Set<Long>, current: List<Disruption>, alertsEnabled: Boolean): Boolean =
        alertsEnabled && current.any { it.severity <= DisruptionSeverity.MAJOR && it.id !in previousIds }

    /** The title without its countdown, so "Train in 6 min" and "Train in 5 min" match. */
    private fun headline(title: String): String = title.replace(Regex("""\d+( hr)?( \d+)? min"""), "#").replace(" now", " #")
}
