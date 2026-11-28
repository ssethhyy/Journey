/*
 * Feature Module: Data / API
 * File: PtvHttpCache.kt
 * Description: A disk cache for the PTV answers that hardly ever change, so they aren't fetched again every launch.
 *
 * Stops, lines, their stops and shapes and directions change with timetable updates, not minute to minute, yet the
 * in-memory caches forget them whenever the app closes. This keeps them on disk (in the app's cache folder, which
 * Android may clear) for [PtvCachePolicy.maxAgeSeconds]. PTV's own replies say not to cache, so the cache headers are
 * rewritten for those paths only. Anything live (departures, stopping patterns, disruptions, search) is never kept.
 *
 * A request's URL is signed but stable (the same path with the same key always signs the same), so a cached answer is
 * found again; a different key gives different URLs, so answers are never shared between keys.
 */

package com.ssethhyy.journey.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Which PTV paths may be kept, and for how long. */
object PtvCachePolicy {
    private const val DAY = 24 * 60 * 60L

    /** Seconds to keep the answer for [path] (a PTV path, without the query), or null to never keep it. */
    fun maxAgeSeconds(path: String): Long? = when {
        // A line's details (with its shape), its directions and its stops: they change with timetable updates.
        path.startsWith("/v3/routes/") -> 7 * DAY
        path.startsWith("/v3/directions/route/") -> 7 * DAY
        path.startsWith("/v3/stops/route/") -> 7 * DAY
        // Stops near a point (the map's whole-network load asks the same thing every launch).
        path.startsWith("/v3/stops/location/") -> DAY
        // A stop's details and facilities.
        Regex("^/v3/stops/\\d+/route_type/\\d+$").matches(path) -> DAY
        else -> null
    }
}

object PtvHttpCache {
    private const val SIZE_BYTES = 20L * 1024 * 1024

    /** Rewrites PTV's "don't cache" headers on the paths [PtvCachePolicy] allows. */
    internal val rewrite = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val maxAge = PtvCachePolicy.maxAgeSeconds(chain.request().url.encodedPath)
        if (maxAge == null || !response.isSuccessful) {
            response
        } else {
            response.newBuilder()
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .header("Cache-Control", "public, max-age=$maxAge")
                .build()
        }
    }

    /** The PTV client with the disk cache. */
    fun client(context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "ptv_http"), SIZE_BYTES))
        .addNetworkInterceptor(rewrite)
        .build()
}
