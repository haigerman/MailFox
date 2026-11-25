package com.fsck.k9.activity.compose

import android.content.Intent
import app.k9mail.legacy.di.DI
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.autocrypt.AutocryptOperations
import com.fsck.k9.mail.BoundaryGenerator
import com.fsck.k9.mail.filter.EOLConvertingOutputStream
import com.fsck.k9.mail.internet.BinaryTempFileBody
import com.fsck.k9.mail.internet.MessageIdGenerator
import com.fsck.k9.mail.internet.MimeBodyPart
import com.fsck.k9.mail.internet.MimeHeader
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.mail.internet.MimeMessageHelper
import com.fsck.k9.mail.internet.TextBody
import com.fsck.k9.message.MessageBuilder
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.feature.mail.message.composer.signature.HtmlSignatureSanitizer
import net.thunderbird.feature.mailfox.pgp.MailFoxPgp
import org.apache.james.mime4j.util.MimeUtil

/**
 * MailFox: Builds a PGP/MIME encrypted message (RFC 3156) using MailFox' built-in OpenPGP implementation.
 */
class MailFoxPgpMessageBuilder(
    private val mailFoxPgp: MailFoxPgp,
    private val autocryptOperations: AutocryptOperations,
    private val accountUuid: String,
    private val encryptionRequest: MailFoxEncryptionRequest,
    private val isEncryptSubject: Boolean,
    messageIdGenerator: MessageIdGenerator,
    boundaryGenerator: BoundaryGenerator,
    resourceProvider: CoreResourceProvider,
    settingsManager: GeneralSettingsManager,
    htmlSignatureSanitizer: HtmlSignatureSanitizer,
) : MessageBuilder(messageIdGenerator, boundaryGenerator, resourceProvider, settingsManager, htmlSignatureSanitizer) {

    @Suppress("TooGenericExceptionCaught")
    override fun buildMessageInternal() {
        val message = try {
            build()
        } catch (e: MessagingException) {
            queueMessageBuildException(e)
            return
        }

        try {
            addAutocryptHeader(message)
            val contentPart = createContentPart(message)
            val encryptedBody = encrypt(contentPart)
            setEncryptedBody(message, encryptedBody)
            queueMessageBuildSuccess(message)
        } catch (e: MessagingException) {
            queueMessageBuildException(e)
        } catch (e: Exception) {
            queueMessageBuildException(MessagingException("Encryption failed: ${e.message}", e))
        }
    }

    override fun buildMessageOnActivityResult(requestCode: Int, data: Intent) {
        throw UnsupportedOperationException("No user interaction required")
    }

    private fun addAutocryptHeader(message: MimeMessage) {
        val keyData = mailFoxPgp.getAutocryptKeyData(accountUuid) ?: return
        val fromAddress = message.from.firstOrNull()?.address ?: return
        autocryptOperations.addAutocryptHeaderToMessage(message, keyData, fromAddress, false)
    }

    private fun createContentPart(message: MimeMessage): MimeBodyPart {
        val contentPart = message.toBodyPart()
        message.getHeader(MimeHeader.HEADER_CONTENT_TYPE).firstOrNull()?.let { contentType ->
            contentPart.setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType)
        }

        if (isEncryptSubject) {
            message.getHeader(MimeHeader.SUBJECT).firstOrNull()?.let { subject ->
                contentPart.setHeader(
                    MimeHeader.HEADER_CONTENT_TYPE,
                    contentPart.contentType + "; protected-headers=\"v1\"",
                )
                contentPart.setHeader(MimeHeader.SUBJECT, subject)
                message.subject = REPLACEMENT_SUBJECT
            }
        }

        return contentPart
    }

    private fun encrypt(contentPart: MimeBodyPart): BinaryTempFileBody {
        val plaintext = BinaryTempFileBody(MimeUtil.ENC_8BIT)
        plaintext.outputStream.use { contentPart.writeTo(it) }

        val ciphertext = BinaryTempFileBody(MimeUtil.ENC_7BIT)
        // Make sure the ASCII-armored output uses CRLF line endings
        EOLConvertingOutputStream(ciphertext.outputStream).use { output ->
            plaintext.inputStream.use { input ->
                mailFoxPgp.encrypt(
                    accountUuid = accountUuid,
                    plaintext = input,
                    output = output,
                    recipients = encryptionRequest.recipients,
                    hiddenRecipients = encryptionRequest.hiddenRecipients,
                )
            }
        }
        return ciphertext
    }

    private fun setEncryptedBody(message: MimeMessage, encryptedBody: BinaryTempFileBody) {
        val multipartEncrypted = createMimeMultipart()
        multipartEncrypted.setSubType("encrypted")
        multipartEncrypted.addBodyPart(MimeBodyPart.create(TextBody("Version: 1"), "application/pgp-encrypted"))
        val encryptedPart = MimeBodyPart.create(encryptedBody, "application/octet-stream; name=\"encrypted.asc\"")
        encryptedPart.addHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "inline; filename=\"encrypted.asc\"")
        multipartEncrypted.addBodyPart(encryptedPart)
        MimeMessageHelper.setBody(message, multipartEncrypted)

        message.setHeader(
            MimeHeader.HEADER_CONTENT_TYPE,
            "multipart/encrypted; boundary=\"${multipartEncrypted.boundary}\";\r\n" +
                "  protocol=\"application/pgp-encrypted\"",
        )
    }

    companion object {
        private const val REPLACEMENT_SUBJECT = "[...]"

        @JvmStatic
        fun newInstance(
            accountUuid: String,
            encryptionRequest: MailFoxEncryptionRequest,
            isEncryptSubject: Boolean,
        ): MailFoxPgpMessageBuilder {
            return MailFoxPgpMessageBuilder(
                mailFoxPgp = DI.get(MailFoxPgp::class.java),
                autocryptOperations = AutocryptOperations.getInstance(),
                accountUuid = accountUuid,
                encryptionRequest = encryptionRequest,
                isEncryptSubject = isEncryptSubject,
                messageIdGenerator = MessageIdGenerator.getInstance(),
                boundaryGenerator = BoundaryGenerator.getInstance(),
                resourceProvider = DI.get(CoreResourceProvider::class.java),
                settingsManager = DI.get(GeneralSettingsManager::class.java),
                htmlSignatureSanitizer = DI.get(HtmlSignatureSanitizer::class.java),
            )
        }
    }
}
