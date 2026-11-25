package net.thunderbird.feature.mailfox.pgp.internal.settings

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.card.CardOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
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
        }
    }
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
