package net.thunderbird.feature.mailfox.pgp.internal

import java.io.File
import net.thunderbird.feature.mailfox.pgp.MailFoxPgp
import net.thunderbird.feature.mailfox.pgp.MailFoxPgpSettingsLauncher
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.KeyServerClient
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.OkHttpKeyServerClient
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.RecipientKeyFinder
import net.thunderbird.feature.mailfox.pgp.internal.provider.OpenPgpRequestHandler
import net.thunderbird.feature.mailfox.pgp.internal.settings.ContentResolverPgpKeyFiles
import net.thunderbird.feature.mailfox.pgp.internal.settings.PgpKeyFiles
import net.thunderbird.feature.mailfox.pgp.internal.settings.PgpSettingsActivityLauncher
import net.thunderbird.feature.mailfox.pgp.internal.settings.PgpSettingsViewModel
import net.thunderbird.feature.mailfox.pgp.internal.storage.AndroidKeystoreCipher
import net.thunderbird.feature.mailfox.pgp.internal.storage.PgpKeyStore
import net.thunderbird.feature.mailfox.pgp.internal.storage.SecretKeyCipher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * MailFox: built-in OpenPGP encryption.
 */
val featureMailFoxPgpModule = module {
    single<SecretKeyCipher> { AndroidKeystoreCipher() }
    single {
        PgpKeyStore(
            directory = File(androidContext().filesDir, "mailfox_pgp"),
            cipher = get(),
        )
    }
    single<KeyServerClient> { OkHttpKeyServerClient() }
    single { RecipientKeyFinder(keyServerClient = get()) }
    single<MailFoxPgp> { DefaultMailFoxPgp(keyStore = get(), recipientKeyFinder = get()) }
    single {
        PgpAccountSetup(
            accountManager = get(),
            keyStore = get(),
            keyServerClient = get(),
            keyImporter = get(),
            openPgpProviderPackageName = androidContext().packageName,
        )
    }
    single { OpenPgpRequestHandler(keyStore = get(), logger = get()) }
    single<MailFoxPgpSettingsLauncher> { PgpSettingsActivityLauncher() }
    single { PgpKeyExporter(keyStore = get()) }
    single { PgpKeyImporter(keyStore = get()) }
    single<PgpKeyFiles> { ContentResolverPgpKeyFiles(androidContext()) }
    viewModel { PgpSettingsViewModel(accountSetup = get(), keyExporter = get(), keyFiles = get()) }
}
