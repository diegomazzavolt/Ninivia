package com.example.data

import com.example.crypto.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val noteDao: NoteDao) {
    
    val allNotes: Flow<List<NoteModel>> = noteDao.getAllNotes().map { list ->
        list.map { it.toModel() }
    }
    
    val allCategories: Flow<List<CategoryEntity>> = noteDao.getAllCategories()
    
    fun getNoteById(id: String): Flow<NoteModel?> {
        return noteDao.getNoteById(id).map { it?.toModel() }
    }
    
    suspend fun saveNote(note: NoteModel) {
        val entity = NoteEntity(
            id = note.id,
            categoryId = note.category?.id,
            encryptedTitle = CryptoManager.encrypt(note.title),
            encryptedContent = CryptoManager.encrypt(note.content),
            tags = note.tags.joinToString(","),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertNote(entity)
        noteDao.deleteAttachmentsByNoteId(note.id)
        if (note.attachments.isNotEmpty()) {
            noteDao.insertAttachments(note.attachments)
        }
    }
    
    suspend fun saveCategory(category: CategoryEntity) {
        noteDao.insertCategory(category)
    }
    
    suspend fun deleteNote(id: String) {
        noteDao.deleteNote(id)
    }
    
    private fun NoteWithDetails.toModel(): NoteModel {
        val decryptedTitle = try {
            CryptoManager.decrypt(note.encryptedTitle)
        } catch (e: Exception) {
            "Decryption Error"
        }
        val decryptedContent = try {
            CryptoManager.decrypt(note.encryptedContent)
        } catch (e: Exception) {
            "Decryption Error"
        }
        
        return NoteModel(
            id = note.id,
            title = decryptedTitle,
            content = decryptedContent,
            category = category,
            tags = if (note.tags.isBlank()) emptyList() else note.tags.split(","),
            attachments = attachments,
            updatedAt = note.updatedAt
        )
    }
}
