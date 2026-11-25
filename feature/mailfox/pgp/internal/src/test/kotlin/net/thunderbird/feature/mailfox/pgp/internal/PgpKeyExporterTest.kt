package net.thunderbird.feature.mailfox.pgp.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore
import net.thunderbird.feature.mailfox.pgp.internal.storage.SecretKeyCipher
import org.pgpainless.PGPainless
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.key.protection.UnlockSecretKey
import org.pgpainless.util.Passphrase

class PgpKeyExporterTest {
    private val keyStore = PgpKeyStore(Files.createTempDirectory("pgp").toFile(), NoOpCipher())
    private val secretKey = PgpCrypto.generateKey("Bob", "bob@example.org")
    private val testSubject = PgpKeyExporter(keyStore, ioDispatcher = UnconfinedTestDispatcher())

    @Test
    fun `export should write passphrase-protected armored secret key`() = runTest {
        keyStore.saveSecretKey(ACCOUNT_UUID, secretKey)
        val output = ByteArrayOutputStream()

        testSubject.export(ACCOUNT_UUID, PASSPHRASE.toCharArray()) { output }

        val armored = output.toString(Charsets.US_ASCII.name())
        assertThat(armored).startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----")
        val exportedKey = PGPainless.readKeyRing().secretKeyRing(armored)!!
        assertThat(exportedKey.publicKey.keyID).isEqualTo(secretKey.publicKey.keyID)
        assertThat(PGPainless.inspectKeyRing(exportedKey).isFullyDecrypted).isFalse()
        assertThat(canUnlock(exportedKey, PASSPHRASE)).isTrue()
        assertThat(canUnlock(exportedKey, "wrong password")).isFalse()
    }

    @Test
    fun `export should not change the stored key`() = runTest {
        keyStore.saveSecretKey(ACCOUNT_UUID, secretKey)

        testSubject.export(ACCOUNT_UUID, PASSPHRASE.toCharArray()) { ByteArrayOutputStream() }

        assertThat(PGPainless.inspectKeyRing(keyStore.getSecretKey(ACCOUNT_UUID)!!).isFullyDecrypted).isTrue()
    }

    @Test
    fun `export should reject short passphrase`() = runTest {
        keyStore.saveSecretKey(ACCOUNT_UUID, secretKey)

        assertFailure {
            testSubject.export(ACCOUNT_UUID, "short".toCharArray()) { ByteArrayOutputStream() }
        }
    }

    private fun canUnlock(key: org.bouncycastle.openpgp.PGPSecretKeyRing, passphrase: String): Boolean {
        val protector = SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(passphrase))
        return runCatching { UnlockSecretKey.unlockSecretKey(key.secretKey, protector) }.isSuccess
    }

    private class NoOpCipher : SecretKeyCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(ciphertext: ByteArray) = ciphertext
    }

    private companion object {
        const val ACCOUNT_UUID = "0b7c3a4e-1111-2222-3333-444455556666"
        const val PASSPHRASE = "korrekt-pferd-batterie"
    }
}
