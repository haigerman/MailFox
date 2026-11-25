package net.thunderbird.feature.mailfox.pgp.internal.keyserver

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.IOException
import java.util.Date
import kotlin.test.Test
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto

class RecipientKeyFinderTest {

    @Test
    fun `selectNewest should pick the most recently created usable key`() {
        val older = PgpCrypto.extractCertificate(PgpCrypto.generateKey("Bob", "bob@example.org"))
        Thread.sleep(SLEEP_FOR_NEW_CREATION_TIME)
        val newer = PgpCrypto.extractCertificate(PgpCrypto.generateKey("Bob", "bob@example.org"))
        val other = PgpCrypto.extractCertificate(PgpCrypto.generateKey("Eve", "eve@example.org"))

        val result = RecipientKeyFinder.selectNewest(listOf(older, other, newer), "bob@example.org", Date())

        assertThat(result?.let { PgpCrypto.fingerprint(it) }).isEqualTo(PgpCrypto.fingerprint(newer))
    }

    @Test
    fun `selectNewest should ignore keys for other addresses`() {
        val other = PgpCrypto.extractCertificate(PgpCrypto.generateKey("Eve", "eve@example.org"))

        assertThat(RecipientKeyFinder.selectNewest(listOf(other), "bob@example.org", Date())).isNull()
    }

    @Test
    fun `find should report failure when key server is not reachable`() {
        val testSubject = RecipientKeyFinder(
            keyServerClient = object : KeyServerClient {
                override fun fetchKeys(emailAddress: String): List<ByteArray> = throw IOException("offline")
                override fun publish(armoredCertificate: String, emailAddress: String) = error("unused")
            },
        )

        assertThat(testSubject.find("bob@example.org")).isInstanceOf<RecipientKeySearchResult.Failed>()
    }

    @Test
    fun `find should parse armored key from key server`() {
        val certificate = PgpCrypto.extractCertificate(PgpCrypto.generateKey("Bob", "bob@example.org"))
        val testSubject = RecipientKeyFinder(
            keyServerClient = object : KeyServerClient {
                override fun fetchKeys(emailAddress: String) = listOf(PgpCrypto.armor(certificate).toByteArray())
                override fun publish(armoredCertificate: String, emailAddress: String) = error("unused")
            },
        )

        val result = testSubject.find("bob@example.org")

        assertThat(result).isInstanceOf<RecipientKeySearchResult.Found>()
    }

    private companion object {
        // OpenPGP creation times have a resolution of one second
        const val SLEEP_FOR_NEW_CREATION_TIME = 1100L
    }
}
