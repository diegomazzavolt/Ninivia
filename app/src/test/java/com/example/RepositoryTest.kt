package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.TextCipher
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

object TestCipher : TextCipher {
    override fun encrypt(data: String) = "encrypted:" + data
    override fun decrypt(data: String): String {
        if (data.isBlank()) return data
        require(data.startsWith("encrypted:"))
        return data.removePrefix("encrypted:")
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: NoteRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = NoteRepository(db, TestCipher)
    }
    @After fun close() { db.close() }
    @Test fun savePreservesCreationTimeAndReplacesAttachmentsAtomically() = runTest {
        val n = NoteModel(title = "Primeira", createdAt = 123, attachments = listOf(AttachmentEntity(noteId = "", uri = "content://a", type = "text/plain", name = "arquivo")))
        repo.saveNote(n)
        repo.saveNote(n.copy(title = "Segunda", attachments = emptyList()))
        val saved = repo.getNoteById(n.id)!!
        assertEquals(123, saved.createdAt)
        assertEquals("Segunda", saved.title)
        assertTrue(saved.attachments.isEmpty())
        assertTrue(db.noteDao().getNoteById(n.id)!!.note.encryptedTitle.startsWith("encrypted:"))
    }
    @Test fun trashRestoreAndPermanentDeleteCleanAttachments() = runTest {
        val n = NoteModel(title = "Teste", attachments = listOf(AttachmentEntity(noteId = "", uri = "content://a", type = "text/plain", name = "anexo")))
        repo.saveNote(n)
        try { repo.deletePermanently(n.id); fail("Must require trash") } catch (_: IllegalStateException) {}
        repo.trash(n.id)
        assertNotNull(repo.getNoteById(n.id)!!.deletedAt)
        repo.trash(n.id, true)
        assertNull(repo.getNoteById(n.id)!!.deletedAt)
        repo.trash(n.id)
        repo.deletePermanently(n.id)
        assertNull(repo.getNoteById(n.id))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM attachments").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
    @Test fun repeatedCompletionCreatesOnlyOneNextOccurrence() = runTest {
        val n = NoteModel(title = "Revisar", status = GtdStatus.NEXT, dueDay = LocalDate.now().toEpochDay(), repeat = RepeatRule.WEEKLY,
            checklist = listOf(ChecklistItem(text = "Olhar agenda", done = true)))
        repo.saveNote(n)
        repo.toggleComplete(n.id)
        repo.toggleComplete(n.id)
        repo.toggleComplete(n.id)
        val all = repo.allNotes.first()
        assertEquals(2, all.size)
        assertEquals(n.dueDay!! + 7, all.single { it.id != n.id }.dueDay)
        assertFalse(all.single { it.id != n.id }.checklist.single().done)
    }
    @Test fun unreadableNoteIsPreservedAndCannotBeOverwritten() = runTest {
        val n = NoteEntity(categoryId = null, encryptedTitle = "bad data", encryptedContent = "bad data", tags = "")
        db.noteDao().insertNote(n)
        val unreadable = repo.getNoteById(n.id)!!
        assertFalse(unreadable.readable)
        try { repo.saveNote(unreadable); fail("Must prevent overwrite") } catch (_: IllegalArgumentException) {}
        assertEquals("bad data", db.noteDao().getNoteById(n.id)!!.note.encryptedContent)
        try { repo.exportSnapshot(); fail("Incomplete backup must not succeed") } catch (_: IllegalStateException) {}
    }
    @Test fun backupImportCreatesCopiesWithRemappedProjects() = runTest {
        val project = CategoryEntity(name = "Reformar escritório", outcome = "Sala pronta")
        repo.saveCategory(project)
        val n = NoteModel(title = "Medir", category = project, status = GtdStatus.NEXT, tags = listOf("casa"), checklist = listOf(ChecklistItem(text = "Parede")))
        repo.saveNote(n)
        val json = repo.exportSnapshot()
        assertEquals(1, repo.importSnapshot(json))
        val all = repo.allNotes.first()
        assertEquals(2, all.size)
        val copy = all.single { it.id != n.id }
        assertNotEquals(project.id, copy.category!!.id)
        assertEquals(project.name, copy.category.name)
        assertEquals(n.checklist, copy.checklist)
    }
    @Test fun malformedImportLeavesExistingDataUntouched() = runTest {
        val n = NoteModel(title = "Manter")
        repo.saveNote(n)
        val json = repo.exportSnapshot().replace("\"version\":1", "\"version\":999")
        try { repo.importSnapshot(json); fail("Must reject unknown version") } catch (_: IllegalArgumentException) {}
        assertEquals(1, repo.allNotes.first().size)
    }
    @Test fun checklistRoundTripAndValidation() {
        val list = listOf(ChecklistItem(text = "a\nb \"c\" 🌿", done = true))
        assertEquals(list, NoteJson.readChecklist(NoteJson.checklist(list).toString()))
        assertThrows(IllegalArgumentException::class.java) {
            NoteJson.readChecklist(NoteJson.checklist(List(201) { ChecklistItem(text = "Passo") }).toString())
        }
    }
}
