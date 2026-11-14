package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.foryou.DateStyle
import com.ssethhyy.journey.data.repository.ThemeMode
import com.ssethhyy.journey.data.weather.TemperatureUnit
import com.ssethhyy.journey.data.weather.WeatherCondition
import com.ssethhyy.journey.data.weather.WeatherNow
import com.ssethhyy.journey.data.weather.WeatherProvider
import com.ssethhyy.journey.ui.screens.favourites.ForYouDates
import com.ssethhyy.journey.ui.screens.favourites.ForYouHeader
import com.ssethhyy.journey.ui.theme.JourneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZonedDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ForYouHeaderTest {
    @get:Rule val rule = createComposeRule()

    private val morning = ZonedDateTime.of(2026, 11, 14, 9, 30, 0, 0, ZoneId.of("Australia/Melbourne")).toInstant()
    private val rain = WeatherNow(temperatureC = 21.2, condition = WeatherCondition.RAIN, description = "Light rain", provider = WeatherProvider.OPENWEATHERMAP)

    @Test
    fun formatsTheDateLikeTheDesign() {
        assertEquals("Today is the 14th of November 2026", ForYouDates.format(DateStyle.SENTENCE, morning, java.util.Locale.UK))
        assertEquals("14/11/26", ForYouDates.format(DateStyle.NUMERIC, morning, java.util.Locale.UK))
        assertEquals("Good morning, Seth", ForYouDates.greeting("Seth", morning))
        assertEquals("Good morning", ForYouDates.greeting(" ", morning))
    }

    @Test
    fun drawsExpanded() = draw(expanded = true)

    @Test
    fun drawsFolded() = draw(expanded = false)

    private fun draw(expanded: Boolean) {
        rule.setContent {
            JourneyTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    ForYouHeader(
                        greeting = "Good morning, Seth",
                        date = "Today is the 14th of November 2026",
                        compactDate = "14/11/26",
                        weather = LoadState.Success(rain),
                        unit = TemperatureUnit.CELSIUS,
                        showDetails = false,
                        expanded = expanded,
                        onExpandedChange = {},
                        onEdit = {},
                        onSetUpWeather = {},
                        onRetryWeather = {},
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("21°, Light rain").assertExists()
        rule.onNodeWithContentDescription("Edit For You").assertExists()
        if (expanded) rule.onNodeWithText("Good morning, Seth").assertExists() else rule.onNodeWithText("14/11/26").assertExists()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve(if (expanded) "for_you_header.png" else "for_you_header_folded.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun drawsTheDisruptionsCardFoldedAndOpen() {
        val line = com.ssethhyy.journey.data.model.Route(id = 8, mode = com.ssethhyy.journey.data.model.TransportMode.TRAIN, name = "Hurstbridge", number = "")
        val list = listOf(
            com.ssethhyy.journey.data.model.Disruption(
                id = 1, title = "Buses replace trains between Clifton Hill and Eltham from 8:30pm", description = "", url = null,
                status = "Current", type = "Planned Works", publishedOn = null, fromDate = null, toDate = null,
                routes = listOf(line), stopNames = emptyList(), mode = com.ssethhyy.journey.data.model.TransportMode.TRAIN,
            ),
        )
        var collapsed by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            JourneyTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                androidx.compose.foundation.layout.Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    com.ssethhyy.journey.ui.screens.favourites.DisruptionsWidget(
                        disruptions = list,
                        level = com.ssethhyy.journey.data.foryou.DisruptionLevel.MAJOR,
                        maxShown = 4,
                        collapsed = collapsed,
                        onToggle = { collapsed = !collapsed },
                        onOpenAll = {},
                    )
                }
            }
        }
        rule.onNodeWithText("1 major disruption").assertExists()
        rule.onNodeWithText("Buses replace trains", substring = true).assertDoesNotExist()
        rule.onNodeWithText("1 major disruption").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Buses replace trains", substring = true).assertExists()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("for_you_disruptions.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
