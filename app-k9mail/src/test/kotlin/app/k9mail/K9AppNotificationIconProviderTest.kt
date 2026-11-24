package app.k9mail

import app.k9mail.provider.K9AppNotificationIconProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class K9AppNotificationIconProviderTest {
    @Test
    fun `provides correct K9 notification icon`() {
        val provider = K9AppNotificationIconProvider()
        val icon = provider.pushNotificationIcon

        assertThat(icon)
            .isEqualTo(com.fsck.k9.R.drawable.ic_mailfox_notification)
    }
}
