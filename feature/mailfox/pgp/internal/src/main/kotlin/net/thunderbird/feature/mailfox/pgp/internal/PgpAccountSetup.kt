package net.thunderbird.feature.mailfox.pgp.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.KeyServerClient
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.PublishStatus
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore

internal data class PgpAccountInfo(
    val accountUuid: String,
    val displayName: String,
    val emailAddress: String,
    val fingerprint: String?,
)

internal sealed interface PgpSetupResult {
    data class Success(val fingerprint: String, val publishStatus: PublishStatus?) : PgpSetupResult
    data object Error : PgpSetupResult
}

/**
 * Sets up PGP for an account: generates a key (if there is none yet), uses MailFox as OpenPGP provider for the
 * account (to decrypt received messages) and publishes the public key on keys.openpgp.org.
 */
internal class PgpAccountSetup(
    private val accountManager: LegacyAccountDtoManager,
    private val keyStore: PgpKeyStore,
    private val keyServerClient: KeyServerClient,
    private val openPgpProviderPackageName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getAccounts(): List<PgpAccountInfo> = withContext(ioDispatcher) {
        accountManager.getAccounts().map { account ->
            PgpAccountInfo(
                accountUuid = account.uuid,
                displayName = account.displayName,
                emailAddress = account.email,
                fingerprint = keyStore.getSecretKey(account.uuid)?.let { PgpCrypto.fingerprint(it.toCertificate()) },
            )
        }
    }

    suspend fun setUp(accountUuid: String): PgpSetupResult = withContext(ioDispatcher) {
        val account = accountManager.getAccount(accountUuid) ?: return@withContext PgpSetupResult.Error

        val secretKey = keyStore.getSecretKey(accountUuid)
            ?: PgpCrypto.generateKey(account.senderName, account.email).also { keyStore.saveSecretKey(accountUuid, it) }

        useMailFoxAsOpenPgpProvider(account, keyId = secretKey.publicKey.keyID)

        val certificate = secretKey.toCertificate()
        // Publishing can be retried later ("Publish again"), so a failure is not an error here
        val publishStatus = runCatching { keyServerClient.publish(PgpCrypto.armor(certificate), account.email) }
            .getOrNull()

        PgpSetupResult.Success(fingerprint = PgpCrypto.fingerprint(certificate), publishStatus = publishStatus)
    }

    private fun useMailFoxAsOpenPgpProvider(account: LegacyAccountDto, keyId: Long) {
        account.openPgpProvider = openPgpProviderPackageName
        account.openPgpKey = keyId
        accountManager.saveAccount(account)
    }
}
