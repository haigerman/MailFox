package net.thunderbird.feature.mailfox.pgp.internal.keyserver

import java.io.IOException
import java.util.Date
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import org.bouncycastle.openpgp.PGPPublicKeyRing

internal sealed interface RecipientKeySearchResult {
    data class Found(val certificate: PGPPublicKeyRing) : RecipientKeySearchResult
    data object NotFound : RecipientKeySearchResult
    data object Failed : RecipientKeySearchResult
}

/**
 * Finds the newest usable key for an email address.
 */
internal class RecipientKeyFinder(
    private val keyServerClient: KeyServerClient,
    private val clock: () -> Date = { Date() },
) {
    fun find(emailAddress: String): RecipientKeySearchResult {
        val keyData = try {
            keyServerClient.fetchKeys(emailAddress)
        } catch (@Suppress("SwallowedException") e: IOException) {
            // Network problems are reported to the user as "key search failed"
            return RecipientKeySearchResult.Failed
        }

        val candidates = keyData.flatMap { PgpCrypto.readCertificates(it) }
        val certificate = selectNewest(candidates, emailAddress, clock())
        return certificate?.let { RecipientKeySearchResult.Found(it) } ?: RecipientKeySearchResult.NotFound
    }

    companion object {
        /**
         * Returns the most recently created certificate that is currently usable to encrypt to [emailAddress].
         */
        fun selectNewest(candidates: List<PGPPublicKeyRing>, emailAddress: String, now: Date): PGPPublicKeyRing? {
            return candidates
                .filter { PgpCrypto.isUsableFor(it, emailAddress, now) }
                .maxByOrNull { PgpCrypto.creationDate(it) }
        }
    }
}
