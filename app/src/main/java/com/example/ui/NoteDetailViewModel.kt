package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttachmentEntity
import com.example.data.CategoryEntity
import com.example.data.NoteModel
import com.example.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class NoteDetailViewModel(
    private val repository: NoteRepository,
    private val noteId: String?
) : ViewModel() {

    private val _noteTitle = MutableStateFlow("")
    val noteTitle: StateFlow<String> = _noteTitle
    
    private val _noteContent = MutableStateFlow("")
    val noteContent: StateFlow<String> = _noteContent
    
    private val _selectedCategory = MutableStateFlow<CategoryEntity?>(null)
    val selectedCategory: StateFlow<CategoryEntity?> = _selectedCategory
    
    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags
    
    private val _attachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    val attachments: StateFlow<List<AttachmentEntity>> = _attachments

    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (noteId != null) {
            viewModelScope.launch {
                repository.getNoteById(noteId).collect { note ->
                    if (note != null) {
                        _noteTitle.value = note.title
                        _noteContent.value = note.content
                        _selectedCategory.value = note.category
                        _tags.value = note.tags
                        _attachments.value = note.attachments
                    }
                }
            }
        }
    }

    fun updateTitle(title: String) { _noteTitle.value = title }
    fun updateContent(content: String) { _noteContent.value = content }
    fun selectCategory(category: CategoryEntity?) { _selectedCategory.value = category }
    fun addTag(tag: String) { 
        if (tag.isNotBlank() && !_tags.value.contains(tag)) {
            _tags.value = _tags.value + tag
        }
    }
    fun removeTag(tag: String) { _tags.value = _tags.value - tag }
    
    fun addAttachment(uri: String, type: String, name: String) {
        val attachment = AttachmentEntity(
            noteId = noteId ?: "",
            uri = uri,
            type = type,
            name = name
        )
        _attachments.value = _attachments.value + attachment
    }
    
    fun removeAttachment(attachment: AttachmentEntity) {
        _attachments.value = _attachments.value - attachment
    }

    fun saveNote(onSaved: () -> Unit) {
        val idToSave = noteId ?: UUID.randomUUID().toString()
        val finalAttachments = _attachments.value.map { it.copy(noteId = idToSave) }
        val note = NoteModel(
            id = idToSave,
            title = _noteTitle.value,
            content = _noteContent.value,
            category = _selectedCategory.value,
            tags = _tags.value,
            attachments = finalAttachments,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveNote(note)
            onSaved()
        }
    }
}
