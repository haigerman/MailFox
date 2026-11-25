package net.thunderbird.feature.mailfox.pgp.internal.keyserver

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

class KeyServerUrlsTest {

    @Test
    fun `wkdHash should match the example of the WKD specification`() {
        // draft-koch-openpgp-webkey-service: "Joe.Doe@Example.ORG" -> "iy9q119eutrkn8s1mk4r39qejnbu3n5q"
        val result = KeyServerUrls.wkdHash("Joe.Doe")

        assertThat(result).isEqualTo("iy9q119eutrkn8s1mk4r39qejnbu3n5q")
    }

    @Test
    fun `wkd should return advanced and direct URL`() {
        val result = KeyServerUrls.wkd("Joe.Doe@Example.ORG")

        val hash = "iy9q119eutrkn8s1mk4r39qejnbu3n5q"
        assertThat(result).containsExactly(
            "https://openpgpkey.example.org/.well-known/openpgpkey/example.org/hu/$hash?l=Joe.Doe",
            "https://example.org/.well-known/openpgpkey/hu/$hash?l=Joe.Doe",
        )
    }

    @Test
    fun `wkd should return no URLs for invalid address`() {
        assertThat(KeyServerUrls.wkd("no-at-sign")).isEmpty()
    }

    @Test
    fun `keysOpenPgpOrgByEmail should encode the address`() {
        val result = KeyServerUrls.keysOpenPgpOrgByEmail("a+b@example.org")

        assertThat(result).isEqualTo("https://keys.openpgp.org/vks/v1/by-email/a%2Bb%40example.org")
    }
}
