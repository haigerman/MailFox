package net.thunderbird.feature.mailfox.pgp.internal.storage

import java.io.File
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing

/**
 * Stores the accounts' secret keys (encrypted with [SecretKeyCipher]) and certificates of correspondents.
 *
 * Correspondents' certificates are only used to verify signatures of received messages. The keys used to encrypt
 * messages are always looked up on the key servers when sending.
 */
internal class PgpKeyStore(
    private val directory: File,
    private val cipher: SecretKeyCipher,
) {
    private val accountsDirectory get() = File(directory, "accounts")
    private val peersDirectory get() = File(directory, "peers")

    @Synchronized
    fun getSecretKey(accountUuid: String): PGPSecretKeyRing? {
        val file = secretKeyFile(accountUuid)
        if (!file.exists()) return null
        return PgpCrypto.readSecretKeyRing(cipher.decrypt(file.readBytes()))
    }

    @Synchronized
    fun hasSecretKey(accountUuid: String): Boolean = secretKeyFile(accountUuid).exists()

    @Synchronized
    fun saveSecretKey(accountUuid: String, secretKeyRing: PGPSecretKeyRing) {
        accountsDirectory.mkdirs()
        val file = secretKeyFile(accountUuid)
        val tempFile = File(file.path + ".tmp")
        tempFile.writeBytes(cipher.encrypt(secretKeyRing.encoded))
        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            error("Couldn't store key")
        }
    }

    @Synchronized
    fun getAllSecretKeys(): List<PGPSecretKeyRing> {
        val files = accountsDirectory.listFiles { file -> file.name.endsWith(SECRET_KEY_SUFFIX) }.orEmpty()
        return files.mapNotNull { file ->
            runCatching { PgpCrypto.readSecretKeyRing(cipher.decrypt(file.readBytes())) }.getOrNull()
        }
    }

    @Synchronized
    fun savePeerCertificate(certificate: PGPPublicKeyRing) {
        peersDirectory.mkdirs()
        File(peersDirectory, PgpCrypto.fingerprint(certificate) + CERTIFICATE_SUFFIX).writeBytes(certificate.encoded)
    }

    @Synchronized
    fun getPeerCertificates(): List<PGPPublicKeyRing> {
        val files = peersDirectory.listFiles { file -> file.name.endsWith(CERTIFICATE_SUFFIX) }.orEmpty()
        return files.mapNotNull { PgpCrypto.readCertificate(it.readBytes()) }
    }

    private fun secretKeyFile(accountUuid: String): File {
        require(accountUuid.all { it.isLetterOrDigit() || it == '-' }) { "Invalid account UUID" }
        return File(accountsDirectory, accountUuid + SECRET_KEY_SUFFIX)
    }

    private companion object {
        const val SECRET_KEY_SUFFIX = ".key"
        const val CERTIFICATE_SUFFIX = ".pgp"
    }
}
