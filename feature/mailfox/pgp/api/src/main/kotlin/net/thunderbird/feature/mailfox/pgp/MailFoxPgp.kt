package net.thunderbird.feature.mailfox.pgp

import java.io.InputStream
import java.io.OutputStream

/**
 * MailFox: built-in OpenPGP encryption.
 */
interface MailFoxPgp {

    /**
     * Returns `true` if a PGP key has been set up for the account.
     */
    fun hasKey(accountUuid: String): Boolean

    /**
     * Looks up the current public key of every address on the key servers.
     *
     * Network operation. The result contains an entry for every address in [emailAddresses].
     */
    suspend fun lookupRecipientKeys(emailAddresses: List<String>): Map<String, PgpKeyLookupResult>

    /**
     * Encrypts [plaintext] (a MIME entity) and writes the ASCII-armored OpenPGP message to [output].
     *
     * The message is encrypted to [recipients], to [hiddenRecipients] (without disclosing their key IDs) and to the
     * account's own key, and signed with the account's key, if the account has one.
     *
     * Blocking operation, must not be called on the main thread.
     */
    fun encrypt(
        accountUuid: String,
        plaintext: InputStream,
        output: OutputStream,
        recipients: List<PgpRecipientKey>,
        hiddenRecipients: List<PgpRecipientKey>,
    )

    /**
     * Returns the account's public key (binary) for the `Autocrypt` header, or `null` if there is none.
     */
    fun getAutocryptKeyData(accountUuid: String): ByteArray?
}

/**
 * A recipient's public key.
 */
class PgpRecipientKey(
    val emailAddress: String,
    val fingerprint: String,
    val keyData: ByteArray,
)

sealed interface PgpKeyLookupResult {
    data class Found(val key: PgpRecipientKey) : PgpKeyLookupResult

    data object NotFound : PgpKeyLookupResult

    /**
     * The lookup failed, e.g. because there is no network connection.
     */
    data object Failed : PgpKeyLookupResult
}
