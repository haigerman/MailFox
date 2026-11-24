package app.k9mail.provider

import androidx.compose.runtime.Composable
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

internal class K9FeatureThemeProvider : FeatureThemeProvider {
    @Composable
    override fun WithTheme(content: @Composable () -> Unit) {
        MailFoxBoltTheme {
            content()
        }
    }

    @Composable
    override fun WithTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
        MailFoxBoltTheme(darkTheme = darkTheme) {
            content()
        }
    }
}
