package net.thunderbird.feature.mailfox.pgp

import android.content.Context
import android.content.Intent

/**
 * MailFox: Creates the intent for the screen "Set up mail encryption with PGP".
 */
fun interface MailFoxPgpSettingsLauncher {
    fun createIntent(context: Context): Intent
}
