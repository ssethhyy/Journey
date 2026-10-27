package com.ssethhyy.journey.island

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sameerasw.island.sdk.DecodedPayload
import com.sameerasw.island.sdk.HostInfo
import com.sameerasw.island.sdk.HostRequestType
import com.sameerasw.island.sdk.IIslandHost
import com.sameerasw.island.sdk.IIslandProvider
import com.sameerasw.island.sdk.IslandCodec
import com.sameerasw.island.sdk.IslandSchema
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.IslandSeverity
import com.ssethhyy.journey.ui.TestJourneyApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Plays the Essentials side of the island provider protocol against the real service:
 * approval handshake, attach, publish decoding with the SDK codec, host requests and detach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], application = TestJourneyApp::class)
class IslandHostSimulatorTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestJourneyApp>()
    private val hostUid = 10_999
    private val strangerUid = 10_998

    /** What a host received. */
    private class FakeHost : IIslandHost.Stub() {
        val publishes = CopyOnWriteArrayList<DecodedPayload>()
        val requests = CopyOnWriteArrayList<Triple<String, String, Bundle>>()
        override fun publish(schema: Int, json: String, assets: Bundle?) {
            IslandCodec.decode(schema, json, assets)?.let { publishes += it }
        }
        override fun request(itemKey: String, type: String, args: Bundle) {
            requests += Triple(itemKey, type, args)
        }
        override fun snapshot(itemKey: String, bitmap: Bitmap) = Unit
    }

    private lateinit var provider: IIslandProvider
    private val host = FakeHost()

    @Before
    fun setUp() {
        val pm = shadowOf(app.packageManager)
        pm.installPackage(
            PackageInfo().apply {
                packageName = IslandSchema.DEFAULT_HOST_PACKAGE
                @Suppress("DEPRECATION")
                signatures = arrayOf(Signature("3082deadbeef"))
            },
        )
        pm.setPackagesForUid(hostUid, IslandSchema.DEFAULT_HOST_PACKAGE)
        pm.setPackagesForUid(strangerUid, "com.example.stranger")
        runBlocking {
            val container = app.appContainer
            container.favouritesRepository.clear()
            container.favouritesRepository.add(Stop(2258, TransportMode.TRAM, "Bourke St/Spencer St #1", "Melbourne City", -37.8166, 144.9548))
            container.favouritesRepository.add(Stop(1071, TransportMode.TRAIN, "Flinders Street Station", "Melbourne City", -37.8183, 144.9671))
            container.settingsRepository.setIslandSeverity(IslandSeverity.ALL)
        }
        val service = Robolectric.buildService(JourneyIslandService::class.java).create().get()
        provider = IIslandProvider.Stub.asInterface(service.onBind(Intent(IslandSchema.SERVICE_ACTION)))
    }

    @After
    fun tearDown() {
        IslandConnection.stopPreview()
        ShadowBinder.reset()
    }

    private fun asHost(block: () -> Unit) {
        ShadowBinder.setCallingUid(hostUid)
        block()
    }

    private fun hostInfo() = HostInfo(hostPackage = IslandSchema.DEFAULT_HOST_PACKAGE, accent = 0xFFA9C7FF.toInt(), density = 3f, cornerPx = 96, cameraClearancePx = 120).toBundle()

    private fun waitFor(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for host condition")
    }

    @Test
    fun strangersAreRejectedAndNothingIsPublished() {
        ShadowBinder.setCallingUid(strangerUid)
        assertFalse(provider.grantHost())
        assertFalse(provider.isHostAuthorized)
        provider.attach(host, hostInfo())
        Thread.sleep(500)
        assertTrue(host.publishes.isEmpty())
    }

    @Test
    fun unapprovedEssentialsCannotAttach() {
        asHost {
            assertFalse(provider.isHostAuthorized)
            provider.attach(host, hostInfo())
        }
        Thread.sleep(500)
        assertTrue("attach before approval must be ignored", host.publishes.isEmpty())
    }

    @Test
    fun approvedHostReceivesDisruptionsWithColouredAlertIcon() {
        asHost {
            assertEquals(IslandSchema.VERSION, provider.schemaVersion)
            assertTrue("grantHost pairs Essentials", provider.grantHost())
            assertTrue(provider.isHostAuthorized)
            provider.attach(host, hostInfo())
        }
        waitFor { host.publishes.any { p -> p.payload.items.any { it.key == JourneyIsland.ITEM_DISRUPTIONS } } }
        val decoded = host.publishes.last { p -> p.payload.items.any { it.key == JourneyIsland.ITEM_DISRUPTIONS } }
        val item = decoded.payload.items.first { it.key == JourneyIsland.ITEM_DISRUPTIONS }
        // Fake server: tram 96 has major delays, Frankston line has planned works; both serve favourites.
        assertEquals("2", item.compact[1].text)
        assertEquals("alert_major", item.compact[0].iconId)
        val icon = decoded.assets["alert_major"]
        assertNotNull("icon bitmap sent with the payload", icon)
        assertTrue(icon!!.width <= 256 && icon.height <= 256)
        assertEquals(1, decoded.payload.schema)
        assertTrue(IslandConnection.state.value.attached)
    }

    @Test
    fun previewPublishesTrackingAndPeeksWithinHostLimits() {
        asHost {
            provider.grantHost()
            provider.attach(host, hostInfo())
        }
        IslandConnection.startPreview(30)
        waitFor { host.publishes.any { p -> p.payload.items.any { it.key == JourneyIsland.ITEM_TRACKING } } }
        val tracking = host.publishes.last { p -> p.payload.items.any { it.key == JourneyIsland.ITEM_TRACKING } }
        val item = tracking.payload.items.first { it.key == JourneyIsland.ITEM_TRACKING }
        assertEquals("Hurstbridge line", item.line?.start)
        assertTrue(item.compact[1].text!!.endsWith("min"))
        assertNotNull(tracking.assets[item.compact[0].iconId])

        // Line style peeks on the first status, with a duration the host accepts.
        waitFor { host.requests.any { it.second == HostRequestType.PEEK } }
        val peek = host.requests.first { it.second == HostRequestType.PEEK }
        assertEquals(JourneyIsland.ITEM_TRACKING, peek.first)
        assertTrue(peek.third.getLong(HostRequestType.ARG_DURATION_MS) in 1..8_000)

        IslandConnection.stopPreview()
        waitFor { host.publishes.last().payload.items.none { it.key == JourneyIsland.ITEM_TRACKING } }
    }

    @Test
    fun turningIslandOffClearsEverything() {
        asHost {
            provider.grantHost()
            provider.attach(host, hostInfo())
        }
        waitFor { host.publishes.any { it.payload.items.isNotEmpty() } }
        runBlocking { app.appContainer.settingsRepository.setIslandEnabled(false) }
        waitFor { host.publishes.last().payload.items.isEmpty() }
        runBlocking { app.appContainer.settingsRepository.setIslandEnabled(true) }
    }

    @Test
    fun revokeDetachesAndBlocksFurtherCalls() {
        asHost {
            provider.grantHost()
            provider.attach(host, hostInfo())
        }
        waitFor { IslandConnection.state.value.attached }
        asHost { provider.revokeHost() }
        waitFor { !IslandConnection.state.value.attached }
        asHost { assertFalse(provider.isHostAuthorized) }
    }
}
