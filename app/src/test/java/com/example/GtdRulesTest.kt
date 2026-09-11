package com.example

import com.example.data.*
import com.example.data.Collection
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GtdRulesTest {
    private val today = LocalDate.of(2026, 9, 11).toEpochDay()
    private val task = NoteModel(title = "Enviar proposta", status = GtdStatus.NEXT, dueDay = today)
    @Test fun todayIncludesDueAndOverdueActions() {
        assertTrue(GtdRules.inCollection(task, Collection.TODAY, today))
        assertTrue(GtdRules.inCollection(task.copy(dueDay = today - 1), Collection.TODAY, today))
        assertFalse(GtdRules.inCollection(task.copy(dueDay = today + 1), Collection.TODAY, today))
        assertFalse(GtdRules.inCollection(task.copy(dueDay = null), Collection.TODAY, today))
    }
    @Test fun referenceSomedayAndInboxAreNotTodayCommitments() {
        listOf(GtdStatus.REFERENCE, GtdStatus.SOMEDAY, GtdStatus.INBOX).forEach {
            assertFalse(GtdRules.inCollection(task.copy(status = it), Collection.TODAY, today))
        }
        assertTrue(GtdRules.inCollection(task.copy(status = GtdStatus.WAITING), Collection.TODAY, today))
    }
    @Test fun completedAndTrashedNeverLeakIntoActiveLists() {
        listOf(Collection.INBOX, Collection.TODAY, Collection.NEXT, Collection.PROJECTS, Collection.REVIEW).forEach {
            assertFalse(GtdRules.inCollection(task.copy(completedAt = 1), it, today))
            assertFalse(GtdRules.inCollection(task.copy(deletedAt = 1), it, today))
        }
        assertTrue(GtdRules.inCollection(task.copy(completedAt = 1), Collection.COMPLETED, today))
        assertFalse(GtdRules.inCollection(task.copy(completedAt = 1, deletedAt = 1), Collection.COMPLETED, today))
        assertTrue(GtdRules.inCollection(task.copy(deletedAt = 1), Collection.TRASH, today))
    }
    @Test fun searchMatchesEveryWordAcrossFields() {
        val n = task.copy(context = "computador", tags = listOf("Cliente"))
        assertTrue(GtdRules.search(n, "cliente ENVIAR"))
        assertTrue(GtdRules.search(n, "   "))
        assertFalse(GtdRules.search(n, "cliente orçamento"))
    }
    @Test fun priorityThenDateThenRecentSort() {
        val urgent = task.copy(id = "urgent", priority = 3, dueDay = today + 10)
        val late = task.copy(id = "late", dueDay = today - 1)
        val later = task.copy(id = "later", dueDay = today + 1)
        assertEquals(listOf("urgent", "late", "later"), listOf(later, late, urgent).sortedWith(GtdRules.ordering).map { it.id })
    }
    @Test fun recurrenceHandlesMonthEndAndLeapYears() {
        val jan = LocalDate.of(2024, 1, 31).toEpochDay()
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), GtdRules.nextDue(jan, RepeatRule.MONTHLY))
        assertEquals(today + 7, GtdRules.nextDue(today, RepeatRule.WEEKLY))
        assertEquals(today + 1, GtdRules.nextDue(today, RepeatRule.DAILY))
    }
}
