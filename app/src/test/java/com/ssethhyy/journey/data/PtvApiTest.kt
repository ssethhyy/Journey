package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.api.PtvApi
import com.ssethhyy.journey.data.api.PtvCredentials
import com.ssethhyy.journey.data.api.PtvException
import com.ssethhyy.journey.data.api.PtvSigner
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PtvApiTest {
    private lateinit var server: MockWebServer
    private val credentials = PtvCredentials("3000000", "9c132d31-6a30-4cac-8d8b-8a1970834799")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(creds: PtvCredentials? = credentials) =
        PtvApi(credentialsProvider = { creds }, baseUrl = server.url("/").toString())

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("ptv/$name")!!.readText()

    @Test
    fun hmacMatchesKnownVector() {
        // RFC 2202 style reference: HMAC-SHA1("key", "The quick brown fox jumps over the lazy dog")
        assertEquals(
            "DE7C9B85B8B78AA6BC8A7A36F70A90701C9DB4D9",
            PtvSigner.hmacSha1Hex("key", "The quick brown fox jumps over the lazy dog"),
        )
    }

    @Test
    fun signedPathAppendsDevIdThenSignatureOfEverythingBeforeIt() {
        val signed = PtvSigner.signedPath("/v3/route_types", credentials)
        val expectedMessage = "/v3/route_types?devid=3000000"
        assertEquals("$expectedMessage&signature=${PtvSigner.hmacSha1Hex(credentials.apiKey, expectedMessage)}", signed)
    }

    @Test
    fun signedPathUsesAmpersandWhenQueryPresent() {
        val signed = PtvSigner.signedPath("/v3/search/abc?route_types=0", credentials)
        assertTrue(signed.startsWith("/v3/search/abc?route_types=0&devid=3000000&signature="))
    }

    @Test
    fun departuresRequestIsSignedAndParsed() = runTest {
        server.enqueue(MockResponse().setBody(fixture("departures.json")))

        val response = api().departures(routeType = 0, stopId = 1071, maxResults = 3)

        val request = server.takeRequest()
        val path = request.path!!
        assertTrue(path, path.startsWith("/v3/departures/route_type/0/stop/1071?"))
        assertTrue(path, "max_results=3" in path)
        assertTrue(path, "expand=All" in path)
        assertTrue(path, "devid=3000000" in path)
        val (unsigned, signature) = path.split("&signature=")
        assertEquals(PtvSigner.hmacSha1Hex(credentials.apiKey, unsigned), signature)

        assertEquals(3, response.departures.size)
        assertEquals("Frankston", response.routes["6"]?.routeName)
        assertEquals(4, response.runs["955123"]?.expressStopCount)
    }

    @Test
    fun searchTermIsPathEncoded() = runTest {
        server.enqueue(MockResponse().setBody("""{"stops":[],"routes":[]}"""))
        api().search("Flinders Street", listOf(0, 1))
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/v3/search/Flinders%20Street?"))
        assertTrue(path, "route_types=0&route_types=1" in path)
    }

    @Test
    fun forbiddenMapsToInvalidCredentials() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Forbidden"}"""))
        try {
            api().disruptions(listOf(0))
            fail("Expected exception")
        } catch (e: PtvException.InvalidCredentials) {
            // expected
        }
    }

    @Test
    fun serverErrorIncludesMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"Invalid stop"}"""))
        try {
            api().stopDetails(1, 0)
            fail("Expected exception")
        } catch (e: PtvException.Http) {
            assertEquals(400, e.code)
            assertTrue(e.message!!.contains("Invalid stop"))
        }
    }

    @Test
    fun missingCredentialsFailsWithoutNetworkCall() = runTest {
        try {
            api(creds = PtvCredentials("", "")).disruptions(listOf(0))
            fail("Expected exception")
        } catch (e: PtvException.MissingCredentials) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun malformedBodyMapsToParseError() = runTest {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        try {
            api().stopsNearby(-37.8, 144.9, listOf(0), 10, 500)
            fail("Expected exception")
        } catch (e: PtvException.Parse) {
            // expected
        }
    }
}
