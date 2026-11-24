package com.fsck.k9.activity

/**
 * MailFox: Determines which inbox is shown next when the user taps the title in the message list toolbar.
 *
 * The order matches the account list in the navigation drawer: the Unified Inbox first (if it is enabled), followed by
 * the inbox of every account. After the last entry it starts over at the beginning.
 */
internal object InboxCycler {

    /**
     * Builds the list of entries to cycle through.
     *
     * @param unifiedEntryId ID used for the Unified Inbox entry.
     * @param showUnifiedInbox Whether the Unified Inbox is enabled in the settings.
     * @param accountUuids UUIDs of all accounts, in the order they are displayed in the navigation drawer.
     */
    fun entries(unifiedEntryId: String, showUnifiedInbox: Boolean, accountUuids: List<String>): List<String> {
        return if (showUnifiedInbox) listOf(unifiedEntryId) + accountUuids else accountUuids
    }

    /**
     * Returns the entry following [current], or `null` if there is nothing to switch to.
     *
     * If [current] is not part of [entries] (e.g. it is `null`), the first entry is returned.
     */
    fun next(entries: List<String>, current: String?): String? {
        if (entries.isEmpty()) return null

        val currentIndex = entries.indexOf(current)
        val nextEntry = entries[(currentIndex + 1) % entries.size]

        return nextEntry.takeIf { it != current }
    }
}
