package net.thunderbird.feature.mailfox.pgp.internal.settings

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.OutputStream

/**
 * Opens the file the user picked via the Storage Access Framework.
 */
internal fun interface PgpKeyFileWriter {
    fun openOutputStream(uri: Uri): OutputStream
}

internal class ContentResolverPgpKeyFileWriter(private val context: Context) : PgpKeyFileWriter {
    override fun openOutputStream(uri: Uri): OutputStream {
        return context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open $uri")
    }
}
