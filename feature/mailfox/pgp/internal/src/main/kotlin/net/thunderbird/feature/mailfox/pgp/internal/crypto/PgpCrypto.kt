package net.thunderbird.feature.mailfox.pgp.internal.crypto

import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.DocumentSignatureType
import org.pgpainless.algorithm.EncryptionPurpose
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.encryption_signing.SigningOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase

/**
 * MailFox: OpenPGP operations based on PGPainless.
 *
 * Secret keys are not protected by a passphrase. They are encrypted at rest by [PgpKeyStore][
 * net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore] with a key from the Android Keystore.
 */
@Suppress("TooManyFunctions")
internal object PgpCrypto {
    private const val BUFFER_SIZE = 8192

    fun generateKey(name: String?, emailAddress: String): PGPSecretKeyRing {
        val userId = if (name.isNullOrBlank()) "<$emailAddress>" else "${name.trim()} <$emailAddress>"
        return PGPainless.generateKeyRing().modernKeyRing(userId)
    }

    fun extractCertificate(secretKeyRing: PGPSecretKeyRing): PGPPublicKeyRing {
        return PGPainless.extractCertificate(secretKeyRing)
    }

    fun armor(certificate: PGPPublicKeyRing): String = PGPainless.asciiArmor(certificate)

    fun readCertificate(data: ByteArray): PGPPublicKeyRing? {
        return runCatching { PGPainless.readKeyRing().publicKeyRing(data) }.getOrNull()
    }

    /**
     * Reads all certificates contained in [data] (binary or ASCII-armored).
     */
    fun readCertificates(data: ByteArray): List<PGPPublicKeyRing> {
        return runCatching { PGPainless.readKeyRing().publicKeyRingCollection(data).toList() }.getOrDefault(emptyList())
    }

    /**
     * Returns a copy of [secretKeyRing] that is protected with [passphrase] (for export).
     */
    fun protectWithPassphrase(secretKeyRing: PGPSecretKeyRing, passphrase: CharArray): PGPSecretKeyRing {
        val newPassphrase = Passphrase(passphrase.copyOf())
        try {
            return PGPainless.modifyKeyRing(secretKeyRing)
                .changePassphraseFromOldPassphrase(Passphrase.emptyPassphrase())
                .withSecureDefaultSettings()
                .toNewPassphrase(newPassphrase)
                .done()
        } finally {
            newPassphrase.clear()
        }
    }

    fun armorSecretKey(secretKeyRing: PGPSecretKeyRing): String = PGPainless.asciiArmor(secretKeyRing)

    fun readSecretKeyRing(data: ByteArray): PGPSecretKeyRing? {
        return runCatching { PGPainless.readKeyRing().secretKeyRing(data) }.getOrNull()
    }

    fun encryptAndSign(
        plaintext: InputStream,
        output: OutputStream,
        recipients: List<PGPPublicKeyRing>,
        hiddenRecipients: List<PGPPublicKeyRing>,
        signingKey: PGPSecretKeyRing?,
    ) {
        require(recipients.isNotEmpty() || hiddenRecipients.isNotEmpty()) { "No recipients" }

        val encryptionOptions = EncryptionOptions.encryptCommunications()
        recipients.forEach { encryptionOptions.addRecipient(it) }
        hiddenRecipients.forEach { encryptionOptions.addHiddenRecipient(it) }

        val producerOptions = if (signingKey != null) {
            val signingOptions = SigningOptions.get().addInlineSignature(
                SecretKeyRingProtector.unprotectedKeys(),
                signingKey,
                DocumentSignatureType.BINARY_DOCUMENT,
            )
            ProducerOptions.signAndEncrypt(encryptionOptions, signingOptions)
        } else {
            ProducerOptions.encrypt(encryptionOptions)
        }.setAsciiArmor(true)

        val encryptionStream = PGPainless.encryptAndOrSign().onOutputStream(output).withOptions(producerOptions)
        encryptionStream.use { plaintext.copyTo(it) }
    }

    /**
     * Decrypts [ciphertext] with [decryptionKeys] and writes the plaintext to [output].
     */
    fun decrypt(
        ciphertext: InputStream,
        output: OutputStream,
        decryptionKeys: List<PGPSecretKeyRing>,
        verificationCertificates: List<PGPPublicKeyRing>,
    ): PgpVerification {
        val options = ConsumerOptions.get()
        decryptionKeys.forEach { options.addDecryptionKey(it) }
        if (verificationCertificates.isNotEmpty()) {
            options.addVerificationCerts(PGPPublicKeyRingCollection(verificationCertificates))
        }

        val decryptionStream = PGPainless.decryptAndOrVerify().onInputStream(ciphertext).withOptions(options)
        decryptionStream.use { it.copyTo(output) }

        val metadata = decryptionStream.metadata
        return PgpVerification(
            isEncrypted = metadata.isEncrypted,
            signature = signatureStatus(
                verifiedKeyIds = metadata.verifiedSignatures.map { it.signingKey.keyId to it.signature.creationTime },
                rejectedKeyIds = metadata.rejectedSignatures.map { it.signature.keyID },
                verificationCertificates = verificationCertificates,
            ),
        )
    }

    /**
     * Verifies a detached signature over [signedData].
     */
    fun verifyDetached(
        signedData: InputStream,
        signature: ByteArray,
        verificationCertificates: List<PGPPublicKeyRing>,
    ): PgpSignatureStatus {
        val options = ConsumerOptions.get().addVerificationOfDetachedSignatures(signature.inputStream())
        if (verificationCertificates.isNotEmpty()) {
            options.addVerificationCerts(PGPPublicKeyRingCollection(verificationCertificates))
        }

        val verificationStream = PGPainless.decryptAndOrVerify().onInputStream(signedData).withOptions(options)
        verificationStream.use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (stream.read(buffer) != -1) {
                // Read all data to verify the signature
            }
        }

        val metadata = verificationStream.metadata
        return signatureStatus(
            verifiedKeyIds = metadata.verifiedDetachedSignatures.map {
                it.signingKey.keyId to it.signature.creationTime
            },
            rejectedKeyIds = metadata.rejectedDetachedSignatures.map { it.signature.keyID },
            verificationCertificates = verificationCertificates,
        )
    }

    private fun signatureStatus(
        verifiedKeyIds: List<Pair<Long, Date>>,
        rejectedKeyIds: List<Long>,
        verificationCertificates: List<PGPPublicKeyRing>,
    ): PgpSignatureStatus {
        val verified = verifiedKeyIds.firstOrNull()
        val rejected = rejectedKeyIds.firstOrNull()
        return when {
            verified != null -> {
                val certificate = verificationCertificates.firstOrNull { it.getPublicKey(verified.first) != null }
                PgpSignatureStatus.Valid(
                    keyId = certificate?.publicKey?.keyID ?: verified.first,
                    userIds = certificate?.let { PGPainless.inspectKeyRing(it).validUserIds }.orEmpty(),
                )
            }

            rejected == null -> PgpSignatureStatus.None

            verificationCertificates.any { it.getPublicKey(rejected) != null } -> PgpSignatureStatus.Invalid

            else -> PgpSignatureStatus.KeyMissing(rejected)
        }
    }

    /**
     * Returns `true` if the certificate can currently be used to encrypt messages to [emailAddress].
     */
    fun isUsableFor(certificate: PGPPublicKeyRing, emailAddress: String, now: Date = Date()): Boolean {
        val info = PGPainless.inspectKeyRing(certificate, now)
        return info.revocationState.isNotRevoked() &&
            info.isUsableForEncryption(EncryptionPurpose.COMMUNICATIONS) &&
            info.validUserIds.any { userId -> extractEmailAddress(userId).equals(emailAddress, ignoreCase = true) }
    }

    fun creationDate(certificate: PGPPublicKeyRing): Date = certificate.publicKey.creationTime

    fun fingerprint(certificate: PGPPublicKeyRing): String {
        return certificate.publicKey.fingerprint.joinToString(separator = "") { "%02X".format(it) }
    }

    internal fun extractEmailAddress(userId: String): String {
        val start = userId.lastIndexOf('<')
        val end = userId.lastIndexOf('>')
        return if (start >= 0 && end > start) userId.substring(start + 1, end).trim() else userId.trim()
    }
}

internal data class PgpVerification(
    val isEncrypted: Boolean,
    val signature: PgpSignatureStatus,
)

internal sealed interface PgpSignatureStatus {
    data object None : PgpSignatureStatus

    data class Valid(val keyId: Long, val userIds: List<String>) : PgpSignatureStatus

    data class KeyMissing(val keyId: Long) : PgpSignatureStatus

    data object Invalid : PgpSignatureStatus
}
