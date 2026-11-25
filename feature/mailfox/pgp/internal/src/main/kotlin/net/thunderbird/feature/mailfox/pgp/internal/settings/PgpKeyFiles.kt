package net.thunderbird.feature.mailfox.pgp.internal.settings

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.OutputStream

/**
 * Access to the key files the user picked via the Storage Access Framework.
 */
internal interface PgpKeyFiles {
    fun openOutputStream(uri: Uri): OutputStream

    /**
     * Reads the file. Key files are small; larger files are rejected.
     */
    fun readBytes(uri: Uri): ByteArray
}

internal class ContentResolverPgpKeyFiles(private val context: Context) : PgpKeyFiles {
    override fun openOutputStream(uri: Uri): OutputStream {
        return context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open $uri")
    }

    override fun readBytes(uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
        return inputStream.use { input ->
            val data = input.readNBytesCompat(MAX_KEY_FILE_SIZE + 1)
            if (data.size > MAX_KEY_FILE_SIZE) throw IOException("File too large")
            data
        }
    }

    private fun java.io.InputStream.readNBytesCompat(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (total < maxBytes) {
            val read = read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_KEY_FILE_SIZE = 1024 * 1024
        const val BUFFER_SIZE = 8192
    }
}
