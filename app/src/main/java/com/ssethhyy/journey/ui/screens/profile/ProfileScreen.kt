/*
 * Feature Module: Profile
 * File: ProfileScreen.kt
 * Description: Your profile: your name with an avatar that morphs between Material 3 Expressive
 * shapes, stats from the trips you've logged, and a way into your mykis. The FAB opens Settings.
 */

package com.ssethhyy.journey.ui.screens.profile

import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.components.usesBottomBar
import com.ssethhyy.journey.ui.components.ToolbarFab
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.myki.DayCount
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.ProfileStats
import com.ssethhyy.journey.data.myki.ProfileStatsCalculator
import com.ssethhyy.journey.data.myki.formatDollars
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.floatingBarBlur
import com.ssethhyy.journey.ui.components.pictogramRes
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val cards: StateFlow<List<MykiCard>> = container.mykiRepository.cards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favouriteCount: StateFlow<Int> = container.favouritesRepository.favouriteKeys
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setName(name: String) {
        viewModelScope.launch { container.settingsRepository.setUserName(name.trim()) }
    }
}

@Composable
fun ProfileScreen() {
    val vm = appViewModel { ProfileViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val favouriteCount by vm.favouriteCount.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val view = androidx.compose.ui.platform.LocalView.current
    val now by rememberNow()
    val stats = remember(cards, now) { ProfileStatsCalculator.compute(cards, now) }
    var editingName by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "Profile",
                subtitle = settings.userName.takeIf { it.isNotBlank() },
                onBackClick = navigator::back,
                pageFab = ToolbarFab(Sym.Settings, "Settings") { navigator.openSettings() },
            )
        },
        floatingActionButton = {
            if (!usesBottomBar()) FloatingActionButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    navigator.openSettings()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) { Icon(Sym.Settings, contentDescription = "Settings") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = pageBottomInset(104.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { ProfileHeader(name = settings.userName, onEditName = { editingName = true }) }
            if (!stats.hasTrips) {
                item(key = "hint") {
                    Text(
                        "Log a trip from Plan or a myki's page and your stats will build up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
            }
            item(key = "stats_1") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = "Trips this month",
                        value = stats.tripsThisMonth.toString(),
                        detail = "${stats.tripsAllTime} all time",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Spent this month",
                        value = formatDollars(stats.spentThisMonthCents),
                        detail = "${formatDollars(stats.spentAllTimeCents)} all time",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "mykis") { MykiSummary(cards = cards, onView = navigator::openMyki) }
            item(key = "stats_2") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val top = stats.topMode
                    StatCard(
                        label = "Most used",
                        value = top?.label ?: "None yet",
                        detail = top?.let { "${stats.modeCounts[it]} trip${if (stats.modeCounts[it] == 1) "" else "s"}" } ?: "Log a trip",
                        modifier = Modifier.weight(1f),
                        leading = top?.let { mode ->
                            { Image(painterResource(mode.pictogramRes), contentDescription = null, modifier = Modifier.size(28.dp)) }
                        },
                    )
                    StatCard(
                        label = "Free trips",
                        value = stats.freeTrips.toString(),
                        detail = "Early Bird, Free Tram Zone and more",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "week") { WeekChart(stats) }
            item(key = "stats_3") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(label = "Favourite stops", value = favouriteCount.toString(), detail = "On For You", modifier = Modifier.weight(1f))
                    StatCard(label = "Mykis", value = stats.cardCount.toString(), detail = if (stats.cardCount == 1) "card" else "cards", modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (editingName) {
        NameDialog(
            current = settings.userName,
            onDismiss = { editingName = false },
            onConfirm = {
                vm.setName(it)
                editingName = false
            },
        )
    }
}

@Composable
private fun ProfileHeader(name: String, onEditName: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MorphingAvatar(Modifier.size(152.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            name.ifBlank { "Your name" },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onEditName)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        FilledTonalButton(onClick = onEditName, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Sym.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (name.isBlank()) "Add your name" else "Change name")
        }
    }
}

// ---- Morphing avatar ----

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val AvatarShapes: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Flower,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Puffy,
    ).map { it.normalized() }
}

/** A shape part-way through a morph, scaled to whatever box it is drawn in. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        val matrix = android.graphics.Matrix().apply { setScale(size.width, size.height) }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}

/**
 * A person in a shape that keeps changing: it morphs to the next Material 3 Expressive shape every few
 * seconds with a springy overshoot, turns slowly, and shifts between the theme's container colours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingAvatar(modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_600)
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
            index = (index + 1) % AvatarShapes.size
        }
    }
    val morph = remember(index) { Morph(AvatarShapes[index], AvatarShapes[(index + 1) % AvatarShapes.size]) }
    val turn = rememberInfiniteTransition(label = "avatar_turn").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "avatar_rotation",
    )
    val colours = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val (container, onContainer) = colours[index % colours.size]
    val containerColour by animateColorAsState(container, tween(900), label = "avatar_container")
    val iconColour by animateColorAsState(onContainer, tween(900), label = "avatar_icon")

    Box(modifier.semantics { contentDescription = "Profile picture" }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .rotate(turn.value)
                .clip(MorphShape(morph, progress.value))
                .background(containerColour),
        )
        Icon(Sym.PersonFilled, contentDescription = null, tint = iconColour, modifier = Modifier.fillMaxSize(0.5f))
    }
}

// ---- Cards ----

@Composable
private fun StatCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = container, contentColor = content) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.75f), maxLines = 1)
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f), maxLines = 2)
        }
    }
}

@Composable
private fun WeekChart(stats: ProfileStats) {
    val most = (stats.last7Days.maxOfOrNull { it.trips } ?: 0).coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                Text(
                    stats.busiestDay?.let { "Busiest: ${it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${it.trips} trip${if (it.trips == 1) "" else "s"}" }
                        ?: "No trips logged this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                stats.last7Days.forEachIndexed { i, day -> DayBar(day, most, isToday = i == stats.last7Days.lastIndex, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayBar(day: DayCount, most: Int, isToday: Boolean, modifier: Modifier = Modifier) {
    val fraction = day.trips.toFloat() / most
    val barHeight by androidx.compose.animation.core.animateDpAsState((6 + 52 * fraction).dp, spring(dampingRatio = 0.7f), label = "bar")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        if (day.trips > 0) Text(day.trips.toString(), style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier
                .width(22.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(if (day.trips == 0) MaterialTheme.colorScheme.surfaceContainerHighest else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MykiSummary(cards: List<MykiCard>, onView: () -> Unit) {
    val main = cards.firstOrNull()
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Sym.CreditCard, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (main == null) "No mykis yet" else "Default myki", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        main?.let { card -> card.nickname + (card.balanceCents?.let { " · ${formatDollars(it)}" } ?: "") } ?: "Add one to track your balance",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (main?.isLow == true) Text("Low balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = onView, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Sym.CreditCard, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (cards.isEmpty()) "Add a myki" else "View mykis")
            }
        }
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.PersonFilled, null) },
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(24) },
                label = { Text("Name") },
                supportingText = { Text("Used in your For You greeting") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
