/*
 * Feature Module: Favourites
 * File: FavouritePicker.kt
 * Description: The sheet that opens when you favourite a stop with more than one line or direction: keep every
 * departure (as before), or pick just the lines and directions you use. Also used to edit an existing favourite.
 */

package com.ssethhyy.journey.ui.screens.favourites

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.FavouritePickerRequest
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.repository.FavouriteLine
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RouteBadge
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.hapticConfirm
import com.ssethhyy.journey.ui.components.hapticSelect
import com.ssethhyy.journey.ui.loadCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A line at the stop with the directions it runs in. */
data class LineChoice(val route: Route, val directions: List<FavouriteLine>)

object FavouriteChoices {
    /** The lines and directions departing a stop, from its upcoming departures, grouped by line. */
    fun from(departures: List<Departure>, routes: List<Route>): List<LineChoice> {
        val byRoute = departures.filter { it.route != null }.groupBy { it.route!!.id }
        val known = routes.associateBy { it.id } + departures.mapNotNull { it.route }.associateBy { it.id }
        return known.values
            .sortedWith(compareBy({ it.badgeLabel.length }, { it.badgeLabel }))
            .map { route ->
                val directions = byRoute[route.id].orEmpty()
                    .distinctBy { it.directionId }
                    .map { d ->
                        FavouriteLine(route.id, route.badgeLabel, d.directionId, (d.directionName ?: d.destination)?.takeIf { it.isNotBlank() })
                    }
                    .sortedBy { it.directionName }
                    // A line with no upcoming departures can still be chosen as a whole.
                    .ifEmpty { listOf(FavouriteLine(route.id, route.badgeLabel)) }
                LineChoice(route, directions)
            }
    }

    /** Nothing to choose between: one line in one direction (or nothing known at all). */
    fun isSingle(choices: List<LineChoice>): Boolean = choices.sumOf { it.directions.size } <= 1
}

class FavouritePickerViewModel(private val container: AppContainer) : ViewModel() {
    private val _choices = MutableStateFlow<LoadState<List<LineChoice>>>(LoadState.Loading)
    val choices: StateFlow<LoadState<List<LineChoice>>> = _choices.asStateFlow()

    fun load(request: FavouritePickerRequest, onNothingToChoose: () -> Unit) {
        _choices.value = LoadState.Loading
        viewModelScope.launch {
            val result = loadCatching {
                val board = container.ptvRepository.departures(request.stop.mode, request.stop.id, maxResults = 30)
                FavouriteChoices.from(board.departures, board.routes)
            }
            _choices.value = result
            // One line, one direction: nothing to ask, so just add it.
            if (!request.editing && result is LoadState.Success && FavouriteChoices.isSingle(result.data)) onNothingToChoose()
        }
    }

    fun save(request: FavouritePickerRequest, lines: List<FavouriteLine>) {
        container.appScope.launch {
            if (request.editing) {
                container.favouritesRepository.setLines(request.stop.key, lines)
            } else {
                container.favouritesRepository.add(request.stop, lines)
            }
        }
    }
}

/** Shown above every screen whenever a stop is waiting for its lines to be chosen. */
@Composable
fun FavouritePickerHost() {
    val container = LocalContext.current.appContainer
    val request by container.favouritePicker.collectAsStateWithLifecycle()
    request?.let { FavouritePickerSheet(it, onDismiss = container::dismissFavouritePicker) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FavouritePickerSheet(request: FavouritePickerRequest, onDismiss: () -> Unit) {
    val vm = appViewModel(key = "favourite_picker") { FavouritePickerViewModel(it) }
    val context = LocalContext.current
    val choices by vm.choices.collectAsStateWithLifecycle()
    var everything by remember(request) { mutableStateOf(request.current.isEmpty()) }
    var picked by remember(request) { mutableStateOf(request.current.map { it.routeId to it.directionId }.toSet()) }

    LaunchedEffect(request) {
        vm.load(request) {
            vm.save(request, emptyList())
            Toast.makeText(context, "Added ${request.stop.name} to favourites", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    StandardBottomSheet(
        onDismissRequest = onDismiss,
        title = if (request.editing) "Lines to show" else "Add to favourites",
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModePictogram(request.stop.mode, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(request.stop.name, style = MaterialTheme.typography.titleMediumEmphasized)
        }
        val list = (choices as? LoadState.Success)?.data
        when {
            choices is LoadState.Loading -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                LoadingIndicator(Modifier.size(40.dp))
            }
            choices is LoadState.Error -> Text(
                "Couldn't load this stop's lines. You can still add every departure.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChoiceRow(
            selected = everything,
            title = "All lines and directions",
            subtitle = "Every departure from this stop",
            onClick = {
                everything = true
                picked = emptySet()
            },
        )
        if (list != null && !FavouriteChoices.isSingle(list)) {
            ChoiceRow(
                selected = !everything,
                title = "Only some",
                subtitle = "Pick the lines and directions you use",
                onClick = { everything = false },
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                list.forEach { choice ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RouteBadge(choice.route)
                            if (choice.route.name.isNotBlank() && choice.route.name != choice.route.badgeLabel) {
                                Spacer(Modifier.width(8.dp))
                                Text(choice.route.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choice.directions.forEach { line ->
                                val id = line.routeId to line.directionId
                                FilterChip(
                                    selected = !everything && id in picked,
                                    onClick = hapticSelect {
                                        everything = false
                                        picked = if (id in picked) picked - id else picked + id
                                    },
                                    label = { Text(line.directionName?.let { "To $it" } ?: "Both directions") },
                                    leadingIcon = if (!everything && id in picked) {
                                        { Icon(Sym.Check, null, Modifier.size(18.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            val canSave = everything || picked.isNotEmpty()
            Button(
                enabled = canSave,
                onClick = hapticConfirm {
                    val lines = if (everything) emptyList() else list.orEmpty().flatMap { it.directions }.filter { (it.routeId to it.directionId) in picked }
                    vm.save(request, lines)
                    onDismiss()
                },
            ) { Text(if (request.editing) "Save" else "Add to favourites") }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = hapticSelect(onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
