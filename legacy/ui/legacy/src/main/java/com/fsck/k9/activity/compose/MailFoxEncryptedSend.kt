package com.fsck.k9.activity.compose

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.fsck.k9.mail.Address
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.thunderbird.feature.mailfox.pgp.MailFoxPgp
import net.thunderbird.feature.mailfox.pgp.PgpKeyLookupResult
import net.thunderbird.feature.mailfox.pgp.PgpRecipientKey

/**
 * MailFox: Keys to encrypt a message with.
 *
 * @param recipients Keys of the To and Cc recipients.
 * @param hiddenRecipients Keys of the Bcc recipients. Their key IDs are not disclosed in the encrypted message.
 */
class MailFoxEncryptionRequest(
    val recipients: List<PgpRecipientKey>,
    val hiddenRecipients: List<PgpRecipientKey>,
)

/**
 * MailFox: "Send encrypted" in the compose screen.
 *
 * Looks up the newest key of every recipient. If all keys were found, the message is sent encrypted. Otherwise the
 * user decides whether to send the message unencrypted or to go back to the editor.
 */
class MailFoxEncryptedSend(
    private val activity: FragmentActivity,
    private val mailFoxPgp: MailFoxPgp,
    private val callback: Callback,
) {
    private var job: Job? = null
    private var dialog: AlertDialog? = null

    fun start(to: List<Address>, cc: List<Address>, bcc: List<Address>) {
        if (job?.isActive == true) return

        val visibleAddresses = (to + cc).map { it.address }.distinctBy { it.lowercase() }
        val hiddenAddresses = bcc.map { it.address }
            .filter { address -> visibleAddresses.none { it.equals(address, ignoreCase = true) } }
            .distinctBy { it.lowercase() }

        showProgress()
        job = activity.lifecycleScope.launch {
            val results = mailFoxPgp.lookupRecipientKeys(visibleAddresses + hiddenAddresses)
            dismissDialog()

            val missing = results.filterValues { it !is PgpKeyLookupResult.Found }
            if (missing.isEmpty()) {
                callback.onSendEncrypted(
                    MailFoxEncryptionRequest(
                        recipients = visibleAddresses.map { results.foundKey(it) },
                        hiddenRecipients = hiddenAddresses.map { results.foundKey(it) },
                    ),
                )
            } else {
                showMissingKeys(missing)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        dismissDialog()
    }

    private fun showProgress() {
        dialog = MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.mailfox_pgp_searching_keys)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancel() }
            .setOnCancelListener { cancel() }
            .show()
    }

    private fun showMissingKeys(missing: Map<String, PgpKeyLookupResult>) {
        val addressList = missing.entries.joinToString(separator = "\n") { (address, result) ->
            val line = if (result is PgpKeyLookupResult.Failed) {
                activity.getString(R.string.mailfox_pgp_lookup_failed, address)
            } else {
                address
            }
            "• $line"
        }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.mailfox_pgp_missing_keys_title)
            .setMessage(activity.getString(R.string.mailfox_pgp_missing_keys_message, addressList))
            .setPositiveButton(R.string.mailfox_pgp_send_unencrypted) { _, _ -> callback.onSendUnencrypted() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dismissDialog() {
        dialog?.dismiss()
        dialog = null
    }

    private fun Map<String, PgpKeyLookupResult>.foundKey(address: String): PgpRecipientKey {
        return (getValue(address) as PgpKeyLookupResult.Found).key
    }

    interface Callback {
        fun onSendEncrypted(request: MailFoxEncryptionRequest)
        fun onSendUnencrypted()
    }
}
