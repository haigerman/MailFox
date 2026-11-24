package app.k9mail.provider

import app.k9mail.core.android.common.provider.NotificationIconResourceProvider
import com.fsck.k9.R

class K9AppNotificationIconProvider : NotificationIconResourceProvider {
    // MailFox: fox status bar icon
    override val pushNotificationIcon: Int
        get() = R.drawable.ic_mailfox_notification
}
