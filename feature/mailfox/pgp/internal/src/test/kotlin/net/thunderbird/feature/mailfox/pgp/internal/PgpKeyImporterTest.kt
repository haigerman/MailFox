package net.thunderbird.feature.mailfox.pgp.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore
import net.thunderbird.feature.mailfox.pgp.internal.storage.SecretKeyCipher

class PgpKeyImporterTest {
    private val keyStore = PgpKeyStore(Files.createTempDirectory("pgp").toFile(), NoOpCipher())
    private val dispatcher = UnconfinedTestDispatcher()
    private val testSubject = PgpKeyImporter(keyStore, ioDispatcher = dispatcher)
    private val bobKey = PgpCrypto.generateKey("Bob", BOB)

    @Test
    fun `import should store unprotected armored key`() = runTest {
        val keyData = PgpCrypto.armorSecretKey(bobKey).toByteArray()

        val result = testSubject.import(ACCOUNT_UUID, BOB, keyData, passphrase = null)

        assertThat(result).isInstanceOf<PgpImportResult.Success>()
        assertThat(keyStore.getSecretKey(ACCOUNT_UUID)?.publicKey?.keyID).isEqualTo(bobKey.publicKey.keyID)
    }

    @Test
    fun `import should accept binary key`() = runTest {
        val result = testSubject.import(ACCOUNT_UUID, BOB, bobKey.encoded, passphrase = null)

        assertThat(result).isInstanceOf<PgpImportResult.Success>()
    }

    @Test
    fun `protected key should require passphrase`() = runTest {
        val keyData = protectedKeyData()

        val result = testSubject.import(ACCOUNT_UUID, BOB, keyData, passphrase = null)

        assertThat(result).isEqualTo(PgpImportResult.PassphraseRequired)
        assertThat(keyStore.getSecretKey(ACCOUNT_UUID)).isNull()
    }

    @Test
    fun `protected key with wrong passphrase should be rejected`() = runTest {
        val result = testSubject.import(ACCOUNT_UUID, BOB, protectedKeyData(), "falsches-passwort".toCharArray())

        assertThat(result).isEqualTo(PgpImportResult.WrongPassphrase)
        assertThat(keyStore.getSecretKey(ACCOUNT_UUID)).isNull()
    }

    @Test
    fun `protected key with correct passphrase should be stored without passphrase`() = runTest {
        val result = testSubject.import(ACCOUNT_UUID, BOB, protectedKeyData(), PASSPHRASE.toCharArray())

        assertThat(result).isInstanceOf<PgpImportResult.Success>()
        val storedKey = keyStore.getSecretKey(ACCOUNT_UUID)!!
        assertThat(PgpCrypto.isPassphraseProtected(storedKey)).isFalse()
        assertThat(storedKey.publicKey.keyID).isEqualTo(bobKey.publicKey.keyID)
    }

    @Test
    fun `key exported by MailFox should be importable`() = runTest {
        val sourceStore = PgpKeyStore(Files.createTempDirectory("pgp").toFile(), NoOpCipher())
        sourceStore.saveSecretKey(ACCOUNT_UUID, bobKey)
        val exported = ByteArrayOutputStream()
        PgpKeyExporter(sourceStore, dispatcher).export(ACCOUNT_UUID, PASSPHRASE.toCharArray()) { exported }

        val result = testSubject.import(ACCOUNT_UUID, BOB, exported.toByteArray(), PASSPHRASE.toCharArray())

        assertThat(result).isInstanceOf<PgpImportResult.Success>()
    }

    @Test
    fun `key for another address should be rejected`() = runTest {
        val result = testSubject.import(ACCOUNT_UUID, "alice@example.org", bobKey.encoded, passphrase = null)

        assertThat(result).isEqualTo(PgpImportResult.WrongEmailAddress)
    }

    @Test
    fun `public key should be rejected`() = runTest {
        val publicKey = PgpCrypto.armor(PgpCrypto.extractCertificate(bobKey)).toByteArray()

        val result = testSubject.import(ACCOUNT_UUID, BOB, publicKey, passphrase = null)

        assertThat(result).isEqualTo(PgpImportResult.NoSecretKey)
    }

    @Test
    fun `garbage should be rejected`() = runTest {
        val result = testSubject.import(ACCOUNT_UUID, BOB, "kein Schlüssel".toByteArray(), passphrase = null)

        assertThat(result).isEqualTo(PgpImportResult.NoSecretKey)
    }

    private fun protectedKeyData(): ByteArray {
        val protectedKey = PgpCrypto.protectWithPassphrase(bobKey, PASSPHRASE.toCharArray())
        return PgpCrypto.armorSecretKey(protectedKey).toByteArray()
    }

    private class NoOpCipher : SecretKeyCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(ciphertext: ByteArray) = ciphertext
    }

    private companion object {
        const val ACCOUNT_UUID = "0b7c3a4e-1111-2222-3333-444455556666"
        const val BOB = "bob@example.org"
        const val PASSPHRASE = "korrekt-pferd-batterie"
    }
}
