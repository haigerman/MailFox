package net.thunderbird.feature.mailfox.pgp.internal

import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.feature.mailfox.pgp.internal.crypto.PgpCrypto
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore

/**
 * Exports an account's secret key as ASCII-armored OpenPGP key, protected with a passphrase.
 *
 * The exported file can be imported e.g. into Thunderbird, OpenKeychain or GnuPG.
 */
internal class PgpKeyExporter(
    private val keyStore: PgpKeyStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun export(accountUuid: String, passphrase: CharArray, output: () -> OutputStream) {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Passphrase too short" }

        withContext(ioDispatcher) {
            val secretKey = keyStore.getSecretKey(accountUuid) ?: error("No key for this account")
            val protectedKey = PgpCrypto.protectWithPassphrase(secretKey, passphrase)
            val armoredKey = PgpCrypto.armorSecretKey(protectedKey)

            output().use { it.write(armoredKey.toByteArray(Charsets.US_ASCII)) }
        }
    }

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
