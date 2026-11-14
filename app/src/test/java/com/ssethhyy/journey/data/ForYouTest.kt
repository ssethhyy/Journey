package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.foryou.DisruptionLevel
import com.ssethhyy.journey.data.foryou.DisruptionScope
import com.ssethhyy.journey.data.foryou.DisruptionsConfig
import com.ssethhyy.journey.data.foryou.ForYouDisruptions
import com.ssethhyy.journey.data.foryou.ForYouLayout
import com.ssethhyy.journey.data.foryou.ForYouWidget
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteLine
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.FavouritesCodec
import com.ssethhyy.journey.data.weather.TemperatureUnit
import com.ssethhyy.journey.data.weather.WeatherCondition
import com.ssethhyy.journey.data.weather.WeatherParsers
import com.ssethhyy.journey.data.weather.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ForYouTest {

    // --- Weather ---

    @Test
    fun parsesOpenWeatherMap() {
        val body = """
            {"weather":[{"id":500,"main":"Rain","description":"light rain","icon":"10d"}],
             "main":{"temp":21.3,"feels_like":20.1,"temp_min":15.2,"temp_max":24.8},
             "sys":{"sunrise":1,"sunset":2},"name":"Melbourne"}
        """.trimIndent()
        val w = WeatherParsers.parse(WeatherProvider.OPENWEATHERMAP, body)
        assertEquals(21.3, w.temperatureC, 0.01)
        assertEquals(WeatherCondition.RAIN, w.condition)
        assertEquals("Light rain", w.description)
        assertTrue(w.isDay)
        assertEquals(24.8, w.highC!!, 0.01)
        assertEquals("Melbourne", w.place)
    }

    @Test
    fun parsesWeatherApiWithForecastHighAndLow() {
        val body = """
            {"location":{"name":"Fitzroy"},
             "current":{"temp_c":12.0,"feelslike_c":10.5,"is_day":0,"condition":{"text":"Partly cloudy","code":1003}},
             "forecast":{"forecastday":[{"day":{"maxtemp_c":18.0,"mintemp_c":9.0}}]}}
        """.trimIndent()
        val w = WeatherParsers.parse(WeatherProvider.WEATHERAPI, body)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, w.condition)
        assertFalse(w.isDay)
        assertEquals(9.0, w.lowC!!, 0.01)
        assertEquals("Fitzroy", w.place)
    }

    @Test
    fun parsesOpenMeteo() {
        val body = """
            {"current":{"temperature_2m":30.4,"apparent_temperature":31.0,"weather_code":95,"is_day":1},
             "daily":{"temperature_2m_max":[33.1],"temperature_2m_min":[19.6]}}
        """.trimIndent()
        val w = WeatherParsers.parse(WeatherProvider.OPEN_METEO, body)
        assertEquals(WeatherCondition.STORM, w.condition)
        assertEquals(33.1, w.highC!!, 0.01)
    }

    @Test
    fun mapsConditionCodes() {
        assertEquals(WeatherCondition.CLEAR, WeatherParsers.openWeatherCondition(800))
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherParsers.openWeatherCondition(502))
        assertEquals(WeatherCondition.FOG, WeatherParsers.openWeatherCondition(741))
        assertEquals(WeatherCondition.DRIZZLE, WeatherParsers.openMeteoCondition(53))
        assertEquals(WeatherCondition.SNOW, WeatherParsers.weatherApiCondition(1219))
        assertEquals(WeatherCondition.STORM, WeatherParsers.weatherApiCondition(9999, "Thundery outbreaks"))
    }

    @Test
    fun formatsTemperatures() {
        assertEquals("21°", TemperatureUnit.CELSIUS.format(21.3))
        assertEquals("70°", TemperatureUnit.FAHRENHEIT.format(21.3))
    }

    // --- Layout ---

    @Test
    fun defaultLayoutPutsFavouritesUnderPlaces() {
        val widgets = ForYouLayout().widgets
        assertEquals(listOf(ForYouWidget.GREETING, ForYouWidget.PLACES, ForYouWidget.FAVOURITES), widgets.take(3))
        assertTrue(ForYouLayout().isCollapsed(ForYouWidget.DISRUPTIONS))
        assertTrue(ForYouWidget.SHORTCUTS in ForYouLayout().available)
    }

    @Test
    fun movesRemovesAndAddsWidgets() {
        val layout = ForYouLayout()
        val moved = layout.move(from = 5, to = 1)
        assertEquals(ForYouWidget.NEAR_YOU, moved.widgets[1])
        val removed = moved.remove(ForYouWidget.PLACES)
        assertTrue(ForYouWidget.PLACES in removed.available)
        val added = removed.add(ForYouWidget.PLACES, at = 0)
        assertEquals(ForYouWidget.PLACES, added.widgets.first())
        // Adding twice does nothing.
        assertEquals(added, added.add(ForYouWidget.PLACES))
    }

    @Test
    fun layoutSurvivesARoundTripAndBadData() {
        val layout = ForYouLayout(showTitles = false).toggleCollapsed(ForYouWidget.COMMUTES)
        assertEquals(layout, ForYouLayout.decode(ForYouLayout.encode(layout)))
        assertNull(ForYouLayout.decode("not json"))
        // Unknown fields from a newer version are ignored.
        assertEquals(ForYouLayout(), ForYouLayout.decode("""{"somethingNew":true}"""))
    }

    @Test
    fun reordersFavouritesByKey() {
        val a = fav(1, "Alphington")
        val b = fav(2, "Fairfield")
        val c = fav(3, "Dennis")
        val result = FavouritesCodec.reorder(listOf(a, b, c), listOf(c.key, a.key))
        assertEquals(listOf(c, a, b), result)
    }

    // --- Disruptions ---

    private val hurstbridge = Route(id = 8, mode = TransportMode.TRAIN, name = "Hurstbridge", number = "")
    private val frankston = Route(id = 6, mode = TransportMode.TRAIN, name = "Frankston", number = "")

    private fun disruption(id: Long, title: String, type: String, routes: List<Route>, stops: List<String> = emptyList()) = Disruption(
        id = id, title = title, description = "", url = null, status = "Current", type = type,
        publishedOn = Instant.EPOCH, fromDate = null, toDate = null, routes = routes, stopNames = stops, mode = TransportMode.TRAIN,
    )

    private fun fav(id: Int, name: String, lines: List<FavouriteLine> = emptyList()) =
        FavouriteStop(stopId = id, routeType = 0, name = name, lines = lines)

    @Test
    fun majorMeansClosuresAndReplacementBuses() {
        assertTrue(ForYouDisruptions.isMajor(disruption(1, "Buses replace trains between Clifton Hill and Eltham", "Planned Works", listOf(hurstbridge))))
        assertTrue(ForYouDisruptions.isMajor(disruption(2, "Station closed", "Planned Closure", listOf(hurstbridge))))
        assertTrue(ForYouDisruptions.isMajor(disruption(3, "Timetable changes this weekend", "Service Information", listOf(hurstbridge))))
        assertFalse(ForYouDisruptions.isMajor(disruption(4, "Lift out of service", "Service Information", listOf(hurstbridge))))
        assertFalse(ForYouDisruptions.isMajor(disruption(5, "Minor delays", "Minor Delays", listOf(hurstbridge))))
    }

    @Test
    fun onlyShowsMajorDisruptionsOnFavouriteLines() {
        val board = DepartureBoard(departures = emptyList(), disruptions = emptyMap(), routes = listOf(hurstbridge), fetchedAt = Instant.EPOCH)
        val alphington = fav(1, "Alphington")
        val network = listOf(
            disruption(1, "Buses replace trains", "Planned Works", listOf(hurstbridge)),
            disruption(2, "Buses replace trains", "Planned Works", listOf(frankston)),
            disruption(3, "Minor delays", "Minor Delays", listOf(hurstbridge)),
            disruption(4, "Station closed", "Planned Closure", emptyList(), stops = listOf("Alphington Station")),
        )
        val major = ForYouDisruptions.select(network, listOf(alphington), mapOf(alphington.key to board), DisruptionsConfig())
        assertEquals(listOf(1L, 4L), major.map { it.id }.sorted())

        val withDelays = ForYouDisruptions.select(network, listOf(alphington), mapOf(alphington.key to board), DisruptionsConfig(level = DisruptionLevel.DELAYS))
        assertEquals(listOf(1L, 3L, 4L), withDelays.map { it.id }.sorted())

        val everywhere = ForYouDisruptions.select(network, listOf(alphington), mapOf(alphington.key to board), DisruptionsConfig(scope = DisruptionScope.NETWORK))
        assertEquals(listOf(1L, 2L, 4L), everywhere.map { it.id }.sorted())
    }

    @Test
    fun aFavouriteLimitedToALineOnlyCountsThatLine() {
        val flinders = fav(9, "Flinders Street", lines = listOf(FavouriteLine(routeId = 6, routeLabel = "Frankston")))
        val keys = ForYouDisruptions.favouriteRouteKeys(listOf(flinders), emptyMap())
        assertEquals(setOf(frankston.key), keys)
    }
}
