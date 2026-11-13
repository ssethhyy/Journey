/*
 * Feature Module: Updates
 * File: Updates.kt
 * Description: Finds a newer Journey release on GitHub. Releases are read from the public GitHub API (no account or
 * key needed), compared with the installed version, and the newest one with an APK attached is offered, including
 * pre-releases only if the user asked for them. The version logic is pure and unit tested.
 */

package com.ssethhyy.journey.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

/** A release newer than the installed app, with the APK to install. */
data class AvailableUpdate(
    val version: String,
    val title: String,
    /** The release notes, in Markdown, as written on GitHub. */
    val notes: String,
    val prerelease: Boolean,
    val publishedAt: Instant?,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val pageUrl: String,
)

object UpdateLogic {
    private data class Version(val numbers: List<Int>, val preRelease: String?)

    private fun parse(raw: String): Version {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        val core = clean.substringBefore('-').substringBefore('+')
        val pre = clean.substringAfter('-', "").substringBefore('+').takeIf { it.isNotBlank() }
        val numbers = core.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        return Version(numbers, pre)
    }

    /** Positive if [a] is newer than [b]. "1.2.0" > "1.2.0-beta.2" > "1.2.0-beta.1" > "1.1.9". */
    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.numbers.size, y.numbers.size)) {
            val d = x.numbers.getOrElse(i) { 0 }.compareTo(y.numbers.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return when {
            x.preRelease == y.preRelease -> 0
            x.preRelease == null -> 1
            y.preRelease == null -> -1
            else -> comparePre(x.preRelease, y.preRelease)
        }
    }

    private fun comparePre(a: String, b: String): Int {
        val xs = a.split('.')
        val ys = b.split('.')
        for (i in 0 until maxOf(xs.size, ys.size)) {
            val p = xs.getOrNull(i) ?: return -1
            val q = ys.getOrNull(i) ?: return 1
            val pn = p.toIntOrNull()
            val qn = q.toIntOrNull()
            val d = if (pn != null && qn != null) pn.compareTo(qn) else p.compareTo(q)
            if (d != 0) return d
        }
        return 0
    }

    private fun apkOf(release: GitHubRelease): GitHubAsset? =
        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    /** The newest release above [currentVersion] that has an APK, or null when Journey is up to date. */
    fun newest(releases: List<GitHubRelease>, currentVersion: String, includePrereleases: Boolean): AvailableUpdate? {
        val best = releases
            .filter { !it.draft && (includePrereleases || !it.prerelease) && apkOf(it) != null }
            .maxWithOrNull { a, b -> compare(a.tagName, b.tagName) }
            ?: return null
        if (compare(best.tagName, currentVersion) <= 0) return null
        val apk = apkOf(best)!!
        return AvailableUpdate(
            version = best.tagName.removePrefix("v"),
            title = best.name?.takeIf { it.isNotBlank() } ?: "Journey ${best.tagName.removePrefix("v")}",
            notes = best.body.orEmpty().trim(),
            prerelease = best.prerelease,
            publishedAt = best.publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            apkUrl = apk.downloadUrl,
            apkName = apk.name,
            apkSize = apk.size,
            pageUrl = best.htmlUrl,
        )
    }
}

class UpdateException(message: String) : Exception(message)

/** Reads Journey's releases from the public GitHub API. */
class GitHubReleasesApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val repository: String = REPOSITORY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun releases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/repos/$repository/releases?per_page=15")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Journey-Android")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateException("Couldn't reach GitHub. Check your connection.")
        }
        response.use {
            when {
                it.code == 403 || it.code == 429 -> throw UpdateException("GitHub is limiting update checks. Try again in a while.")
                !it.isSuccessful -> throw UpdateException("GitHub returned ${it.code}")
            }
            json.decodeFromString(ListSerializer(GitHubRelease.serializer()), it.body?.string().orEmpty())
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com"
        const val REPOSITORY = "ssethhyy/Journey"
    }
}
