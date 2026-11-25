package net.thunderbird.feature.mailfox.pgp.internal.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.feature.mailfox.pgp.MailFoxPgpSettingsLauncher
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * MailFox: "Set up mail encryption with PGP".
 */
class PgpSettingsActivity : ComponentActivity() {
    private val themeProvider: FeatureThemeProvider by inject()
    private val viewModel: PgpSettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            themeProvider.WithTheme {
                PgpSettingsScreen(viewModel = viewModel, onBack = ::finish)
            }
        }
    }
}

internal class PgpSettingsActivityLauncher : MailFoxPgpSettingsLauncher {
    override fun createIntent(context: Context): Intent = Intent(context, PgpSettingsActivity::class.java)
}
