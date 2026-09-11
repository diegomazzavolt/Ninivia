package com.example.data

import androidx.room.withTransaction
import com.example.crypto.CryptoManager
import com.example.crypto.TextCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class NoteRepository(private val db: AppDatabase, private val cipher: TextCipher = CryptoManager) {
    private val dao = db.noteDao()
    val allNotes = dao.getAllNotes().map { notes -> notes.map { decode(it) } }.flowOn(Dispatchers.IO)
    val allCategories = dao.getAllCategories()
    suspend fun getNoteById(id: String) = withContext(Dispatchers.IO) { dao.getNoteById(id)?.let(::decode) }
    suspend fun saveNote(note: NoteModel) = withContext(Dispatchers.IO) {
        require(note.readable) { "Não é possível sobrescrever uma nota que não pôde ser aberta." }
        require(note.title.isNotBlank() || note.content.isNotBlank()) { "Escreva um título ou uma nota." }
        require(note.title.length <= 500 && note.content.length <= 100_000) { "A nota excede o tamanho permitido." }
        require(note.priority in 0..3)
        require(note.repeat == RepeatRule.NONE || (note.dueDay != null && note.actionable)) { "Uma ação recorrente precisa de uma data." }
        db.withTransaction { write(note.copy(updatedAt = System.currentTimeMillis())) }
    }
    private suspend fun write(note: NoteModel) {
        dao.insertNote(NoteEntity(
            id = note.id, categoryId = note.category?.id,
            encryptedTitle = cipher.encrypt(note.title), encryptedContent = cipher.encrypt(note.content),
            tags = note.tags.joinToString(","), createdAt = note.createdAt, updatedAt = note.updatedAt,
            status = note.status.name, dueDay = note.dueDay, priority = note.priority,
            context = note.context, waitingFor = note.waitingFor,
            encryptedChecklist = cipher.encrypt(NoteJson.checklist(note.checklist).toString()),
            completedAt = note.completedAt, deletedAt = note.deletedAt, repeat = note.repeat.name
        ))
        dao.deleteAttachmentsByNoteId(note.id)
        dao.insertAttachments(note.attachments.map { it.copy(noteId = note.id) })
    }
    suspend fun saveCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        require(category.name.isNotBlank() && category.name.length <= 100) { "Informe um nome de projeto com até 100 caracteres." }
        dao.insertCategory(category.copy(name = category.name.trim()))
    }
    suspend fun trash(id: String, restore: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.trash(id, if (restore) null else now, now)
    }
    suspend fun deletePermanently(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            check(dao.getNoteById(id)?.note?.deletedAt != null) { "Mova a nota para a lixeira primeiro." }
            dao.deleteAttachmentsByNoteId(id)
            dao.deleteNote(id)
        }
    }
    suspend fun toggleComplete(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val note = dao.getNoteById(id)?.let(::decode) ?: return@withTransaction
            check(note.readable && note.deletedAt == null)
            val now = System.currentTimeMillis()
            val completing = note.completedAt == null
            write(note.copy(completedAt = if (completing) now else null, updatedAt = now))
            // A deterministic occurrence ID makes complete/reopen/complete idempotent.
            if (completing && note.repeat != RepeatRule.NONE && note.dueDay != null) {
                val nextDay = GtdRules.nextDue(maxOf(note.dueDay, LocalDate.now().toEpochDay()), note.repeat)
                val nextId = UUID.nameUUIDFromBytes(("repeat:" + note.id).toByteArray()).toString()
                if (dao.getNoteById(nextId) == null) {
                    write(note.copy(id = nextId, dueDay = nextDay, completedAt = null, createdAt = now, updatedAt = now,
                        attachments = note.attachments.map { it.copy(id = UUID.randomUUID().toString(), noteId = nextId) },
                        checklist = note.checklist.map { it.copy(done = false) }))
                }
            }
        }
    }
    suspend fun exportSnapshot(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val notes = dao.snapshot().map(::decode)
            check(notes.all { it.readable }) { "Há notas sem chave de leitura. O backup foi interrompido para evitar perda de conteúdo." }
            NoteJson.backup(notes, dao.categorySnapshot()).toString()
        }
    }
    suspend fun importSnapshot(json: String): Int = withContext(Dispatchers.IO) {
        val (notes, projects) = NoteJson.readBackup(json)
        db.withTransaction {
            // Import as copies, never overwrite the current database.
            val projectIds = projects.associate { it.id to UUID.randomUUID().toString() }
            val newProjects = projects.map { it.copy(id = projectIds.getValue(it.id)) }
            newProjects.forEach { dao.insertCategory(it) }
            notes.forEach { note ->
                val project = note.category?.id?.let { old -> newProjects.find { it.id == projectIds[old] } }
                write(note.copy(id = UUID.randomUUID().toString(), category = project, attachments = emptyList()))
            }
        }
        notes.size
    }
    private fun decode(details: NoteWithDetails): NoteModel {
        val n = details.note
        return try {
            NoteModel(id = n.id, title = cipher.decrypt(n.encryptedTitle), content = cipher.decrypt(n.encryptedContent),
                category = details.category, tags = n.tags.split(",").filter { it.isNotBlank() },
                attachments = details.attachments, createdAt = n.createdAt, updatedAt = n.updatedAt,
                status = GtdStatus.valueOf(n.status), dueDay = n.dueDay, priority = n.priority,
                context = n.context, waitingFor = n.waitingFor,
                checklist = NoteJson.readChecklist(cipher.decrypt(n.encryptedChecklist)),
                completedAt = n.completedAt, deletedAt = n.deletedAt, repeat = RepeatRule.valueOf(n.repeat))
        } catch (_: Exception) {
            NoteModel(id = n.id, title = "Nota indisponível", content = "Não foi possível decifrar esta nota. O conteúdo original foi preservado.",
                category = details.category, createdAt = n.createdAt, updatedAt = n.updatedAt,
                deletedAt = n.deletedAt, completedAt = n.completedAt, readable = false)
        }
    }
}
