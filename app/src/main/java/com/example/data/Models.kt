package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithDetails(
    @Embedded val note: NoteEntity,
    
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity?,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val attachments: List<AttachmentEntity>
)

data class NoteModel(
    val id: String,
    val title: String,
    val content: String,
    val category: CategoryEntity?,
    val tags: List<String>,
    val attachments: List<AttachmentEntity>,
    val updatedAt: Long
)
