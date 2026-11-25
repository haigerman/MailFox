package net.thunderbird.feature.mailfox.pgp.internal.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.thunderbird.feature.mailfox.pgp.internal.PgpAccountSetup
import net.thunderbird.feature.mailfox.pgp.internal.PgpSetupResult
import net.thunderbird.feature.mailfox.pgp.internal.keyserver.PublishStatus

internal data class PgpSettingsState(
    val isLoading: Boolean = true,
    val accounts: List<PgpAccountItem> = emptyList(),
)

internal data class PgpAccountItem(
    val accountUuid: String,
    val displayName: String,
    val emailAddress: String,
    val fingerprint: String?,
    val status: PgpSetupStatus = PgpSetupStatus.Idle,
)

internal sealed interface PgpSetupStatus {
    data object Idle : PgpSetupStatus
    data object InProgress : PgpSetupStatus
    data object Published : PgpSetupStatus
    data object VerificationPending : PgpSetupStatus
    data object PublishFailed : PgpSetupStatus
    data object Failed : PgpSetupStatus
}

internal sealed interface PgpSettingsEvent {
    data class SetUpClicked(val accountUuid: String) : PgpSettingsEvent
}

internal class PgpSettingsViewModel(
    private val accountSetup: PgpAccountSetup,
) : ViewModel() {
    private val _state = MutableStateFlow(PgpSettingsState())
    val state: StateFlow<PgpSettingsState> = _state.asStateFlow()

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
            _state.value = PgpSettingsState(isLoading = false, accounts = accounts)
        }
    }

    fun event(event: PgpSettingsEvent) {
        when (event) {
            is PgpSettingsEvent.SetUpClicked -> setUp(event.accountUuid)
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

    private fun updateAccount(accountUuid: String, block: (PgpAccountItem) -> PgpAccountItem) {
        _state.update { state ->
            state.copy(accounts = state.accounts.map { if (it.accountUuid == accountUuid) block(it) else it })
        }
    }
}
