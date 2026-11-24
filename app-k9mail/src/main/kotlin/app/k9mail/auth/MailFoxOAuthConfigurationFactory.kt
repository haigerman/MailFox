package app.k9mail.auth

import net.thunderbird.core.common.oauth.OAuthConfiguration
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory

/**
 * MailFox: OAuth 2.0 is not supported.
 *
 * The OAuth client registrations of K-9 Mail (Gmail, Microsoft, Yahoo, AOL, Fastmail, Thundermail) are bound to K-9's
 * application ID and signing key and don't work with MailFox. Without any configuration, account setup never offers
 * OAuth sign-in.
 */
class MailFoxOAuthConfigurationFactory : OAuthConfigurationFactory {
    override fun createConfigurations(): Map<List<String>, OAuthConfiguration> = emptyMap()
}
