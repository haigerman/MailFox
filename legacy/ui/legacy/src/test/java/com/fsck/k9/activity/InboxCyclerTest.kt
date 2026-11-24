package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class InboxCyclerTest {
    private val testSubject = InboxCycler

    @Test
    fun `entries should start with unified inbox when it is enabled`() {
        val entries = testSubject.entries(UNIFIED, showUnifiedInbox = true, accountUuids = listOf("a", "b"))

        assertThat(entries).containsExactly(UNIFIED, "a", "b")
    }

    @Test
    fun `entries should only contain accounts when unified inbox is disabled`() {
        val entries = testSubject.entries(UNIFIED, showUnifiedInbox = false, accountUuids = listOf("a", "b"))

        assertThat(entries).containsExactly("a", "b")
    }

    @Test
    fun `next should go from unified inbox to first account and on to the following accounts`() {
        val entries = listOf(UNIFIED, "a", "b")

        assertThat(testSubject.next(entries, UNIFIED)).isEqualTo("a")
        assertThat(testSubject.next(entries, "a")).isEqualTo("b")
    }

    @Test
    fun `next should wrap around after the last entry`() {
        val entries = listOf(UNIFIED, "a", "b")

        assertThat(testSubject.next(entries, "b")).isEqualTo(UNIFIED)
    }

    @Test
    fun `next should return first entry when current entry is unknown`() {
        val entries = listOf("a", "b")

        assertThat(testSubject.next(entries, "removed")).isEqualTo("a")
    }

    @Test
    fun `next should return null when there is only the current entry`() {
        assertThat(testSubject.next(listOf("a"), "a")).isNull()
    }

    @Test
    fun `next should return null when there are no entries`() {
        assertThat(testSubject.next(emptyList(), null)).isNull()
    }

    private companion object {
        const val UNIFIED = "unified"
    }
}
