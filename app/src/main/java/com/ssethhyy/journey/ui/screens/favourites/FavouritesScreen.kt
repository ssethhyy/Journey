/*
 * Feature Module: Favourites
 * File: FavouritesScreen.kt
 * Description: Favourite stops grouped by mode, each with live departures.
 */

package com.ssethhyy.journey.ui.screens.favourites

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.FloatingPullToRefreshBox
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.PlaceKind
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.ui.components.PlaceEditorSheet
import com.ssethhyy.journey.ui.components.PlacesRow
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.DepartureRow
import com.ssethhyy.journey.ui.components.DepartureSkeleton
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.InlineError
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.containerColor
import com.ssethhyy.journey.ui.components.contentColor
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.home.ToolbarClearance
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(onAddFromNearby: () -> Unit, onAddFromSearch: () -> Unit) {
    val vm = appViewModel { FavouritesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val now by rememberNow()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val headerGone by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > with(density) { 72.dp.roundToPx() }
        }
    }
    var renaming by remember { mutableStateOf<FavouriteStop?>(null) }
    var askingName by rememberSaveable { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<Pair<PlaceKind, SavedPlace?>?>(null) }

    AutoRefreshEffect(enabled = state.settings.autoRefresh && state.favourites.isNotEmpty()) { vm.refresh(force = true) }

    val onRemove: (FavouriteStop) -> Unit = { fav ->
        vm.remove(fav.key)
        scope.launch {
            val result = snackbarHostState.showSnackbar("Removed ${fav.displayName}", actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) vm.restore(fav)
        }
    }

    Scaffold(
        topBar = {
            // The big greeting is the first list item; once it has scrolled away this pill takes over.
            ReusableTopAppBar(
                isTabRoot = true,
                title = "For You",
                subtitle = TimeFormatter.shortToday(now),
                visible = headerGone || !state.isLoaded,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = ToolbarClearance - 24.dp)) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        FloatingPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh(force = true, userInitiated = true) },
            topInset = innerPadding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !state.isLoaded -> LoadingState()
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().floatingBarBlur(innerPadding.calculateTopPadding()),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 4.dp, bottom = ToolbarClearance + innerPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "greeting") {
                        GreetingHeader(
                            greeting = TimeFormatter.personalGreeting(state.settings.userName, now),
                            today = TimeFormatter.todayLine(now),
                        )
                    }
                    if (state.settings.userName.isBlank()) {
                        item(key = "name_prompt") {
                            androidx.compose.material3.AssistChip(
                                onClick = { askingName = true },
                                label = { Text("Add your name for a personal greeting") },
                                leadingIcon = { Icon(Sym.PersonFilled, null, Modifier.size(18.dp)) },
                                shape = androidx.compose.foundation.shape.CircleShape,
                            )
                        }
                    }
                    item(key = "places") {
                        PlacesRow(
                            places = state.places,
                            onOpen = { navigator.planTo(it.toPlanLocation()) },
                            onEdit = { editingPlace = it.kind to it },
                            onCreate = { kind -> editingPlace = kind to null },
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                    if (state.favourites.isEmpty()) {
                        item(key = "empty") { EmptyFavourites(onAddFromNearby, onAddFromSearch) }
                        return@LazyColumn
                    }
                    item(key = "status") {
                        NetworkStatusCard(
                            disruptionCount = state.relevantDisruptions.size,
                            severeCount = state.relevantDisruptions.count { it.severity.ordinal <= 1 },
                            onClick = navigator::openDisruptions,
                        )
                    }
                    if (state.availableModes.size > 1) {
                        item(key = "filters") {
                            ModeFilterRow(
                                selected = state.filter,
                                onToggle = vm::toggleFilter,
                                modes = state.availableModes,
                                contentPadding = PaddingValues(0.dp),
                            )
                        }
                    }
                    val firstMode = state.visibleGroups.firstOrNull()?.first
                    state.visibleGroups.forEach { (mode, favourites) ->
                        item(key = "header_${mode.name}") {
                            SectionLabel(
                                mode.pluralLabel,
                                Modifier.animateItem(),
                                // The first group header carries "Add", replacing the old For You FAB.
                                trailing = if (mode == firstMode) {
                                    { AddFavouriteButton(onClick = onAddFromNearby) }
                                } else null,
                            )
                        }
                        items(favourites, key = { it.key }) { fav ->
                            FavouriteStopCard(
                                favourite = fav,
                                board = state.boards[fav.key],
                                now = now,
                                departuresToShow = state.settings.departuresPerStop,
                                timeDisplay = state.settings.timeDisplay,
                                canMoveUp = favourites.first() != fav,
                                canMoveDown = favourites.last() != fav,
                                onOpen = { navigator.openStop(fav.toStop()) },
                                onOpenTrip = { dep ->
                                    dep.runRef?.let { navigator.openTrip(it, fav.mode, fav.stopId, dep.headsign) }
                                },
                                onRetry = { vm.refresh(force = true) },
                                onRename = { renaming = fav },
                                onMove = { delta -> vm.move(fav.key, delta) },
                                onRemove = { onRemove(fav) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    editingPlace?.let { (kind, place) ->
        PlaceEditorSheet(initialKind = kind, existing = place, onDismiss = { editingPlace = null })
    }

    if (askingName) {
        NameDialog(
            onDismiss = { askingName = false },
            onConfirm = { name ->
                vm.setUserName(name)
                askingName = false
            },
        )
    }

    renaming?.let { fav ->
        RenameDialog(
            favourite = fav,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                vm.rename(fav.key, name)
                renaming = null
            },
        )
    }
}

@Composable
private fun AddFavouriteButton(onClick: () -> Unit) {
    val view = LocalView.current
    FilledTonalButton(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(Sym.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add", style = MaterialTheme.typography.labelLarge)
    }
}

/** The big greeting at the top of For You. */
@Composable
private fun GreetingHeader(greeting: String, today: String) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
        Text(greeting, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(today, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkStatusCard(disruptionCount: Int, severeCount: Int, onClick: () -> Unit) {
    val view = LocalView.current
    val severity = when {
        severeCount > 0 -> com.ssethhyy.journey.data.model.DisruptionSeverity.MAJOR
        disruptionCount > 0 -> com.ssethhyy.journey.data.model.DisruptionSeverity.MINOR
        else -> com.ssethhyy.journey.data.model.DisruptionSeverity.INFO
    }
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        shape = RoundedCornerShape(28.dp),
        color = if (disruptionCount == 0) MaterialTheme.colorScheme.primaryContainer else severity.containerColor(),
        contentColor = if (disruptionCount == 0) MaterialTheme.colorScheme.onPrimaryContainer else severity.contentColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (disruptionCount == 0) Sym.CheckCircle else Sym.Warning, contentDescription = null)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (disruptionCount) {
                        0 -> "Good service"
                        1 -> "1 service update"
                        else -> "$disruptionCount service updates"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (disruptionCount == 0) "No reported disruptions for your modes" else "Tap to see what's affecting your travel",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteStopCard(
    favourite: FavouriteStop,
    board: LoadState<DepartureBoard>?,
    now: Instant,
    departuresToShow: Int,
    timeDisplay: com.ssethhyy.journey.data.repository.TimeDisplay,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onOpenTrip: (com.ssethhyy.journey.data.model.Departure) -> Unit,
    onRetry: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            onOpen()
                        },
                        onLongClick = {
                            HapticUtil.performHeavyHaptic(view)
                            menuOpen = true
                        },
                        onLongClickLabel = "More options",
                    )
                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModePictogram(favourite.mode, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(favourite.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(
                        favourite.nickname?.let { favourite.name },
                        favourite.suburb,
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    HapticIconButton(Sym.MoreVert, "More options for ${favourite.displayName}", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Sym.Edit, null) }, onClick = {
                            menuOpen = false
                            onRename()
                        })
                        if (canMoveUp) {
                            DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Sym.ArrowUpward, null) }, onClick = {
                                menuOpen = false
                                onMove(-1)
                            })
                        }
                        if (canMoveDown) {
                            DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Sym.ArrowDownward, null) }, onClick = {
                                menuOpen = false
                                onMove(1)
                            })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Sym.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
            Column(
                Modifier
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(22.dp)),
            ) {
                when (board) {
                    null, LoadState.Loading -> DepartureSkeleton(rows = departuresToShow.coerceAtMost(3))
                    is LoadState.Error -> InlineError(board.error.message ?: "Couldn't load departures", onRetry)
                    is LoadState.Success -> {
                        val upcoming = board.data.departures.filter { d -> d.bestTime?.let { it.isAfter(now.minusSeconds(60)) } ?: false }
                            .take(departuresToShow)
                        if (upcoming.isEmpty()) {
                            Text(
                                "No upcoming departures",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            upcoming.forEachIndexed { index, dep ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                DepartureRow(
                                    departure = dep,
                                    now = now,
                                    display = timeDisplay,
                                    hasDisruption = dep.disruptionIds.isNotEmpty(),
                                    onClick = { onOpenTrip(dep) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavourites(onAddFromNearby: () -> Unit, onAddFromSearch: () -> Unit) {
    Column {
        MessageState(
            icon = Sym.Star,
            title = "No favourites yet",
            message = "Save the stations and stops you use most to see their next departures at a glance.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddFromNearby) {
                    Icon(Sym.NearMe, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nearby")
                }
                FilledTonalButton(onClick = onAddFromSearch) {
                    Icon(Sym.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search")
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(favourite: FavouriteStop, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(favourite.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.Edit, null) },
        title = { Text("Rename favourite") },
        text = {
            Column {
                Text(favourite.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text("Nickname") },
                    placeholder = { Text("e.g. Work, Home") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = {
            Row {
                if (favourite.nickname != null) TextButton(onClick = { onConfirm(null) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.PersonFilled, null) },
        title = { Text("What should we call you?") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                label = { Text("Your name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
