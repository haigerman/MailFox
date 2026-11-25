package net.thunderbird.feature.mailfox.pgp.internal.keyserver

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpKeyServerClientTest {
    private val server = MockWebServer()

    @BeforeTest
    fun setUp() {
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun createTestSubject() = OkHttpKeyServerClient(
        keysOpenPgpOrgBaseUrl = server.url("").toString().trimEnd('/'),
        wkdUrls = { emptyList() },
    )

    @Test
    fun `fetchKeys should return key from keys_openpgp_org`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("KEYDATA"))

        val result = createTestSubject().fetchKeys("bob@example.org")

        assertThat(result).hasSize(1)
        assertThat(String(result[0])).isEqualTo("KEYDATA")
        assertThat(server.takeRequest().path).isEqualTo("/vks/v1/by-email/bob%40example.org")
    }

    @Test
    fun `fetchKeys should return empty list when there is no key`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = createTestSubject().fetchKeys("bob@example.org")

        assertThat(result).isEmpty()
    }

    @Test
    fun `fetchKeys should throw on server error`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertFailsWith<IOException> { createTestSubject().fetchKeys("bob@example.org") }
    }

    @Test
    fun `publish should upload key and request verification`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"key_fpr":"ABC","status":{"bob@example.org":"unpublished"},"token":"TOKEN"}"""),
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"key_fpr":"ABC","status":{"bob@example.org":"pending"},"token":"TOKEN"}"""),
        )

        val result = createTestSubject().publish("ARMORED", "bob@example.org")

        assertThat(result).isEqualTo(PublishStatus.VERIFICATION_PENDING)
        assertThat(server.takeRequest().path).isEqualTo("/vks/v1/upload")
        val verifyRequest = server.takeRequest()
        assertThat(verifyRequest.path).isEqualTo("/vks/v1/request-verify")
        assertThat(verifyRequest.body.readUtf8()).startsWith("""{"token":"TOKEN","addresses":["bob@example.org"]""")
    }

    @Test
    fun `publish should not request verification when address is already published`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"key_fpr":"ABC","status":{"bob@example.org":"published"},"token":"TOKEN"}"""),
        )

        val result = createTestSubject().publish("ARMORED", "bob@example.org")

        assertThat(result).isEqualTo(PublishStatus.PUBLISHED)
        assertThat(server.requestCount).isEqualTo(1)
    }
}
