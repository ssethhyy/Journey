/*
 * Feature Module: App
 * File: MainActivity.kt
 * Description: Single activity hosting the Navigation 3 display.
 */

package com.ssethhyy.journey

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import com.ssethhyy.journey.widget.JourneyWidget
import android.os.SystemClock
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.ui.navigation.DisruptionsKey
import com.ssethhyy.journey.ui.navigation.HomeKey
import com.ssethhyy.journey.ui.navigation.HomeTab
import com.ssethhyy.journey.ui.navigation.IslandSettingsKey
import com.ssethhyy.journey.ui.navigation.FaresKey
import com.ssethhyy.journey.ui.navigation.ProfileKey
import com.ssethhyy.journey.ui.screens.profile.ProfileScreen
import com.ssethhyy.journey.ui.navigation.MykiCardKey
import com.ssethhyy.journey.ui.screens.myki.FaresScreen
import com.ssethhyy.journey.ui.navigation.MykiWalletKey
import com.ssethhyy.journey.ui.screens.myki.MykiCardScreen
import com.ssethhyy.journey.ui.screens.myki.MykiWalletScreen
import com.ssethhyy.journey.ui.screens.settings.IslandSettingsScreen
import com.ssethhyy.journey.ui.navigation.JourneyDetailKey
import com.ssethhyy.journey.ui.screens.plan.JourneyDetailScreen
import com.ssethhyy.journey.ui.navigation.LocalNavigator
import com.ssethhyy.journey.ui.navigation.Navigator
import com.ssethhyy.journey.ui.navigation.RouteDetailKey
import com.ssethhyy.journey.ui.navigation.SearchKey
import com.ssethhyy.journey.ui.navigation.SettingsKey
import com.ssethhyy.journey.ui.navigation.StopDetailKey
import com.ssethhyy.journey.ui.navigation.TripKey
import com.ssethhyy.journey.ui.screens.disruptions.DisruptionsScreen
import com.ssethhyy.journey.ui.screens.home.HomeScreen
import com.ssethhyy.journey.ui.screens.route.RouteDetailScreen
import com.ssethhyy.journey.ui.screens.search.SearchScreen
import com.ssethhyy.journey.ui.screens.settings.SettingsScreen
import com.ssethhyy.journey.ui.screens.stop.StopDetailScreen
import com.ssethhyy.journey.ui.screens.trip.TripScreen
import com.ssethhyy.journey.ui.theme.JourneyTheme

class MainActivity : ComponentActivity() {
    /** A stop opened from the home-screen widget, consumed by [JourneyRoot]. */
    private val widgetStop = mutableStateOf<StopDetailKey?>(null)

    /** Set when Essentials opens Journey's island settings. */
    private val openIsland = mutableStateOf(false)
    private val openMykiCard = mutableStateOf<String?>(null)

    @Volatile
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false

        widgetStop.value = stopFromIntent(intent)
        if (isIslandSettingsIntent(intent)) openIsland.value = true
        mykiCardFromIntent(intent)?.let { openMykiCard.value = it }
        val container = appContainer
        // Keep the splash up until settings (theme) are known to avoid a theme flash,
        // but never longer than a moment.
        val splashStart = SystemClock.uptimeMillis()
        lifecycleScope.launch {
            container.settingsRepository.settings.first()
            settingsLoaded = true
        }
        splashScreen.setKeepOnScreenCondition { !settingsLoaded && SystemClock.uptimeMillis() - splashStart < 800 }
        setupExitAnimation(splashScreen)

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: AppSettings()
            JourneyTheme(
                themeMode = current.themeMode,
                dynamicColor = current.dynamicColor,
                pitchBlack = current.pitchBlack,
            ) {
                JourneyRoot(
                    container = container,
                    pendingStop = widgetStop.value,
                    onPendingConsumed = { widgetStop.value = null },
                    openIslandSettings = openIsland.value,
                    onIslandSettingsOpened = { openIsland.value = false },
                    openMykiCardId = openMykiCard.value,
                    onMykiCardOpened = { openMykiCard.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stopFromIntent(intent)?.let { widgetStop.value = it }
        if (isIslandSettingsIntent(intent)) openIsland.value = true
        mykiCardFromIntent(intent)?.let { openMykiCard.value = it }
    }

    /** The myki card a low-balance notification was tapped for. */
    private fun mykiCardFromIntent(intent: Intent?): String? =
        intent?.getStringExtra(com.ssethhyy.journey.data.myki.MykiNotifier.EXTRA_MYKI_CARD_ID)

    /** True when launched from the gear next to Journey in Essentials' Island settings. */
    private fun isIslandSettingsIntent(intent: Intent?): Boolean =
        intent?.component?.className == "com.ssethhyy.journey.IslandSettingsActivity"

    private fun stopFromIntent(intent: Intent?): StopDetailKey? {
        val stopId = intent?.getIntExtra(JourneyWidget.EXTRA_STOP_ID, -1) ?: -1
        if (stopId < 0) return null
        return StopDetailKey(
            stopId = stopId,
            routeType = intent!!.getIntExtra(JourneyWidget.EXTRA_ROUTE_TYPE, 0),
            name = intent.getStringExtra(JourneyWidget.EXTRA_STOP_NAME).orEmpty(),
        )
    }

    private fun setupExitAnimation(splashScreen: SplashScreen) {
        splashScreen.setOnExitAnimationListener { provider ->
            val splashView = provider.view
            val icon = runCatching { provider.iconView }.getOrNull()
            val fadeOut = ObjectAnimator.ofFloat(splashView, "alpha", 1f, 0f).apply {
                interpolator = AnticipateInterpolator()
                duration = 450
            }
            fadeOut.doOnEnd {
                provider.remove()
                enableEdgeToEdge()
            }
            if (icon != null) {
                listOf("scaleX", "scaleY").forEach { property ->
                    ObjectAnimator.ofFloat(icon, property, 1f, 0.5f).apply {
                        interpolator = AnticipateInterpolator()
                        duration = 450
                        start()
                    }
                }
            }
            fadeOut.start()
        }
    }
}

@Composable
fun JourneyRoot(
    container: AppContainer,
    pendingStop: StopDetailKey? = null,
    onPendingConsumed: () -> Unit = {},
    openIslandSettings: Boolean = false,
    onIslandSettingsOpened: () -> Unit = {},
    openMykiCardId: String? = null,
    onMykiCardOpened: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(HomeKey)
    // One-shot tab change requested from outside the home screen (e.g. showing a stop on the map).
    var requestedTab by remember { mutableStateOf<HomeTab?>(null) }
    val navigator = remember(backStack) { Navigator(backStack, container) { requestedTab = it } }
    LaunchedEffect(openMykiCardId) {
        openMykiCardId?.let {
            navigator.openMykiCard(it)
            onMykiCardOpened()
        }
    }
    LaunchedEffect(openIslandSettings) {
        if (openIslandSettings) {
            navigator.openIslandSettings()
            onIslandSettingsOpened()
        }
    }
    LaunchedEffect(pendingStop) {
        if (pendingStop != null) {
            navigator.open(pendingStop)
            onPendingConsumed()
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            onBack = { navigator.back() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
            },
            popTransitionSpec = {
                (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            },
            predictivePopTransitionSpec = {
                (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            },
            entryProvider = entryProvider {
                entry<HomeKey> { HomeScreen(requestedTab = requestedTab, onTabRequestHandled = { requestedTab = null }) }
                entry<StopDetailKey> { key -> StopDetailScreen(key) }
                entry<RouteDetailKey> { key -> RouteDetailScreen(key) }
                entry<TripKey> { key -> TripScreen(key) }
                entry<SearchKey> { SearchScreen() }
                entry<SettingsKey> { SettingsScreen() }
                entry<DisruptionsKey> { DisruptionsScreen() }
                entry<IslandSettingsKey> { IslandSettingsScreen() }
                entry<MykiWalletKey> { MykiWalletScreen() }
                entry<MykiCardKey> { key -> MykiCardScreen(key) }
                entry<FaresKey> { FaresScreen() }
                entry<ProfileKey> { ProfileScreen() }
                entry<JourneyDetailKey> { key -> JourneyDetailScreen(key) }
            },
        )
    }
}
