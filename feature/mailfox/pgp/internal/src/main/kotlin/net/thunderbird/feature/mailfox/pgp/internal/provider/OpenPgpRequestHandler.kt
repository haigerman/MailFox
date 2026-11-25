package net.thunderbird.feature.mailfox.pgp.internal.provider

import android.content.Intent
import androidx.core.content.IntentCompat
import java.io.InputStream
import java.io.OutputStream
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpSignatureStatus
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore
import org.openintents.openpgp.AutocryptPeerUpdate
import org.openintents.openpgp.OpenPgpDecryptionResult
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.OpenPgpSignatureResult
import org.openintents.openpgp.OpenPgpSignatureResult.SenderStatusResult
import org.openintents.openpgp.util.OpenPgpApi

private const val TAG = "MailFoxOpenPgp"

/**
 * Handles the OpenPGP API requests K-9 makes when displaying messages.
 */
internal class OpenPgpRequestHandler(
    private val keyStore: PgpKeyStore,
    private val logger: Logger,
) {
    @Suppress("TooGenericExceptionCaught")
    fun execute(data: Intent, input: InputStream?, output: OutputStream?): Intent {
        return try {
            when (data.action) {
                OpenPgpApi.ACTION_CHECK_PERMISSION -> success()
                OpenPgpApi.ACTION_DECRYPT_VERIFY -> decryptVerify(data, input, output)
                OpenPgpApi.ACTION_UPDATE_AUTOCRYPT_PEER -> updateAutocryptPeer(data)
                OpenPgpApi.ACTION_GET_SIGN_KEY_ID -> getSignKeyId(data)
                OpenPgpApi.ACTION_GET_KEY -> getKey(data, output)
                else -> error("Not supported by MailFox: ${data.action}")
            }
        } catch (e: Exception) {
            logger.error(TAG, e) { "OpenPGP operation failed" }
            error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun decryptVerify(data: Intent, input: InputStream?, output: OutputStream?): Intent {
        requireNotNull(input) { "No input" }
        rememberAutocryptKeys(data)

        val senderAddress = data.getStringExtra(OpenPgpApi.EXTRA_SENDER_ADDRESS)
        val certificates = keyStore.getPeerCertificates() + keyStore.getAllSecretKeys().map {
            PgpCrypto.extractCertificate(it)
        }

        val detachedSignature = data.getByteArrayExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE)
        if (detachedSignature != null) {
            val signatureStatus = PgpCrypto.verifyDetached(input, detachedSignature, certificates)
            return success()
                .putExtra(
                    OpenPgpApi.RESULT_DECRYPTION,
                    OpenPgpDecryptionResult(OpenPgpDecryptionResult.RESULT_NOT_ENCRYPTED),
                )
                .putExtra(OpenPgpApi.RESULT_SIGNATURE, signatureStatus.toSignatureResult(senderAddress))
        }

        requireNotNull(output) { "No output" }
        val verification = PgpCrypto.decrypt(
            ciphertext = input,
            output = output,
            decryptionKeys = keyStore.getAllSecretKeys(),
            verificationCertificates = certificates,
        )
        val decryptionResult = if (verification.isEncrypted) {
            OpenPgpDecryptionResult.RESULT_ENCRYPTED
        } else {
            OpenPgpDecryptionResult.RESULT_NOT_ENCRYPTED
        }

        return success()
            .putExtra(OpenPgpApi.RESULT_DECRYPTION, OpenPgpDecryptionResult(decryptionResult))
            .putExtra(OpenPgpApi.RESULT_SIGNATURE, verification.signature.toSignatureResult(senderAddress))
    }

    private fun updateAutocryptPeer(data: Intent): Intent {
        rememberAutocryptKeys(data)
        return success()
    }

    /**
     * Keys sent via Autocrypt are only used to verify signatures, never to encrypt.
     */
    private fun rememberAutocryptKeys(data: Intent) {
        val updates = buildList {
            IntentCompat.getParcelableExtra(
                data,
                OpenPgpApi.EXTRA_AUTOCRYPT_PEER_UPDATE,
                AutocryptPeerUpdate::class.java,
            )
                ?.let { add(it) }
            data.extras?.getBundle(OpenPgpApi.EXTRA_AUTOCRYPT_PEER_GOSSIP_UPDATES)?.let { bundle ->
                bundle.classLoader = AutocryptPeerUpdate::class.java.classLoader
                bundle.keySet().forEach { key ->
                    @Suppress("DEPRECATION")
                    (bundle.getParcelable<AutocryptPeerUpdate>(key))?.let { add(it) }
                }
            }
        }

        for (update in updates) {
            val keyData = update.keyData ?: continue
            PgpCrypto.readCertificate(keyData)?.let { certificate ->
                runCatching { keyStore.savePeerCertificate(certificate) }
            }
        }
    }

    private fun getSignKeyId(data: Intent): Intent {
        val userId = data.getStringExtra(OpenPgpApi.EXTRA_USER_ID)
        val key = findOwnKey(userId) ?: return error("No MailFox key for this account")
        return success().putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, key.publicKey.keyID)
    }

    private fun getKey(data: Intent, output: OutputStream?): Intent {
        val keyId = data.getLongExtra(OpenPgpApi.EXTRA_KEY_ID, 0L)
        val key = keyStore.getAllSecretKeys().firstOrNull { it.publicKey.keyID == keyId }
            ?: return error("Key not found")
        output?.write(PgpCrypto.extractCertificate(key).encoded)
        return success()
    }

    private fun findOwnKey(emailAddress: String?) = keyStore.getAllSecretKeys().firstOrNull { key ->
        emailAddress == null || key.publicKey.userIDs.asSequence().any { userId ->
            PgpCrypto.extractEmailAddress(userId).equals(emailAddress, ignoreCase = true)
        }
    }

    private fun PgpSignatureStatus.toSignatureResult(senderAddress: String?): OpenPgpSignatureResult = when (this) {
        PgpSignatureStatus.None -> OpenPgpSignatureResult.createWithNoSignature()

        PgpSignatureStatus.Invalid -> OpenPgpSignatureResult.createWithInvalidSignature()

        is PgpSignatureStatus.KeyMissing -> OpenPgpSignatureResult.createWithKeyMissing(keyId)

        is PgpSignatureStatus.Valid -> {
            val emailAddresses = userIds.map { PgpCrypto.extractEmailAddress(it) }
            val senderStatus = when {
                senderAddress == null -> SenderStatusResult.UNKNOWN

                emailAddresses.any {
                    it.equals(senderAddress, ignoreCase = true)
                } -> SenderStatusResult.USER_ID_UNCONFIRMED

                else -> SenderStatusResult.USER_ID_MISSING
            }
            OpenPgpSignatureResult.createWithValidSignature(
                OpenPgpSignatureResult.RESULT_VALID_KEY_UNCONFIRMED,
                userIds.firstOrNull(),
                keyId,
                ArrayList(userIds),
                ArrayList(),
                senderStatus,
            )
        }
    }

    private fun success() = Intent().putExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_SUCCESS)

    private fun error(message: String) = Intent()
        .putExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)
        .putExtra(OpenPgpApi.RESULT_ERROR, OpenPgpError(OpenPgpError.GENERIC_ERROR, message))
}
