package net.thunderbird.feature.mailfox.pgp.internal.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.thunderbird.feature.mailfox.pgp.internal.PgpAccountSetup
import net.thunderbird.feature.mailfox.pgp.internal.PgpImportResult
import net.thunderbird.feature.mailfox.pgp.internal.PgpKeyExporter
import net.thunderbird.feature.mailfox.pgp.internal.PgpSetupResult
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.PublishStatus

internal data class PgpSettingsState(
    val isLoading: Boolean = true,
    val accounts: List<PgpAccountItem> = emptyList(),
    val exportDialog: PgpExportDialogState? = null,
    val importDialog: PgpImportDialogState? = null,
)

internal data class PgpAccountItem(
    val accountUuid: String,
    val displayName: String,
    val emailAddress: String,
    val fingerprint: String?,
    val status: PgpSetupStatus = PgpSetupStatus.Idle,
    val exportStatus: PgpExportStatus = PgpExportStatus.None,
    val importStatus: PgpImportStatus = PgpImportStatus.None,
)

internal data class PgpImportDialogState(
    val accountUuid: String,
    val passphrase: String = "",
    val isWrongPassphrase: Boolean = false,
)

internal enum class PgpImportStatus {
    None,
    InProgress,
    Done,
    NoSecretKey,
    WrongEmailAddress,
    KeyNotUsable,
    Failed,
}

internal data class PgpExportDialogState(
    val accountUuid: String,
    val passphrase: String = "",
    val confirmation: String = "",
    val error: PgpExportDialogError? = null,
)

internal enum class PgpExportDialogError {
    TOO_SHORT,
    MISMATCH,
}

internal sealed interface PgpSetupStatus {
    data object Idle : PgpSetupStatus
    data object InProgress : PgpSetupStatus
    data object Published : PgpSetupStatus
    data object VerificationPending : PgpSetupStatus
    data object PublishFailed : PgpSetupStatus
    data object Failed : PgpSetupStatus
}

internal enum class PgpExportStatus {
    None,
    InProgress,
    Done,
    Failed,
}

internal sealed interface PgpSettingsEvent {
    data class SetUpClicked(val accountUuid: String) : PgpSettingsEvent
    data class ExportClicked(val accountUuid: String) : PgpSettingsEvent
    data class ExportPassphraseChanged(val passphrase: String) : PgpSettingsEvent
    data class ExportConfirmationChanged(val confirmation: String) : PgpSettingsEvent
    data object ExportConfirmed : PgpSettingsEvent
    data object ExportDialogDismissed : PgpSettingsEvent
    data class ExportFileSelected(val uri: Uri?) : PgpSettingsEvent
    data class ImportClicked(val accountUuid: String) : PgpSettingsEvent
    data class ImportFileSelected(val uri: Uri?) : PgpSettingsEvent
    data class ImportPassphraseChanged(val passphrase: String) : PgpSettingsEvent
    data object ImportConfirmed : PgpSettingsEvent
    data object ImportDialogDismissed : PgpSettingsEvent
}

internal sealed interface PgpSettingsEffect {
    /** Ask the user where to save the exported key. */
    data class CreateExportFile(val fileName: String) : PgpSettingsEffect

    /** Ask the user for the key file to import. */
    data object OpenImportFile : PgpSettingsEffect
}

@Suppress("TooManyFunctions")
internal class PgpSettingsViewModel(
    private val accountSetup: PgpAccountSetup,
    private val keyExporter: PgpKeyExporter,
    private val keyFiles: PgpKeyFiles,
) : ViewModel() {
    private val _state = MutableStateFlow(PgpSettingsState())
    val state: StateFlow<PgpSettingsState> = _state.asStateFlow()

    private val _effects = Channel<PgpSettingsEffect>(Channel.BUFFERED)
    val effects: Flow<PgpSettingsEffect> = _effects.receiveAsFlow()

    /** Account and passphrase of an export waiting for the user to pick a file. */
    private var pendingExport: Pair<String, CharArray>? = null

    /** Account for which the user is picking a key file. */
    private var pendingImportAccountUuid: String? = null

    /** Key file waiting for its passphrase. */
    private var pendingImport: Pair<String, ByteArray>? = null

    init {
        viewModelScope.launch {
            val accounts = accountSetup.getAccounts().map { account ->
                PgpAccountItem(
                    accountUuid = account.accountUuid,
                    displayName = account.displayName,
                    emailAddress = account.emailAddress,
                    fingerprint = account.fingerprint,
                )
            }
            _state.update { it.copy(isLoading = false, accounts = accounts) }
        }
    }

    fun event(event: PgpSettingsEvent) {
        when (event) {
            is PgpSettingsEvent.SetUpClicked -> setUp(event.accountUuid)

            is PgpSettingsEvent.ExportClicked -> _state.update {
                it.copy(exportDialog = PgpExportDialogState(accountUuid = event.accountUuid))
            }

            is PgpSettingsEvent.ExportPassphraseChanged -> updateDialog { it.copy(passphrase = event.passphrase) }

            is PgpSettingsEvent.ExportConfirmationChanged -> updateDialog { it.copy(confirmation = event.confirmation) }

            PgpSettingsEvent.ExportConfirmed -> confirmExport()

            PgpSettingsEvent.ExportDialogDismissed -> _state.update { it.copy(exportDialog = null) }

            is PgpSettingsEvent.ExportFileSelected -> exportToFile(event.uri)

            is PgpSettingsEvent.ImportClicked -> {
                pendingImportAccountUuid = event.accountUuid
                _effects.trySend(PgpSettingsEffect.OpenImportFile)
            }

            is PgpSettingsEvent.ImportFileSelected -> importFromFile(event.uri)

            is PgpSettingsEvent.ImportPassphraseChanged -> _state.update { state ->
                state.copy(
                    importDialog = state.importDialog?.copy(passphrase = event.passphrase, isWrongPassphrase = false),
                )
            }

            PgpSettingsEvent.ImportConfirmed -> confirmImport()

            PgpSettingsEvent.ImportDialogDismissed -> {
                pendingImport = null
                _state.update { it.copy(importDialog = null) }
            }
        }
    }

    private fun setUp(accountUuid: String) {
        updateAccount(accountUuid) { it.copy(status = PgpSetupStatus.InProgress) }

        viewModelScope.launch {
            when (val result = accountSetup.setUp(accountUuid)) {
                is PgpSetupResult.Success -> updateAccount(accountUuid) {
                    it.copy(
                        fingerprint = result.fingerprint,
                        status = when (result.publishStatus) {
                            PublishStatus.PUBLISHED -> PgpSetupStatus.Published
                            PublishStatus.VERIFICATION_PENDING -> PgpSetupStatus.VerificationPending
                            null -> PgpSetupStatus.PublishFailed
                        },
                    )
                }

                PgpSetupResult.Error -> updateAccount(accountUuid) { it.copy(status = PgpSetupStatus.Failed) }
            }
        }
    }

    private fun confirmExport() {
        val dialog = _state.value.exportDialog ?: return
        val error = when {
            dialog.passphrase.length < PgpKeyExporter.MIN_PASSPHRASE_LENGTH -> PgpExportDialogError.TOO_SHORT
            dialog.passphrase != dialog.confirmation -> PgpExportDialogError.MISMATCH
            else -> null
        }
        val account = _state.value.accounts.firstOrNull { it.accountUuid == dialog.accountUuid }

        if (error != null) {
            _state.update { state -> state.copy(exportDialog = state.exportDialog?.copy(error = error)) }
        } else if (account != null) {
            clearPendingExport()
            pendingExport = dialog.accountUuid to dialog.passphrase.toCharArray()
            _state.update { it.copy(exportDialog = null) }
            _effects.trySend(PgpSettingsEffect.CreateExportFile(exportFileName(account.emailAddress)))
        }
    }

    private fun exportToFile(uri: Uri?) {
        val (accountUuid, passphrase) = pendingExport ?: return
        pendingExport = null
        if (uri == null) {
            passphrase.fill(' ')
            return
        }

        updateAccount(accountUuid) { it.copy(exportStatus = PgpExportStatus.InProgress) }
        viewModelScope.launch {
            val result = runCatching {
                keyExporter.export(accountUuid, passphrase) { keyFiles.openOutputStream(uri) }
            }
            passphrase.fill(' ')
            val status = if (result.isSuccess) PgpExportStatus.Done else PgpExportStatus.Failed
            updateAccount(accountUuid) { it.copy(exportStatus = status) }
        }
    }

    private fun importFromFile(uri: Uri?) {
        val accountUuid = pendingImportAccountUuid ?: return
        pendingImportAccountUuid = null
        if (uri == null) return

        updateAccount(accountUuid) { it.copy(importStatus = PgpImportStatus.InProgress) }
        viewModelScope.launch {
            val keyData = runCatching { keyFiles.readBytes(uri) }.getOrNull()
            if (keyData == null) {
                updateAccount(accountUuid) { it.copy(importStatus = PgpImportStatus.Failed) }
            } else {
                runImport(accountUuid, keyData, passphrase = null)
            }
        }
    }

    private fun confirmImport() {
        val (accountUuid, keyData) = pendingImport ?: return
        val passphrase = _state.value.importDialog?.passphrase?.toCharArray() ?: return

        viewModelScope.launch {
            runImport(accountUuid, keyData, passphrase)
            passphrase.fill(' ')
        }
    }

    private suspend fun runImport(accountUuid: String, keyData: ByteArray, passphrase: CharArray?) {
        val result = runCatching { accountSetup.importKey(accountUuid, keyData, passphrase) }.getOrNull()
        when (result) {
            PgpImportResult.PassphraseRequired -> {
                pendingImport = accountUuid to keyData
                updateAccount(accountUuid) { it.copy(importStatus = PgpImportStatus.None) }
                _state.update { it.copy(importDialog = PgpImportDialogState(accountUuid)) }
            }

            PgpImportResult.WrongPassphrase -> _state.update { state ->
                state.copy(importDialog = state.importDialog?.copy(isWrongPassphrase = true))
            }

            else -> {
                pendingImport = null
                _state.update { it.copy(importDialog = null) }
                updateAccount(accountUuid) { account -> account.withImportResult(result) }
            }
        }
    }

    private fun PgpAccountItem.withImportResult(result: PgpImportResult?): PgpAccountItem = when (result) {
        is PgpImportResult.Success -> copy(
            fingerprint = result.fingerprint,
            status = PgpSetupStatus.Idle,
            importStatus = PgpImportStatus.Done,
        )

        PgpImportResult.NoSecretKey -> copy(importStatus = PgpImportStatus.NoSecretKey)

        PgpImportResult.WrongEmailAddress -> copy(importStatus = PgpImportStatus.WrongEmailAddress)

        PgpImportResult.KeyNotUsable -> copy(importStatus = PgpImportStatus.KeyNotUsable)

        else -> copy(importStatus = PgpImportStatus.Failed)
    }

    private fun clearPendingExport() {
        pendingExport?.second?.fill(' ')
        pendingExport = null
    }

    override fun onCleared() {
        clearPendingExport()
    }

    /** Updates the input fields of the export dialog and clears an error message. */
    private fun updateDialog(block: (PgpExportDialogState) -> PgpExportDialogState) {
        _state.update { state -> state.copy(exportDialog = state.exportDialog?.let { block(it).copy(error = null) }) }
    }

    private fun updateAccount(accountUuid: String, block: (PgpAccountItem) -> PgpAccountItem) {
        _state.update { state ->
            state.copy(accounts = state.accounts.map { if (it.accountUuid == accountUuid) block(it) else it })
        }
    }

    private fun exportFileName(emailAddress: String): String {
        val safeAddress = emailAddress.replace(Regex("[^A-Za-z0-9@._-]"), "_")
        return "mailfox-$safeAddress-private-key.asc"
    }
}
