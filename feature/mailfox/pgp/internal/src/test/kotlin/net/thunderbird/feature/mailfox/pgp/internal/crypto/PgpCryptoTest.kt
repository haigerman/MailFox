package net.thunderbird.feature.mailfox.pgp.internal.crypto

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import org.pgpainless.PGPainless

class PgpCryptoTest {
    private val alice = PgpCrypto.generateKey("Alice", "alice@example.org")
    private val bob = PgpCrypto.generateKey("Bob", "bob@example.org")
    private val aliceCertificate = PgpCrypto.extractCertificate(alice)
    private val bobCertificate = PgpCrypto.extractCertificate(bob)

    @Test
    fun `message encrypted and signed by alice should be decrypted and verified by bob`() {
        val ciphertext = encrypt(plaintext = MIME_TEXT, recipients = listOf(bobCertificate))
        val output = ByteArrayOutputStream()

        val testSubject = PgpCrypto.decrypt(
            ciphertext = ciphertext.inputStream(),
            output = output,
            decryptionKeys = listOf(bob),
            verificationCertificates = listOf(aliceCertificate),
        )

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(MIME_TEXT)
        assertThat(testSubject.isEncrypted).isTrue()
        assertThat(testSubject.signature).isInstanceOf<PgpSignatureStatus.Valid>()
        assertThat((testSubject.signature as PgpSignatureStatus.Valid).userIds).contains("Alice <alice@example.org>")
    }

    @Test
    fun `ciphertext should be ASCII-armored`() {
        val ciphertext = encrypt(plaintext = MIME_TEXT, recipients = listOf(bobCertificate))

        assertThat(ciphertext.toString(Charsets.US_ASCII).startsWith("-----BEGIN PGP MESSAGE-----")).isTrue()
    }

    @Test
    fun `hidden recipient should be able to decrypt`() {
        val ciphertext =
            encrypt(plaintext = MIME_TEXT, recipients = emptyList(), hiddenRecipients = listOf(bobCertificate))
        val output = ByteArrayOutputStream()

        PgpCrypto.decrypt(ciphertext.inputStream(), output, listOf(bob), emptyList())

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(MIME_TEXT)
    }

    @Test
    fun `signature without known certificate should be reported as key missing`() {
        val ciphertext = encrypt(plaintext = MIME_TEXT, recipients = listOf(bobCertificate))

        val testSubject = PgpCrypto.decrypt(ciphertext.inputStream(), ByteArrayOutputStream(), listOf(bob), emptyList())

        assertThat(testSubject.signature).isInstanceOf<PgpSignatureStatus.KeyMissing>()
    }

    @Test
    fun `detached signature should be verified`() {
        val data = MIME_TEXT.toByteArray()
        val signature = ByteArrayOutputStream().also { output ->
            val options = org.pgpainless.encryption_signing.SigningOptions.get().addDetachedSignature(
                org.pgpainless.key.protection.SecretKeyRingProtector.unprotectedKeys(),
                alice,
                org.pgpainless.algorithm.DocumentSignatureType.BINARY_DOCUMENT,
            )
            val stream = PGPainless.encryptAndOrSign().onOutputStream(ByteArrayOutputStream())
                .withOptions(org.pgpainless.encryption_signing.ProducerOptions.sign(options))
            stream.use { it.write(data) }
            stream.result.detachedSignatures.values.flatten().forEach { output.write(it.encoded) }
        }.toByteArray()

        val testSubject = PgpCrypto.verifyDetached(data.inputStream(), signature, listOf(aliceCertificate))

        assertThat(testSubject).isInstanceOf<PgpSignatureStatus.Valid>()
    }

    @Test
    fun `isUsableFor should match the email address of the user ID`() {
        assertThat(PgpCrypto.isUsableFor(bobCertificate, "BOB@example.org")).isTrue()
        assertThat(PgpCrypto.isUsableFor(bobCertificate, "alice@example.org")).isFalse()
    }

    @Test
    fun `extractEmailAddress should handle user IDs with and without name`() {
        assertThat(PgpCrypto.extractEmailAddress("Bob <bob@example.org>")).isEqualTo("bob@example.org")
        assertThat(PgpCrypto.extractEmailAddress("<bob@example.org>")).isEqualTo("bob@example.org")
        assertThat(PgpCrypto.extractEmailAddress("bob@example.org")).isEqualTo("bob@example.org")
    }

    private fun encrypt(
        plaintext: String,
        recipients: List<org.bouncycastle.openpgp.PGPPublicKeyRing>,
        hiddenRecipients: List<org.bouncycastle.openpgp.PGPPublicKeyRing> = emptyList(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        PgpCrypto.encryptAndSign(plaintext.byteInputStream(), output, recipients, hiddenRecipients, signingKey = alice)
        return output.toByteArray()
    }

    private companion object {
        const val MIME_TEXT = "Content-Type: text/plain; charset=utf-8\r\n\r\nHallo Bob, grüß dich!\r\n"
    }
}
