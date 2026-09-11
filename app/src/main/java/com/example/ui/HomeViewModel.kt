package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.BackupCrypto
import com.example.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class HomeState(
    val notes: List<NoteModel> = emptyList(), val projects: List<CategoryEntity> = emptyList(),
    val loading: Boolean = true, val error: String? = null
)
class HomeViewModel(application: Application, private val repository: NoteRepository) : AndroidViewModel(application) {
    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val busy = MutableStateFlow(false)
    private val prefs = application.getSharedPreferences("ninivia_review", 0)
    val reviewChecks = MutableStateFlow((0..4).filter { prefs.getBoolean("step_$it", false) }.toSet())
    val lastReview = MutableStateFlow(prefs.getLong("last_review", 0))
    val state = combine(repository.allNotes, repository.allCategories) { notes, projects -> HomeState(notes, projects, false) }
        .catch { emit(HomeState(loading = false, error = "Não foi possível carregar o banco de dados. Feche e abra o aplicativo para tentar novamente.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { eventChannel.send(e.message ?: "Não foi possível concluir. Tente novamente.") }
    }
    fun complete(id: String) { action { repository.toggleComplete(id) } }
    fun trash(id: String, restore: Boolean = false) { action { repository.trash(id, restore); eventChannel.send(if (restore) "Item restaurado." else "Item movido para a lixeira.") } }
    fun delete(id: String) { action { repository.deletePermanently(id); eventChannel.send("Item excluído permanentemente.") } }
    fun saveProject(project: CategoryEntity, onSaved: () -> Unit) { action { repository.saveCategory(project); onSaved() } }
    fun checkReview(step: Int, checked: Boolean) {
        reviewChecks.value = if (checked) reviewChecks.value + step else reviewChecks.value - step
        prefs.edit().putBoolean("step_$step", checked).apply()
    }
    fun finishReview() {
        if (reviewChecks.value.size != 5) return
        val now = System.currentTimeMillis()
        lastReview.value = now
        reviewChecks.value = emptySet()
        prefs.edit().clear().putLong("last_review", now).apply()
        action { eventChannel.send("Revisão concluída. Seu sistema está em dia.") }
    }
    fun backup(uri: Uri, password: CharArray, importing: Boolean) {
        if (busy.value) { password.fill('\u0000'); return }
        busy.value = true
        action {
            try {
                val message = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    if (importing) {
                        val bytes = resolver.openInputStream(uri)?.use { stream ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var count: Int
                            while (stream.read(buffer).also { count = it } != -1) {
                                require(out.size() + count <= BackupCrypto.MAX_BYTES) { "O backup excede 20 MB." }
                                out.write(buffer, 0, count)
                            }
                            out.toByteArray()
                        } ?: error("Não foi possível abrir o arquivo.")
                        val json = try { BackupCrypto.decrypt(bytes, password) } catch (_: Exception) { error("Senha incorreta ou backup inválido. Nenhum dado foi alterado.") }
                        val count = repository.importSnapshot(json)
                        "$count itens importados como cópias."
                    } else {
                        val bytes = BackupCrypto.encrypt(repository.exportSnapshot(), password)
                        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Não foi possível gravar o backup.")
                        "Backup protegido salvo."
                    }
                }
                eventChannel.send(message)
            } finally { password.fill('\u0000'); busy.value = false }
        }
    }
}
