package net.thunderbird.feature.mailfox.pgp.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKeyRing

internal sealed interface PgpImportResult {
    data class Success(val fingerprint: String, val keyId: Long) : PgpImportResult

    /** The key is protected; call again with the passphrase. */
    data object PassphraseRequired : PgpImportResult

    data object WrongPassphrase : PgpImportResult

    /** The file doesn't contain a secret key. */
    data object NoSecretKey : PgpImportResult

    /** The key has no (valid) user ID with the account's email address. */
    data object WrongEmailAddress : PgpImportResult

    /** The key is revoked, expired or can't be used to encrypt and sign. */
    data object KeyNotUsable : PgpImportResult
}

/**
 * Imports a secret key (e.g. exported from MailFox, Thunderbird, OpenKeychain or GnuPG) for an account.
 *
 * The key is stored without passphrase (encrypted at rest by [PgpKeyStore]). A previous key of the account is kept to
 * be able to decrypt older messages.
 */
internal class PgpKeyImporter(
    private val keyStore: PgpKeyStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun import(
        accountUuid: String,
        emailAddress: String,
        keyData: ByteArray,
        passphrase: CharArray?,
    ): PgpImportResult = withContext(ioDispatcher) {
        val secretKey = PgpCrypto.readSecretKeyRing(keyData)
        when {
            secretKey == null -> PgpImportResult.NoSecretKey
            PgpCrypto.isPassphraseProtected(secretKey) && passphrase == null -> PgpImportResult.PassphraseRequired
            else -> importKey(accountUuid, emailAddress, secretKey, passphrase)
        }
    }

    private fun importKey(
        accountUuid: String,
        emailAddress: String,
        secretKey: PGPSecretKeyRing,
        passphrase: CharArray?,
    ): PgpImportResult {
        val unlockedKey = if (PgpCrypto.isPassphraseProtected(secretKey)) {
            unlock(secretKey, requireNotNull(passphrase)) ?: return PgpImportResult.WrongPassphrase
        } else {
            secretKey
        }

        val certificate = PgpCrypto.extractCertificate(unlockedKey)
        val hasEmailAddress = certificate.publicKey.userIDs.asSequence().any { userId ->
            PgpCrypto.extractEmailAddress(userId).equals(emailAddress, ignoreCase = true)
        }

        return when {
            !hasEmailAddress -> PgpImportResult.WrongEmailAddress

            !PgpCrypto.isUsableOwnKeyFor(unlockedKey, emailAddress) -> PgpImportResult.KeyNotUsable

            else -> {
                keyStore.saveSecretKey(accountUuid, unlockedKey)
                PgpImportResult.Success(
                    fingerprint = PgpCrypto.fingerprint(certificate),
                    keyId = unlockedKey.publicKey.keyID,
                )
            }
        }
    }

    private fun unlock(secretKey: PGPSecretKeyRing, passphrase: CharArray): PGPSecretKeyRing? {
        val unlockedKey = try {
            PgpCrypto.removePassphrase(secretKey, passphrase)
        } catch (@Suppress("SwallowedException") e: PGPException) {
            // Wrong passphrase
            return null
        }
        return unlockedKey.takeUnless { PgpCrypto.isPassphraseProtected(it) }
    }
}
