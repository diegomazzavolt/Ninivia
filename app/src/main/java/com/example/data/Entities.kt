package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int = 0xFF376957.toInt(),
    @ColumnInfo(defaultValue = "''") val outcome: String = "",
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String?,
    val encryptedTitle: String,
    val encryptedContent: String,
    val tags: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    @ColumnInfo(defaultValue = "'INBOX'") val status: String = "INBOX",
    @ColumnInfo(defaultValue = "NULL") val dueDay: Long? = null,
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    @ColumnInfo(defaultValue = "''") val context: String = "",
    @ColumnInfo(defaultValue = "''") val waitingFor: String = "",
    @ColumnInfo(defaultValue = "''") val encryptedChecklist: String = "",
    @ColumnInfo(defaultValue = "NULL") val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "'NONE'") val repeat: String = "NONE"
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val uri: String,
    val type: String,
    val name: String
)
