package com.example.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class EditorState(val note: NoteModel = NoteModel(), val loading: Boolean = true, val saving: Boolean = false, val dirty: Boolean = false, val error: String? = null)
class NoteDetailViewModel(
    private val repository: NoteRepository, private val noteId: String?,
    private val savedState: SavedStateHandle,
    private val projectId: String? = null
) : ViewModel() {
    val state = MutableStateFlow(EditorState())
    val categories = repository.allCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val saveMutex = Mutex()
    private var pending: Job? = null
    private var revision = 0L
    private val persistedId = noteId ?: savedState.get<String>("persistedNoteId")
    private var persisted = persistedId != null
    private var discarded = false
    init {
        viewModelScope.launch {
            try {
                val draft = savedState.get<String>("draft")?.let { NoteJson.readNote(JSONObject(it)) }
                val existing = persistedId?.let { repository.getNoteById(it) }
                val project = projectId?.let { id -> repository.allCategories.first().find { it.id == id } }
                check(persistedId == null || existing != null || draft != null) { "Este item não está mais disponível." }
                state.value = EditorState(note = draft ?: existing ?: NoteModel(category = project), loading = false, dirty = draft != null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = EditorState(loading = false, error = e.message, note = NoteModel(readable = false)) }
        }
    }
    fun update(transform: (NoteModel) -> NoteModel) {
        val current = state.value
        if (current.loading || !current.note.readable) return
        revision++
        val note = transform(current.note)
        state.value = current.copy(note = note, dirty = true, error = null)
        // Room handles long notes; keep Android's saved-state bundle bounded.
        if (note.content.length < 30_000) savedState["draft"] = NoteJson.note(note, true).toString()
        else savedState.remove<String>("draft")
        pending?.cancel()
        pending = viewModelScope.launch { delay(700); persist() }
    }
    private suspend fun persist(): Boolean = saveMutex.withLock {
        val current = state.value
        if (discarded || current.loading || !current.note.readable) return@withLock false
        if (!current.dirty) return@withLock true
        if (current.note.title.isBlank() && current.note.content.isBlank() && !persisted) return@withLock true
        val savingRevision = revision
        state.value = current.copy(saving = true, error = null)
        try {
            repository.saveNote(current.note)
            persisted = true
            savedState["persistedNoteId"] = current.note.id
            val latest = state.value
            state.value = latest.copy(saving = false, dirty = revision != savingRevision)
            if (revision == savingRevision) savedState.remove<String>("draft")
            true
        } catch (e: CancellationException) { state.value = state.value.copy(saving = false); throw e }
        catch (e: Exception) { state.value = state.value.copy(saving = false, error = e.message ?: "Não foi possível salvar."); false }
    }
    fun saveAndClose(onSaved: () -> Unit) {
        pending?.cancel()
        viewModelScope.launch {
            if (!state.value.note.readable || persist()) onSaved()
        }
    }
    fun flush() { pending?.cancel(); viewModelScope.launch { persist() } }
    fun discard() { discarded = true; pending?.cancel(); savedState.remove<String>("draft") }
    fun addAttachment(uri: String, type: String, name: String) = update {
        if (it.attachments.any { a -> a.uri == uri }) it
        else it.copy(attachments = it.attachments + AttachmentEntity(noteId = it.id, uri = uri, type = type, name = name))
    }
}
