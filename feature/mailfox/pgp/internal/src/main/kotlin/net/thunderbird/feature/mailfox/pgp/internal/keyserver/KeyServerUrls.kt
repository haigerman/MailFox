package net.thunderbird.feature.mailfox.pgp.internal.keyserver

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * URLs of the key sources used by MailFox.
 *
 * - Web Key Directory (WKD): the key is published by the recipient's mail provider/domain owner.
 * - keys.openpgp.org: only publishes user IDs whose email address was verified by the key owner.
 *
 * Key servers that accept unverified uploads (e.g. keyserver.ubuntu.com) are deliberately not used: anyone can upload
 * a key with any email address there, so "the newest key" could easily be an attacker's key.
 */
internal object KeyServerUrls {
    const val KEYS_OPENPGP_ORG = "https://keys.openpgp.org"

    fun keysOpenPgpOrgByEmail(emailAddress: String, baseUrl: String = KEYS_OPENPGP_ORG): String {
        return "$baseUrl/vks/v1/by-email/${urlEncode(emailAddress)}"
    }

    fun keysOpenPgpOrgUpload(baseUrl: String = KEYS_OPENPGP_ORG) = "$baseUrl/vks/v1/upload"

    fun keysOpenPgpOrgRequestVerify(baseUrl: String = KEYS_OPENPGP_ORG) = "$baseUrl/vks/v1/request-verify"

    /**
     * Returns the WKD URLs for [emailAddress]: first the "advanced", then the "direct" method.
     */
    fun wkd(emailAddress: String): List<String> {
        val atIndex = emailAddress.lastIndexOf('@')
        if (atIndex <= 0 || atIndex == emailAddress.length - 1) return emptyList()

        val localPart = emailAddress.substring(0, atIndex)
        val domain = emailAddress.substring(atIndex + 1).lowercase(Locale.ROOT)
        val hash = wkdHash(localPart)
        val query = "?l=${urlEncode(localPart)}"

        return listOf(
            "https://openpgpkey.$domain/.well-known/openpgpkey/$domain/hu/$hash$query",
            "https://$domain/.well-known/openpgpkey/hu/$hash$query",
        )
    }

    fun wkdHash(localPart: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(localPart.lowercase(Locale.ROOT).toByteArray())
        return ZBase32.encode(digest)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
