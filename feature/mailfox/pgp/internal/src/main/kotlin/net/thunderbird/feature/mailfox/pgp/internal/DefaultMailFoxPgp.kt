package net.thunderbird.feature.mailfox.pgp.internal

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.thunderbird.feature.mailfox.pgp.MailFoxPgp
import net.thunderbird.feature.mailfox.pgp.PgpKeyLookupResult
import net.thunderbird.feature.mailfox.pgp.PgpRecipientKey
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.RecipientKeyFinder
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.RecipientKeySearchResult
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore

internal class DefaultMailFoxPgp(
    private val keyStore: PgpKeyStore,
    private val recipientKeyFinder: RecipientKeyFinder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MailFoxPgp {

    override fun hasKey(accountUuid: String): Boolean = keyStore.hasSecretKey(accountUuid)

    override suspend fun lookupRecipientKeys(emailAddresses: List<String>): Map<String, PgpKeyLookupResult> {
        return withContext(ioDispatcher) {
            coroutineScope {
                emailAddresses.distinct()
                    .map { emailAddress -> async { emailAddress to lookup(emailAddress) } }
                    .awaitAll()
                    .toMap()
            }
        }
    }

    private fun lookup(emailAddress: String): PgpKeyLookupResult {
        return when (val result = recipientKeyFinder.find(emailAddress)) {
            is RecipientKeySearchResult.Found -> {
                // Remember the key to be able to verify signatures of messages from this correspondent
                runCatching { keyStore.savePeerCertificate(result.certificate) }

                PgpKeyLookupResult.Found(
                    PgpRecipientKey(
                        emailAddress = emailAddress,
                        fingerprint = PgpCrypto.fingerprint(result.certificate),
                        keyData = result.certificate.encoded,
                    ),
                )
            }

            RecipientKeySearchResult.NotFound -> PgpKeyLookupResult.NotFound

            RecipientKeySearchResult.Failed -> PgpKeyLookupResult.Failed
        }
    }

    override fun encrypt(
        accountUuid: String,
        plaintext: InputStream,
        output: OutputStream,
        recipients: List<PgpRecipientKey>,
        hiddenRecipients: List<PgpRecipientKey>,
    ) {
        val ownKey = keyStore.getSecretKey(accountUuid)
        val recipientCertificates = recipients.map { it.toCertificate() } +
            listOfNotNull(ownKey?.let { PgpCrypto.extractCertificate(it) })

        PgpCrypto.encryptAndSign(
            plaintext = plaintext,
            output = output,
            recipients = recipientCertificates,
            hiddenRecipients = hiddenRecipients.map { it.toCertificate() },
            signingKey = ownKey,
        )
    }

    override fun getAutocryptKeyData(accountUuid: String): ByteArray? {
        return keyStore.getSecretKey(accountUuid)?.let { PgpCrypto.extractCertificate(it).encoded }
    }

    private fun PgpRecipientKey.toCertificate() =
        PgpCrypto.readCertificate(keyData) ?: error("Invalid key for $emailAddress")
}
