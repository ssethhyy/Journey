/*
 * Feature Module: Weather
 * File: Weather.kt
 * Description: Current weather for the For You greeting, from a choice of providers.
 *
 * OpenWeatherMap is the default. Release builds carry a free key for it (WEATHER_API_KEY in .env); anyone who
 * builds the app themselves, or picks another provider that needs a key, pastes their own in the Greeting widget's
 * settings. Open-Meteo needs no key at all. Every provider is asked for metric values and mapped to one model.
 */

package com.ssethhyy.journey.data.weather

import com.ssethhyy.journey.data.location.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    HEAVY_RAIN("Heavy rain"),
    STORM("Thunderstorm"),
    SNOW("Snow"),
}

@Serializable
enum class WeatherProvider(
    val label: String,
    val needsKey: Boolean,
    /** Where to get a free key. */
    val keyUrl: String?,
    val note: String,
) {
    OPENWEATHERMAP(
        "OpenWeatherMap",
        needsKey = true,
        keyUrl = "https://home.openweathermap.org/users/sign_up",
        note = "The default. Release builds include a key.",
    ),
    WEATHERAPI(
        "WeatherAPI.com",
        needsKey = true,
        keyUrl = "https://www.weatherapi.com/signup.aspx",
        note = "Needs your own free key.",
    ),
    OPEN_METEO(
        "Open-Meteo",
        needsKey = false,
        keyUrl = null,
        note = "No key needed. Free for personal use.",
    ),
    ;

    companion object {
        val DEFAULT = OPENWEATHERMAP
    }
}

@Serializable
enum class TemperatureUnit(val label: String, val symbol: String) {
    CELSIUS("Celsius", "°C"),
    FAHRENHEIT("Fahrenheit", "°F"),
    ;

    fun convert(celsius: Double): Double = if (this == CELSIUS) celsius else celsius * 9 / 5 + 32

    /** "21°" */
    fun format(celsius: Double): String = "${convert(celsius).roundToInt()}°"
}

data class WeatherNow(
    val temperatureC: Double,
    val feelsLikeC: Double? = null,
    val highC: Double? = null,
    val lowC: Double? = null,
    val condition: WeatherCondition,
    /** The provider's own wording, e.g. "Light rain". */
    val description: String,
    val isDay: Boolean = true,
    val place: String? = null,
    val provider: WeatherProvider,
    val fetchedAtMs: Long = System.currentTimeMillis(),
)

sealed class WeatherException(message: String) : Exception(message) {
    class MissingKey(val provider: WeatherProvider) : WeatherException("${provider.label} needs an API key")
    class Unauthorized(val provider: WeatherProvider) : WeatherException("${provider.label} rejected the API key")
    class Network(message: String) : WeatherException(message)
}

/** Turns each provider's JSON into [WeatherNow]. Pure, so it's unit tested without a network. */
object WeatherParsers {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(provider: WeatherProvider, body: String, nowMs: Long = System.currentTimeMillis()): WeatherNow {
        val root = json.parseToJsonElement(body) as? JsonObject ?: throw WeatherException.Network("Unexpected weather response")
        return when (provider) {
            WeatherProvider.OPENWEATHERMAP -> openWeather(root, nowMs)
            WeatherProvider.WEATHERAPI -> weatherApi(root, nowMs)
            WeatherProvider.OPEN_METEO -> openMeteo(root, nowMs)
        }
    }

    private fun openWeather(root: JsonObject, nowMs: Long): WeatherNow {
        val main = root.obj("main") ?: throw WeatherException.Network("No weather in the response")
        val weather = (root["weather"] as? JsonArray)?.firstOrNull() as? JsonObject
        val id = weather?.int("id") ?: 800
        val icon = weather?.str("icon").orEmpty()
        val sys = root.obj("sys")
        val sunrise = sys?.long("sunrise")
        val sunset = sys?.long("sunset")
        val isDay = when {
            icon.endsWith("d") -> true
            icon.endsWith("n") -> false
            sunrise != null && sunset != null -> nowMs / 1000 in sunrise..sunset
            else -> true
        }
        val condition = openWeatherCondition(id)
        return WeatherNow(
            temperatureC = main.double("temp") ?: throw WeatherException.Network("No temperature in the response"),
            feelsLikeC = main.double("feels_like"),
            highC = main.double("temp_max"),
            lowC = main.double("temp_min"),
            condition = condition,
            description = weather?.str("description")?.capitalised() ?: condition.label,
            isDay = isDay,
            place = root.str("name")?.takeIf { it.isNotBlank() },
            provider = WeatherProvider.OPENWEATHERMAP,
            fetchedAtMs = nowMs,
        )
    }

    private fun weatherApi(root: JsonObject, nowMs: Long): WeatherNow {
        val current = root.obj("current") ?: throw WeatherException.Network("No weather in the response")
        val condition = current.obj("condition")
        val code = condition?.int("code") ?: 1000
        val text = condition?.str("text")?.trim()
        val day = ((root.obj("forecast")?.get("forecastday") as? JsonArray)?.firstOrNull() as? JsonObject)?.obj("day")
        val mapped = weatherApiCondition(code, text)
        return WeatherNow(
            temperatureC = current.double("temp_c") ?: throw WeatherException.Network("No temperature in the response"),
            feelsLikeC = current.double("feelslike_c"),
            highC = day?.double("maxtemp_c"),
            lowC = day?.double("mintemp_c"),
            condition = mapped,
            description = text?.takeIf { it.isNotBlank() } ?: mapped.label,
            isDay = (current.int("is_day") ?: 1) == 1,
            place = root.obj("location")?.str("name"),
            provider = WeatherProvider.WEATHERAPI,
            fetchedAtMs = nowMs,
        )
    }

    private fun openMeteo(root: JsonObject, nowMs: Long): WeatherNow {
        val current = root.obj("current") ?: throw WeatherException.Network("No weather in the response")
        val daily = root.obj("daily")
        val condition = openMeteoCondition(current.int("weather_code") ?: 0)
        return WeatherNow(
            temperatureC = current.double("temperature_2m") ?: throw WeatherException.Network("No temperature in the response"),
            feelsLikeC = current.double("apparent_temperature"),
            highC = (daily?.get("temperature_2m_max") as? JsonArray)?.firstOrNull()?.asDouble(),
            lowC = (daily?.get("temperature_2m_min") as? JsonArray)?.firstOrNull()?.asDouble(),
            condition = condition,
            description = condition.label,
            isDay = (current.int("is_day") ?: 1) == 1,
            provider = WeatherProvider.OPEN_METEO,
            fetchedAtMs = nowMs,
        )
    }

    /** OpenWeatherMap condition ids: https://openweathermap.org/weather-conditions */
    fun openWeatherCondition(id: Int): WeatherCondition = when (id) {
        in 200..299 -> WeatherCondition.STORM
        in 300..399 -> WeatherCondition.DRIZZLE
        502, 503, 504, 522, 531 -> WeatherCondition.HEAVY_RAIN
        511 -> WeatherCondition.SNOW
        in 500..599 -> WeatherCondition.RAIN
        in 600..699 -> WeatherCondition.SNOW
        in 700..799 -> WeatherCondition.FOG
        800 -> WeatherCondition.CLEAR
        801, 802 -> WeatherCondition.PARTLY_CLOUDY
        else -> WeatherCondition.CLOUDY
    }

    /** WeatherAPI.com condition codes: https://www.weatherapi.com/docs/weather_conditions.json */
    fun weatherApiCondition(code: Int, text: String? = null): WeatherCondition = when (code) {
        1000 -> WeatherCondition.CLEAR
        1003 -> WeatherCondition.PARTLY_CLOUDY
        1006, 1009 -> WeatherCondition.CLOUDY
        1030, 1135, 1147 -> WeatherCondition.FOG
        1072, 1150, 1153, 1168, 1171 -> WeatherCondition.DRIZZLE
        1063, 1180, 1183, 1186, 1189, 1198, 1201, 1240 -> WeatherCondition.RAIN
        1192, 1195, 1243, 1246 -> WeatherCondition.HEAVY_RAIN
        1087, 1273, 1276, 1279, 1282 -> WeatherCondition.STORM
        1066, 1069, 1114, 1117, in 1204..1237, in 1249..1264 -> WeatherCondition.SNOW
        else -> text.orEmpty().lowercase().let { t ->
            when {
                "thunder" in t -> WeatherCondition.STORM
                "snow" in t || "sleet" in t -> WeatherCondition.SNOW
                "heavy" in t && "rain" in t -> WeatherCondition.HEAVY_RAIN
                "drizzle" in t -> WeatherCondition.DRIZZLE
                "rain" in t || "shower" in t -> WeatherCondition.RAIN
                "fog" in t || "mist" in t -> WeatherCondition.FOG
                "cloud" in t || "overcast" in t -> WeatherCondition.CLOUDY
                else -> WeatherCondition.CLEAR
            }
        }
    }

    /** WMO weather codes as used by Open-Meteo. */
    fun openMeteoCondition(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..57 -> WeatherCondition.DRIZZLE
        65, 82 -> WeatherCondition.HEAVY_RAIN
        in 61..67, 80, 81 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        in 95..99 -> WeatherCondition.STORM
        else -> WeatherCondition.CLOUDY
    }

    private fun JsonObject.obj(name: String) = this[name] as? JsonObject
    private fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.double(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull
    private fun JsonElement.asDouble() = (this as? JsonPrimitive)?.doubleOrNull
    private fun String.capitalised() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

class WeatherApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrls: Map<WeatherProvider, String> = DEFAULT_BASE_URLS,
) {
    suspend fun current(provider: WeatherProvider, key: String?, at: LatLng): WeatherNow {
        val cleanKey = key?.trim().orEmpty()
        if (provider.needsKey && cleanKey.isEmpty()) throw WeatherException.MissingKey(provider)
        val base = baseUrls.getValue(provider).trimEnd('/')
        val lat = "%.4f".format(Locale.US, at.latitude)
        val lon = "%.4f".format(Locale.US, at.longitude)
        val url = when (provider) {
            WeatherProvider.OPENWEATHERMAP -> "$base/data/2.5/weather".toHttpUrl().newBuilder()
                .addQueryParameter("lat", lat).addQueryParameter("lon", lon)
                .addQueryParameter("units", "metric").addQueryParameter("appid", cleanKey)
            WeatherProvider.WEATHERAPI -> "$base/v1/forecast.json".toHttpUrl().newBuilder()
                .addQueryParameter("key", cleanKey).addQueryParameter("q", "$lat,$lon")
                .addQueryParameter("days", "1").addQueryParameter("aqi", "no").addQueryParameter("alerts", "no")
            WeatherProvider.OPEN_METEO -> "$base/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", lat).addQueryParameter("longitude", lon)
                .addQueryParameter("current", "temperature_2m,apparent_temperature,weather_code,is_day")
                .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min")
                .addQueryParameter("timezone", "auto").addQueryParameter("forecast_days", "1")
        }.build()
        val response = try {
            client.newCall(Request.Builder().url(url).get().build()).await()
        } catch (e: IOException) {
            throw WeatherException.Network(e.message ?: "Network error")
        }
        response.use {
            // WeatherAPI.com reports a bad key as 400 or 403 with an error code in the body.
            if (it.code == 401 || it.code == 403 || (provider == WeatherProvider.WEATHERAPI && it.code == 400)) {
                throw WeatherException.Unauthorized(provider)
            }
            if (!it.isSuccessful) throw WeatherException.Network("${provider.label}: HTTP ${it.code}")
            return WeatherParsers.parse(provider, it.body?.string().orEmpty())
        }
    }

    companion object {
        val DEFAULT_BASE_URLS = mapOf(
            WeatherProvider.OPENWEATHERMAP to "https://api.openweathermap.org",
            WeatherProvider.WEATHERAPI to "https://api.weatherapi.com",
            WeatherProvider.OPEN_METEO to "https://api.open-meteo.com",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Current weather where the user is (their last known location, or Melbourne), cached for 15 minutes per provider
 * and area so opening For You again costs nothing.
 */
class WeatherRepository(
    private val api: WeatherApi,
    private val location: suspend () -> LatLng?,
    /** The key built into this build for the default provider (empty when built without one). */
    private val builtInKey: String,
) {
    private var cached: Pair<String, WeatherNow>? = null

    fun keyFor(provider: WeatherProvider, userKeys: Map<WeatherProvider, String>): String? {
        val own = userKeys[provider]?.trim().orEmpty()
        return when {
            own.isNotEmpty() -> own
            provider == WeatherProvider.DEFAULT -> builtInKey.trim().ifEmpty { null }
            else -> null
        }
    }

    /** Whether [provider] can be used as things stand: it needs no key, or one is available. */
    fun isReady(provider: WeatherProvider, userKeys: Map<WeatherProvider, String>): Boolean =
        !provider.needsKey || keyFor(provider, userKeys) != null

    suspend fun current(provider: WeatherProvider, userKeys: Map<WeatherProvider, String>, force: Boolean = false): WeatherNow {
        val at = location() ?: MELBOURNE
        val cacheKey = "${provider.name}|${"%.2f".format(Locale.US, at.latitude)}|${"%.2f".format(Locale.US, at.longitude)}|${keyFor(provider, userKeys).hashCode()}"
        cached?.let { (key, weather) ->
            if (!force && key == cacheKey && System.currentTimeMillis() - weather.fetchedAtMs < TTL_MS) return weather
        }
        return api.current(provider, keyFor(provider, userKeys), at).also { cached = cacheKey to it }
    }

    companion object {
        val MELBOURNE = LatLng(-37.8136, 144.9631)
        private const val TTL_MS = 15 * 60_000L
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}
