package net.thunderbird.feature.mailfox.pgp.internal.provider

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.util.concurrent.ConcurrentHashMap
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openintents.openpgp.IOpenPgpService2

/**
 * MailFox: built-in OpenPGP provider implementing the OpenPGP API (as used by OpenKeychain).
 *
 * The account's `openPgpProvider` is set to MailFox' own package name, so K-9's existing message view code uses this
 * service to decrypt and verify messages with the keys managed by MailFox. The service is not exported.
 */
class MailFoxOpenPgpService : Service(), KoinComponent {
    private val requestHandler: OpenPgpRequestHandler by inject()
    private val outputPipes = ConcurrentHashMap<Int, ParcelFileDescriptor>()

    private val binder = object : IOpenPgpService2.Stub() {
        override fun createOutputPipe(pipeId: Int): ParcelFileDescriptor {
            val pipe = ParcelFileDescriptor.createPipe()
            outputPipes[pipeId] = pipe[1]
            return pipe[0]
        }

        override fun execute(data: Intent, input: ParcelFileDescriptor?, pipeId: Int): Intent {
            val output = outputPipes.remove(pipeId)
            val inputStream = input?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
            val outputStream = output?.let { ParcelFileDescriptor.AutoCloseOutputStream(it) }
            try {
                return requestHandler.execute(data, inputStream, outputStream)
            } finally {
                runCatching { inputStream?.close() }
                runCatching { outputStream?.close() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
