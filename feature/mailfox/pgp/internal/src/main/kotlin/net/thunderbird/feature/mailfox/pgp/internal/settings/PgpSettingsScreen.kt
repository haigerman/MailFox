package net.thunderbird.feature.mailfox.pgp.internal.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.card.CardOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlinedPassword
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.organism.TopAppBarWithBackButton
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.mailfox.pgp.internal.R

@Composable
internal fun PgpSettingsScreen(
    viewModel: PgpSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val createExportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { uri -> viewModel.event(PgpSettingsEvent.ExportFileSelected(uri)) }

    val openImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.event(PgpSettingsEvent.ImportFileSelected(uri)) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PgpSettingsEffect.CreateExportFile -> createExportFile.launch(effect.fileName)
                PgpSettingsEffect.OpenImportFile -> openImportFile.launch(arrayOf("*/*"))
            }
        }
    }

    state.exportDialog?.let { dialog ->
        PgpExportDialog(dialog = dialog, onEvent = viewModel::event)
    }

    state.importDialog?.let { dialog ->
        PgpImportDialog(dialog = dialog, onEvent = viewModel::event)
    }

    Scaffold(
        topBar = {
            TopAppBarWithBackButton(
                title = stringResource(R.string.mailfox_pgp_settings_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.double),
        ) {
            item {
                TextBodyMedium(
                    text = stringResource(R.string.mailfox_pgp_settings_intro),
                    modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
                )
            }

            items(state.accounts, key = { it.accountUuid }) { account ->
                PgpAccountCard(
                    account = account,
                    onSetUpClick = { viewModel.event(PgpSettingsEvent.SetUpClicked(account.accountUuid)) },
                    onExportClick = { viewModel.event(PgpSettingsEvent.ExportClicked(account.accountUuid)) },
                    onImportClick = { viewModel.event(PgpSettingsEvent.ImportClicked(account.accountUuid)) },
                    modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
                )
            }
        }
    }
}

@Composable
private fun PgpAccountCard(
    account: PgpAccountItem,
    onSetUpClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(BoltTheme.spacings.double),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        ) {
            TextTitleMedium(text = account.displayName)
            TextBodyMedium(text = account.emailAddress)

            if (account.fingerprint != null) {
                TextBodySmall(
                    text = stringResource(
                        R.string.mailfox_pgp_settings_fingerprint,
                        formatFingerprint(account.fingerprint),
                    ),
                )
            }

            statusText(account)?.let { TextBodyMedium(text = it) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val isInProgress = account.status == PgpSetupStatus.InProgress
                ButtonFilled(
                    text = stringResource(
                        if (account.fingerprint == null) {
                            R.string.mailfox_pgp_settings_set_up_button
                        } else {
                            R.string.mailfox_pgp_settings_publish_again_button
                        },
                    ),
                    onClick = onSetUpClick,
                    enabled = !isInProgress,
                )
                if (isInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = BoltTheme.spacings.double)
                            .size(PROGRESS_SIZE.dp),
                    )
                }
            }

            if (account.fingerprint != null) {
                ButtonOutlined(
                    text = stringResource(R.string.mailfox_pgp_export_button),
                    onClick = onExportClick,
                    enabled = account.exportStatus != PgpExportStatus.InProgress,
                )
                exportStatusText(account.exportStatus)?.let { TextBodyMedium(text = it) }
            }

            ButtonOutlined(
                text = stringResource(R.string.mailfox_pgp_import_button),
                onClick = onImportClick,
                enabled = account.importStatus != PgpImportStatus.InProgress,
            )
            importStatusText(account.importStatus)?.let { TextBodyMedium(text = it) }
        }
    }
}

@Composable
private fun PgpImportDialog(
    dialog: PgpImportDialogState,
    onEvent: (PgpSettingsEvent) -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.mailfox_pgp_import_dialog_title),
        confirmText = stringResource(R.string.mailfox_pgp_import_dialog_confirm),
        onConfirmClick = { onEvent(PgpSettingsEvent.ImportConfirmed) },
        onDismissRequest = { onEvent(PgpSettingsEvent.ImportDialogDismissed) },
        dismissText = stringResource(android.R.string.cancel),
        onDismissClick = { onEvent(PgpSettingsEvent.ImportDialogDismissed) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default)) {
            TextBodyMedium(text = stringResource(R.string.mailfox_pgp_import_dialog_text))
            TextFieldOutlinedPassword(
                value = dialog.passphrase,
                onValueChange = { onEvent(PgpSettingsEvent.ImportPassphraseChanged(it)) },
                label = stringResource(R.string.mailfox_pgp_export_passphrase_label),
                hasError = dialog.isWrongPassphrase,
                modifier = Modifier.fillMaxWidth(),
            )
            if (dialog.isWrongPassphrase) {
                TextBodySmall(
                    text = stringResource(R.string.mailfox_pgp_import_error_wrong_passphrase),
                    color = BoltTheme.colors.error,
                )
            }
        }
    }
}

@Composable
private fun importStatusText(status: PgpImportStatus): String? = when (status) {
    PgpImportStatus.None -> null
    PgpImportStatus.InProgress -> stringResource(R.string.mailfox_pgp_import_status_in_progress)
    PgpImportStatus.Done -> stringResource(R.string.mailfox_pgp_import_status_done)
    PgpImportStatus.NoSecretKey -> stringResource(R.string.mailfox_pgp_import_status_no_secret_key)
    PgpImportStatus.WrongEmailAddress -> stringResource(R.string.mailfox_pgp_import_status_wrong_email_address)
    PgpImportStatus.KeyNotUsable -> stringResource(R.string.mailfox_pgp_import_status_key_not_usable)
    PgpImportStatus.Failed -> stringResource(R.string.mailfox_pgp_import_status_failed)
}

@Composable
private fun PgpExportDialog(
    dialog: PgpExportDialogState,
    onEvent: (PgpSettingsEvent) -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.mailfox_pgp_export_dialog_title),
        confirmText = stringResource(R.string.mailfox_pgp_export_dialog_confirm),
        onConfirmClick = { onEvent(PgpSettingsEvent.ExportConfirmed) },
        onDismissRequest = { onEvent(PgpSettingsEvent.ExportDialogDismissed) },
        dismissText = stringResource(android.R.string.cancel),
        onDismissClick = { onEvent(PgpSettingsEvent.ExportDialogDismissed) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default)) {
            TextBodyMedium(text = stringResource(R.string.mailfox_pgp_export_dialog_text))
            TextFieldOutlinedPassword(
                value = dialog.passphrase,
                onValueChange = { onEvent(PgpSettingsEvent.ExportPassphraseChanged(it)) },
                label = stringResource(R.string.mailfox_pgp_export_passphrase_label),
                hasError = dialog.error == PgpExportDialogError.TOO_SHORT,
                modifier = Modifier.fillMaxWidth(),
            )
            TextFieldOutlinedPassword(
                value = dialog.confirmation,
                onValueChange = { onEvent(PgpSettingsEvent.ExportConfirmationChanged(it)) },
                label = stringResource(R.string.mailfox_pgp_export_confirmation_label),
                hasError = dialog.error == PgpExportDialogError.MISMATCH,
                modifier = Modifier.fillMaxWidth(),
            )
            when (dialog.error) {
                PgpExportDialogError.TOO_SHORT -> stringResource(R.string.mailfox_pgp_export_error_too_short)
                PgpExportDialogError.MISMATCH -> stringResource(R.string.mailfox_pgp_export_error_mismatch)
                null -> null
            }?.let { TextBodySmall(text = it, color = BoltTheme.colors.error) }
        }
    }
}

@Composable
private fun exportStatusText(status: PgpExportStatus): String? = when (status) {
    PgpExportStatus.None -> null
    PgpExportStatus.InProgress -> stringResource(R.string.mailfox_pgp_export_status_in_progress)
    PgpExportStatus.Done -> stringResource(R.string.mailfox_pgp_export_status_done)
    PgpExportStatus.Failed -> stringResource(R.string.mailfox_pgp_export_status_failed)
}

@Composable
private fun statusText(account: PgpAccountItem): String? = when (account.status) {
    PgpSetupStatus.Idle -> account.fingerprint?.let { stringResource(R.string.mailfox_pgp_settings_status_active) }

    PgpSetupStatus.InProgress -> stringResource(R.string.mailfox_pgp_settings_status_in_progress)

    PgpSetupStatus.Published -> stringResource(R.string.mailfox_pgp_settings_status_published)

    PgpSetupStatus.VerificationPending -> stringResource(
        R.string.mailfox_pgp_settings_status_verification_pending,
        account.emailAddress,
    )

    PgpSetupStatus.PublishFailed -> stringResource(R.string.mailfox_pgp_settings_status_publish_failed)

    PgpSetupStatus.Failed -> stringResource(R.string.mailfox_pgp_settings_status_failed)
}

private fun formatFingerprint(fingerprint: String): String = fingerprint.chunked(
    FINGERPRINT_GROUP_SIZE,
).joinToString(" ")

private const val FINGERPRINT_GROUP_SIZE = 4
private const val PROGRESS_SIZE = 24
private const val EXPORT_MIME_TYPE = "application/pgp-keys"
