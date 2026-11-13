package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.update.GitHubAsset
import com.ssethhyy.journey.data.update.GitHubRelease
import com.ssethhyy.journey.data.update.GitHubReleasesApi
import com.ssethhyy.journey.data.update.UpdateLogic
import com.ssethhyy.journey.ui.components.Markdown
import com.ssethhyy.journey.ui.components.MdBlock
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private fun release(tag: String, pre: Boolean = false, draft: Boolean = false, apk: Boolean = true) = GitHubRelease(
        tagName = tag, name = "Journey $tag", body = "## Added\n- Things", draft = draft, prerelease = pre,
        assets = if (apk) listOf(GitHubAsset("journey-$tag.apk", "https://example.com/$tag.apk", 70_000_000)) else emptyList(),
    )

    @Test
    fun versionsCompareLikeSemver() {
        assertTrue(UpdateLogic.compare("v1.3.0", "1.2.0") > 0)
        assertTrue(UpdateLogic.compare("1.2.10", "1.2.9") > 0)
        assertTrue(UpdateLogic.compare("1.2.0", "1.2.0-beta.2") > 0)
        assertTrue(UpdateLogic.compare("1.2.0-beta.10", "1.2.0-beta.2") > 0)
        assertEquals(0, UpdateLogic.compare("v1.2.0", "1.2.0"))
        assertTrue(UpdateLogic.compare("1.2", "1.1.9") > 0)
    }

    @Test
    fun picksTheNewestFullReleaseUnlessPrereleasesAreWanted() {
        val releases = listOf(release("v1.2.0"), release("v1.3.0-beta.1", pre = true), release("v1.2.1"), release("v1.4.0", draft = true), release("v1.5.0", apk = false))
        assertEquals("1.2.1", UpdateLogic.newest(releases, "1.2.0", includePrereleases = false)?.version)
        assertEquals("1.3.0-beta.1", UpdateLogic.newest(releases, "1.2.0", includePrereleases = true)?.version)
        assertNull("already up to date", UpdateLogic.newest(releases, "1.2.1", includePrereleases = false))
    }

    @Test
    fun readsReleasesFromGitHub() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""[{"tag_name":"v1.3.0","name":"Journey 1.3.0","body":"# Journey 1.3.0","draft":false,"prerelease":false,"html_url":"https://github.com/x","published_at":"2026-11-13T00:00:00Z","assets":[{"name":"app-release.apk","browser_download_url":"https://github.com/x.apk","size":123,"content_type":"application/vnd.android.package-archive"}]}]"""))
        server.start()
        try {
            val api = GitHubReleasesApi(baseUrl = server.url("/").toString())
            val update = UpdateLogic.newest(api.releases(), "1.2.0", false)!!
            assertEquals("1.3.0", update.version)
            assertEquals("https://github.com/x.apk", update.apkUrl)
            assertTrue(server.takeRequest().path!!.startsWith("/repos/ssethhyy/Journey/releases"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun markdownBlocksAreParsed() {
        val blocks = Markdown.parse("# Journey 1.3.0\n\n> **Before you update:** uninstall\n> the old one.\n\n### Added\n- **Stop alerts.** Big\n  and clear.\n1. First\n\n---\nPlain *text*.")
        assertEquals(MdBlock.Heading(1, "Journey 1.3.0"), blocks[0])
        assertTrue(blocks[1] is MdBlock.Quote && (blocks[1] as MdBlock.Quote).text.contains("the old one"))
        assertEquals(MdBlock.Heading(3, "Added"), blocks[2])
        assertEquals(MdBlock.Bullet("**Stop alerts.** Big and clear.", "•"), blocks[3])
        assertEquals(MdBlock.Bullet("First", "1."), blocks[4])
        assertEquals(MdBlock.Rule, blocks[5])
        assertEquals(MdBlock.Paragraph("Plain *text*."), blocks[6])
    }
}
