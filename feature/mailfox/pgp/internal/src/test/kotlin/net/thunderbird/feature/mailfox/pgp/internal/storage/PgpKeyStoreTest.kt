package net.thunderbird.feature.mailfox.pgp.internal.storage

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.nio.file.Files
import kotlin.test.Test
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto

class PgpKeyStoreTest {
    private val directory = Files.createTempDirectory("pgp").toFile()
    private val testSubject = PgpKeyStore(directory, cipher = XorCipher())

    @Test
    fun `secret key should be stored encrypted and loaded again`() {
        val key = PgpCrypto.generateKey("Bob", "bob@example.org")

        testSubject.saveSecretKey(ACCOUNT_UUID, key)

        assertThat(testSubject.hasSecretKey(ACCOUNT_UUID)).isTrue()
        assertThat(testSubject.getSecretKey(ACCOUNT_UUID)?.publicKey?.keyID).isEqualTo(key.publicKey.keyID)
        val storedBytes = directory.walk().first { it.isFile }.readBytes()
        assertThat(storedBytes.contentEquals(key.encoded)).isFalse()
    }

    @Test
    fun `replacing a key should keep the previous key for decryption`() {
        val oldKey = PgpCrypto.generateKey("Bob", "bob@example.org")
        val newKey = PgpCrypto.generateKey("Bob", "bob@example.org")

        testSubject.saveSecretKey(ACCOUNT_UUID, oldKey)
        testSubject.saveSecretKey(ACCOUNT_UUID, newKey)

        assertThat(testSubject.getSecretKey(ACCOUNT_UUID)?.publicKey?.keyID).isEqualTo(newKey.publicKey.keyID)
        assertThat(testSubject.getAllSecretKeys().map { it.publicKey.keyID }.toSet())
            .isEqualTo(setOf(oldKey.publicKey.keyID, newKey.publicKey.keyID))
    }

    @Test
    fun `saving the same key again should not create a copy`() {
        val key = PgpCrypto.generateKey("Bob", "bob@example.org")

        testSubject.saveSecretKey(ACCOUNT_UUID, key)
        testSubject.saveSecretKey(ACCOUNT_UUID, key)

        assertThat(testSubject.getAllSecretKeys()).hasSize(1)
    }

    @Test
    fun `getSecretKey should return null for unknown account`() {
        assertThat(testSubject.getSecretKey(ACCOUNT_UUID)).isNull()
    }

    @Test
    fun `peer certificates should be stored`() {
        val certificate = PgpCrypto.extractCertificate(PgpCrypto.generateKey("Alice", "alice@example.org"))

        testSubject.savePeerCertificate(certificate)
        testSubject.savePeerCertificate(certificate)

        assertThat(testSubject.getPeerCertificates()).hasSize(1)
    }

    private class XorCipher : SecretKeyCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext.map { (it.toInt() xor KEY).toByte() }.toByteArray()
        override fun decrypt(ciphertext: ByteArray) = encrypt(ciphertext)
    }

    private companion object {
        const val ACCOUNT_UUID = "0b7c3a4e-1111-2222-3333-444455556666"
        const val KEY = 0x5A
    }
}
