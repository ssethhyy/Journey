/*
 * Feature Module: UI Components
 * File: Symbols.kt
 * Description: App icon set built on Material Symbols Rounded.
 *
 * Each icon uses the Material Symbols Rounded vector drawable in res/drawable when present
 * (rounded_<symbol>_24px.xml, or rounded_<symbol>_fill_24px.xml for filled variants), and is
 * drawn blank (and caught by SymbolsTest) if that file is missing. See art/material-symbols.md.
 */

package com.ssethhyy.journey.ui.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.vectorResource
import java.util.concurrent.ConcurrentHashMap

object Sym {
    val Add: ImageVector @Composable get() = symbol("rounded_add")
    val Search: ImageVector @Composable get() = symbol("rounded_search")
    val SearchOff: ImageVector @Composable get() = symbol("rounded_search_off")
    val Map: ImageVector @Composable get() = symbol("rounded_map")
    val MapFilled: ImageVector @Composable get() = symbol("rounded_map_fill")
    val NearMe: ImageVector @Composable get() = symbol("rounded_near_me")
    val Warning: ImageVector @Composable get() = symbol("rounded_warning")
    val Refresh: ImageVector @Composable get() = symbol("rounded_refresh")
    val Close: ImageVector @Composable get() = symbol("rounded_close")
    val Work: ImageVector @Composable get() = symbol("rounded_work")
    val Star: ImageVector @Composable get() = symbol("rounded_star")
    val StarFilled: ImageVector @Composable get() = symbol("rounded_star_fill")
    val Schedule: ImageVector @Composable get() = symbol("rounded_schedule")
    val Route: ImageVector @Composable get() = symbol("rounded_route")
    val LocationOn: ImageVector @Composable get() = symbol("rounded_location_on")
    val MyLocation: ImageVector @Composable get() = symbol("rounded_my_location")
    val Home: ImageVector @Composable get() = symbol("rounded_home")
    val History: ImageVector @Composable get() = symbol("rounded_history")
    val DirectionsWalk: ImageVector @Composable get() = symbol("rounded_directions_walk")
    val ArrowBack: ImageVector @Composable get() = symbol("rounded_arrow_back")
    val Train: ImageVector @Composable get() = symbol("rounded_train")
    val Key: ImageVector @Composable get() = symbol("rounded_key")
    val Info: ImageVector @Composable get() = symbol("rounded_info")
    val Favorite: ImageVector @Composable get() = symbol("rounded_favorite")
    val FavoriteFilled: ImageVector @Composable get() = symbol("rounded_favorite_fill")
    val EventBusy: ImageVector @Composable get() = symbol("rounded_event_busy")
    val Error: ImageVector @Composable get() = symbol("rounded_error")
    val Edit: ImageVector @Composable get() = symbol("rounded_edit")
    val DirectionsTransit: ImageVector @Composable get() = symbol("rounded_directions_subway")
    val Directions: ImageVector @Composable get() = symbol("rounded_directions")
    val DirectionsFilled: ImageVector @Composable get() = symbol("rounded_directions_fill")
    val DeleteSweep: ImageVector @Composable get() = symbol("rounded_delete_sweep")
    val Delete: ImageVector @Composable get() = symbol("rounded_delete")
    val CheckCircle: ImageVector @Composable get() = symbol("rounded_check_circle")
    val ChevronRight: ImageVector @Composable get() = symbol("rounded_chevron_right")
    val ZoomIn: ImageVector @Composable get() = symbol("rounded_zoom_in")
    val Widgets: ImageVector @Composable get() = symbol("rounded_widgets")
    val Weekend: ImageVector @Composable get() = symbol("rounded_weekend")
    val Wc: ImageVector @Composable get() = symbol("rounded_wc")
    val Lightbulb: ImageVector @Composable get() = symbol("rounded_lightbulb")
    val VisibilityOff: ImageVector @Composable get() = symbol("rounded_visibility_off")
    val Visibility: ImageVector @Composable get() = symbol("rounded_visibility")
    val ViewInAr: ImageVector @Composable get() = symbol("rounded_view_in_ar")
    val Videocam: ImageVector @Composable get() = symbol("rounded_videocam")
    val Vibration: ImageVector @Composable get() = symbol("rounded_vibration")
    val Upload: ImageVector @Composable get() = symbol("rounded_upload")
    val Tram: ImageVector @Composable get() = symbol("rounded_tram")
    val SyncAlt: ImageVector @Composable get() = symbol("rounded_sync_alt")
    val SwapVert: ImageVector @Composable get() = symbol("rounded_swap_vert")
    val Stairs: ImageVector @Composable get() = symbol("rounded_stairs")
    val ShoppingBag: ImageVector @Composable get() = symbol("rounded_shopping_bag")
    val Settings: ImageVector @Composable get() = symbol("rounded_settings")
    val School: ImageVector @Composable get() = symbol("rounded_school")
    val Restaurant: ImageVector @Composable get() = symbol("rounded_restaurant")
    val Park: ImageVector @Composable get() = symbol("rounded_park")
    val Palette: ImageVector @Composable get() = symbol("rounded_palette")
    val NotificationsOff: ImageVector @Composable get() = symbol("rounded_notifications_off")
    val NotificationsActive: ImageVector @Composable get() = symbol("rounded_notifications_active")
    val NightsStay: ImageVector @Composable get() = symbol("rounded_nights_stay")
    val MoreVert: ImageVector @Composable get() = symbol("rounded_more_vert")
    val LocationOff: ImageVector @Composable get() = symbol("rounded_location_off")
    val LocalTaxi: ImageVector @Composable get() = symbol("rounded_local_taxi")
    val LocalHospital: ImageVector @Composable get() = symbol("rounded_local_hospital")
    val Layers: ImageVector @Composable get() = symbol("rounded_layers")
    val KeyboardArrowUp: ImageVector @Composable get() = symbol("rounded_keyboard_arrow_up")
    val KeyboardArrowDown: ImageVector @Composable get() = symbol("rounded_keyboard_arrow_down")
    val Hearing: ImageVector @Composable get() = symbol("rounded_hearing")
    val Grain: ImageVector @Composable get() = symbol("rounded_grain")
    val FormatListNumbered: ImageVector @Composable get() = symbol("rounded_format_list_numbered")
    val FitnessCenter: ImageVector @Composable get() = symbol("rounded_fitness_center")
    val FamilyRestroom: ImageVector @Composable get() = symbol("rounded_family_restroom")
    val ExpandMore: ImageVector @Composable get() = symbol("rounded_expand_more")
    val ExpandLess: ImageVector @Composable get() = symbol("rounded_expand_less")
    val Escalator: ImageVector @Composable get() = symbol("rounded_escalator")
    val Elevator: ImageVector @Composable get() = symbol("rounded_elevator")
    val Download: ImageVector @Composable get() = symbol("rounded_download")
    val DirectionsRailway: ImageVector @Composable get() = symbol("rounded_directions_railway")
    val DirectionsCar: ImageVector @Composable get() = symbol("rounded_directions_car")
    val DirectionsBus: ImageVector @Composable get() = symbol("rounded_directions_bus")
    val Contrast: ImageVector @Composable get() = symbol("rounded_contrast")
    val Coffee: ImageVector @Composable get() = symbol("rounded_coffee")
    val CloudOff: ImageVector @Composable get() = symbol("rounded_cloud_off")
    val Brightness4: ImageVector @Composable get() = symbol("rounded_brightness_4")
    val ArrowUpward: ImageVector @Composable get() = symbol("rounded_arrow_upward")
    val ArrowDownward: ImageVector @Composable get() = symbol("rounded_arrow_downward")
    val AcUnit: ImageVector @Composable get() = symbol("rounded_ac_unit")
    val VolumeUp: ImageVector @Composable get() = symbol("rounded_volume_up")
    val OpenInNew: ImageVector @Composable get() = symbol("rounded_open_in_new")
    val ArrowForward: ImageVector @Composable get() = symbol("rounded_arrow_forward")
    val AccessibleForward: ImageVector @Composable get() = symbol("rounded_accessible_forward")
    val Accessible: ImageVector @Composable get() = symbol("rounded_accessible")
    val ForYou: ImageVector @Composable get() = symbol("rounded_person")
    val ForYouFilled: ImageVector @Composable get() = symbol("rounded_person_fill")
    val Person: ImageVector @Composable get() = symbol("rounded_person")
    val PersonFilled: ImageVector @Composable get() = symbol("rounded_person_fill")
    val CreditCard: ImageVector @Composable get() = symbol("rounded_credit_card")
    val AddCard: ImageVector @Composable get() = symbol("rounded_add_card")
    val Contactless: ImageVector @Composable get() = symbol("rounded_contactless")
    val Nfc: ImageVector @Composable get() = symbol("rounded_nfc")
    val ReceiptLong: ImageVector @Composable get() = symbol("rounded_receipt_long")
    val Wallet: ImageVector @Composable get() = symbol("rounded_account_balance_wallet")
    val Toll: ImageVector @Composable get() = symbol("rounded_toll")

    /** The For You FAB icon: your custom `ic_myki_card` drawable once added, else the rounded credit card. */
    val MykiCard: ImageVector @Composable get() {
        val id = symbolResId(LocalContext.current, "ic_myki_card")
        return if (id != 0) ImageVector.vectorResource(id) else CreditCard
    }

    /** Every Material Symbols Rounded drawable the app looks for (used by the missing-icons check). */
    val allResourceNames: List<String> = listOf(
        "rounded_add",
        "rounded_search",
        "rounded_search_off",
        "rounded_map",
        "rounded_map_fill",
        "rounded_near_me",
        "rounded_warning",
        "rounded_refresh",
        "rounded_close",
        "rounded_work",
        "rounded_star",
        "rounded_star_fill",
        "rounded_schedule",
        "rounded_route",
        "rounded_location_on",
        "rounded_my_location",
        "rounded_home",
        "rounded_history",
        "rounded_directions_walk",
        "rounded_arrow_back",
        "rounded_train",
        "rounded_key",
        "rounded_info",
        "rounded_favorite",
        "rounded_favorite_fill",
        "rounded_event_busy",
        "rounded_error",
        "rounded_edit",
        "rounded_directions_subway",
        "rounded_directions",
        "rounded_directions_fill",
        "rounded_delete_sweep",
        "rounded_delete",
        "rounded_check_circle",
        "rounded_chevron_right",
        "rounded_zoom_in",
        "rounded_widgets",
        "rounded_weekend",
        "rounded_wc",
        "rounded_lightbulb",
        "rounded_visibility_off",
        "rounded_visibility",
        "rounded_view_in_ar",
        "rounded_videocam",
        "rounded_vibration",
        "rounded_upload",
        "rounded_tram",
        "rounded_sync_alt",
        "rounded_swap_vert",
        "rounded_stairs",
        "rounded_shopping_bag",
        "rounded_settings",
        "rounded_school",
        "rounded_restaurant",
        "rounded_park",
        "rounded_palette",
        "rounded_notifications_off",
        "rounded_notifications_active",
        "rounded_nights_stay",
        "rounded_more_vert",
        "rounded_location_off",
        "rounded_local_taxi",
        "rounded_local_hospital",
        "rounded_layers",
        "rounded_keyboard_arrow_up",
        "rounded_keyboard_arrow_down",
        "rounded_hearing",
        "rounded_grain",
        "rounded_format_list_numbered",
        "rounded_fitness_center",
        "rounded_family_restroom",
        "rounded_expand_more",
        "rounded_expand_less",
        "rounded_escalator",
        "rounded_elevator",
        "rounded_download",
        "rounded_directions_railway",
        "rounded_directions_car",
        "rounded_directions_bus",
        "rounded_contrast",
        "rounded_coffee",
        "rounded_cloud_off",
        "rounded_brightness_4",
        "rounded_arrow_upward",
        "rounded_arrow_downward",
        "rounded_ac_unit",
        "rounded_volume_up",
        "rounded_open_in_new",
        "rounded_arrow_forward",
        "rounded_accessible_forward",
        "rounded_accessible",
        "rounded_for_you",
        "rounded_for_you_fill",
        "rounded_person",
        "rounded_person_fill",
        "rounded_credit_card",
        "rounded_add_card",
        "rounded_contactless",
        "rounded_nfc",
        "rounded_receipt_long",
        "rounded_account_balance_wallet",
        "rounded_toll",
    )
}

private val resourceIds = ConcurrentHashMap<String, Int>()

/** Resolves a symbol drawable, accepting the common Material Symbols download names. */
@SuppressLint("DiscouragedApi")
fun symbolResId(context: Context, base: String): Int = resourceIds.getOrPut(base) {
    val res = context.resources
    listOf("${base}_24px", "${base}_24", base)
        .firstNotNullOfOrNull { name -> res.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 } }
        ?: 0
}

/** Drawn when a symbol file is missing (a unit test fails first), instead of quietly using an older icon. */
private val MissingSymbol: ImageVector = ImageVector.Builder(
    name = "missing_symbol",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).build()

/** A Material Symbols Rounded vector from res/drawable. These are the only icons the app uses. */
@Composable
private fun symbol(base: String): ImageVector {
    val id = symbolResId(LocalContext.current, base)
    return if (id != 0) ImageVector.vectorResource(id) else MissingSymbol
}
