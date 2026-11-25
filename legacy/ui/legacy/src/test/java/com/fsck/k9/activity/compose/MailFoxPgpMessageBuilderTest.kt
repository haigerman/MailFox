package com.fsck.k9.activity.compose

import android.app.PendingIntent
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.autocrypt.AutocryptOperations
import com.fsck.k9.mail.BoundaryGenerator
import com.fsck.k9.mail.internet.BinaryTempFileBody
import com.fsck.k9.mail.internet.MessageIdGenerator
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.mail.internet.MimeMultipart
import com.fsck.k9.mail.internet.MimeUtility
import com.fsck.k9.message.MessageBuilder
import com.fsck.k9.message.QuotedTextMode
import com.fsck.k9.message.SimpleMessageFormat
import com.fsck.k9.message.quote.InsertableHtmlContent
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import kotlinx.coroutines.flow.Flow
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.QuoteStyle
import net.thunderbird.core.android.testing.RobolectricPendingWorkRule
import net.thunderbird.core.common.appConfig.PlatformConfigProvider
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.preference.GeneralSettings
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.privacy.PrivacySettings
import net.thunderbird.feature.mailfox.pgp.MailFoxPgp
import net.thunderbird.feature.mailfox.pgp.PgpKeyLookupResult
import net.thunderbird.feature.mailfox.pgp.PgpRecipientKey
import net.thunderbird.legacy.logging.Log
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.component.inject
import org.robolectric.RuntimeEnvironment

class MailFoxPgpMessageBuilderTest : K9RobolectricTest() {
    @get:Rule
    val pendingWork = RobolectricPendingWorkRule()

    private val resourceProvider: CoreResourceProvider by inject()
    private val fakePgp = FakeMailFoxPgp()
    private val callback = FakeCallback()

    @Before
    fun setUp() {
        Log.logger = TestLogger()
        BinaryTempFileBody.setTempDirectory(RuntimeEnvironment.getApplication().cacheDir)
    }

    @Test
    fun `build should create PGP MIME encrypted message`() {
        val testSubject = createTestSubject(isEncryptSubject = false)

        testSubject.buildAsync(callback)
        pendingWork.runAllTasks()

        val message = checkNotNull(callback.message)
        assertThat(message.mimeType).isEqualTo("multipart/encrypted")
        assertThat(MimeUtility.getHeaderParameter(message.contentType, "protocol"))
            .isEqualTo("application/pgp-encrypted")
        val multipart = message.body as MimeMultipart
        assertThat(multipart.bodyParts).hasSize(2)
        assertThat(multipart.getBodyPart(0).mimeType).isEqualTo("application/pgp-encrypted")
        assertThat(multipart.getBodyPart(1).mimeType).isEqualTo("application/octet-stream")
        val ciphertext = multipart.getBodyPart(1).body.inputStream.readBytes().toString(Charsets.US_ASCII)
        assertThat(ciphertext).isEqualTo(FAKE_CIPHERTEXT.replace("\n", "\r\n"))
        assertThat(message.subject).isEqualTo("subject")
    }

    @Test
    fun `build should pass the MIME content and the keys to the encryption`() {
        val testSubject = createTestSubject(isEncryptSubject = false)

        testSubject.buildAsync(callback)
        pendingWork.runAllTasks()

        assertThat(fakePgp.plaintext).isNotNull().contains("Content-Type: text/plain")
        assertThat(fakePgp.plaintext).isNotNull().contains(TEST_MESSAGE_TEXT)
        assertThat(fakePgp.recipients.map { it.emailAddress }).containsExactly("to@example.org")
        assertThat(fakePgp.hiddenRecipients.map { it.emailAddress }).containsExactly("bcc@example.org")
    }

    @Test
    fun `build with encrypted subject should move subject into encrypted part`() {
        val testSubject = createTestSubject(isEncryptSubject = true)

        testSubject.buildAsync(callback)
        pendingWork.runAllTasks()

        assertThat(callback.message?.subject).isEqualTo("[...]")
        assertThat(fakePgp.plaintext).isNotNull().contains("Subject: subject")
        assertThat(fakePgp.plaintext).isNotNull().contains("protected-headers=\"v1\"")
    }

    @Test
    fun `build should report encryption errors`() {
        fakePgp.failEncryption = true
        val testSubject = createTestSubject(isEncryptSubject = false)

        testSubject.buildAsync(callback)
        pendingWork.runAllTasks()

        assertThat(callback.message).isNull()
        assertThat(callback.exception).isNotNull()
    }

    private fun createTestSubject(isEncryptSubject: Boolean): MailFoxPgpMessageBuilder {
        val builder = MailFoxPgpMessageBuilder(
            mailFoxPgp = fakePgp,
            autocryptOperations = AutocryptOperations.getInstance(),
            accountUuid = "account",
            encryptionRequest = MailFoxEncryptionRequest(
                recipients = listOf(recipientKey("to@example.org")),
                hiddenRecipients = listOf(recipientKey("bcc@example.org")),
            ),
            isEncryptSubject = isEncryptSubject,
            messageIdGenerator = MessageIdGenerator.getInstance(),
            boundaryGenerator = BoundaryGenerator.getInstance(),
            resourceProvider = resourceProvider,
            settingsManager = fakeGeneralSettingsManager,
            htmlSignatureSanitizer = { error("HTML signature sanitizer called unexpectedly") },
        )

        builder.setSubject("subject")
            .setSentDate(Date())
            .setHideTimeZone(false)
            .setTo(ArrayList())
            .setCc(ArrayList())
            .setBcc(ArrayList())
            .setRequestReadReceipt(false)
            .setIdentity(
                Identity(name = "tester", email = "sender@example.org", description = "test", signatureUse = false),
            )
            .setMessageFormat(SimpleMessageFormat.TEXT)
            .setText(TEST_MESSAGE_TEXT)
            .setAttachments(ArrayList())
            .setSignature("signature")
            .setQuoteStyle(QuoteStyle.PREFIX)
            .setQuotedTextMode(QuotedTextMode.NONE)
            .setQuotedText("")
            .setQuotedHtmlContent(InsertableHtmlContent())
            .setReplyAfterQuote(false)
            .setSignatureBeforeQuotedText(false)
            .setIdentityChanged(false)
            .setSignatureChanged(false)
            .setCursorPosition(0)
            .setMessageReference(null)
            .isDraft = false

        return builder
    }

    private fun recipientKey(emailAddress: String) = PgpRecipientKey(emailAddress, "FINGERPRINT", byteArrayOf())

    private class FakeMailFoxPgp : MailFoxPgp {
        var plaintext: String? = null
        var recipients: List<PgpRecipientKey> = emptyList()
        var hiddenRecipients: List<PgpRecipientKey> = emptyList()
        var failEncryption = false

        override fun hasKey(accountUuid: String) = true

        override suspend fun lookupRecipientKeys(emailAddresses: List<String>): Map<String, PgpKeyLookupResult> =
            error("not used")

        override fun encrypt(
            accountUuid: String,
            plaintext: InputStream,
            output: OutputStream,
            recipients: List<PgpRecipientKey>,
            hiddenRecipients: List<PgpRecipientKey>,
        ) {
            if (failEncryption) error("encryption failed")
            this.plaintext = plaintext.readBytes().toString(Charsets.UTF_8)
            this.recipients = recipients
            this.hiddenRecipients = hiddenRecipients
            output.write(FAKE_CIPHERTEXT.toByteArray())
        }

        override fun getAutocryptKeyData(accountUuid: String): ByteArray? = null
    }

    private class FakeCallback : MessageBuilder.Callback {
        var message: MimeMessage? = null
        var exception: MessagingException? = null

        override fun onMessageBuildSuccess(message: MimeMessage, isDraft: Boolean) {
            this.message = message
        }

        override fun onMessageBuildCancel() = Unit

        override fun onMessageBuildException(exception: MessagingException) {
            this.exception = exception
        }

        override fun onMessageBuildReturnPendingIntent(pendingIntent: PendingIntent, requestCode: Int) = Unit
    }

    private companion object {
        const val TEST_MESSAGE_TEXT = "Hallo, das ist geheim!"
        const val FAKE_CIPHERTEXT = "-----BEGIN PGP MESSAGE-----\nFAKE\n-----END PGP MESSAGE-----\n"

        val fakeGeneralSettingsManager = object : GeneralSettingsManager {
            @Deprecated("Use PreferenceManager<GeneralSettings>.getConfig() instead")
            override fun getSettings() = GeneralSettings(
                privacy = PrivacySettings(isHideUserAgent = false, isHideTimeZone = false),
                platformConfigProvider = object : PlatformConfigProvider {
                    override val isDebug: Boolean = false
                },
            )

            @Deprecated("Use PreferenceManager<GeneralSettings>.getConfigFlow() instead")
            override fun getSettingsFlow(): Flow<GeneralSettings> = error("not implemented")
            override fun save(config: GeneralSettings) = error("not implemented")
            override fun getConfig(): GeneralSettings = error("not implemented")
            override fun getConfigFlow(): Flow<GeneralSettings> = error("not implemented")
        }
    }
}
