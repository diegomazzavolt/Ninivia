package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Transaction @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteWithDetails>>
    @Transaction @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteWithDetails?
    @Transaction @Query("SELECT * FROM notes")
    suspend fun snapshot(): List<NoteWithDetails>
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories")
    suspend fun categorySnapshot(): List<CategoryEntity>
    @Upsert suspend fun insertNote(note: NoteEntity)
    @Upsert suspend fun insertCategory(category: CategoryEntity)
    @Upsert suspend fun insertAttachments(attachments: List<AttachmentEntity>)
    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteAttachmentsByNoteId(noteId: String)
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)
    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :now WHERE id = :id")
    suspend fun trash(id: String, deletedAt: Long?, now: Long)
}
