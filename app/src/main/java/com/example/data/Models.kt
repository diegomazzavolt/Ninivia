package com.example.data

import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDate
import java.util.UUID

enum class GtdStatus(val label: String, val guidance: String) {
    INBOX("Caixa de entrada", "Capture agora. Decida depois."),
    NEXT("Próximas ações", "Qual é a próxima ação concreta?"),
    WAITING("Aguardando", "O que depende de outra pessoa?"),
    SOMEDAY("Algum dia", "Ideias para retomar no momento certo."),
    REFERENCE("Referência", "Informação útil, sem ação necessária.")
}
enum class RepeatRule(val label: String) { NONE("Não repetir"), DAILY("Diariamente"), WEEKLY("Semanalmente"), MONTHLY("Mensalmente") }
enum class Collection(val label: String) {
    INBOX("Entrada"), TODAY("Hoje"), NEXT("Ações"), PROJECTS("Projetos"), REVIEW("Revisão"),
    WAITING("Aguardando"), SOMEDAY("Algum dia"), REFERENCE("Referência"), COMPLETED("Concluídas"), TRASH("Lixeira")
}
data class ChecklistItem(val id: String = UUID.randomUUID().toString(), val text: String, val done: Boolean = false)
data class NoteWithDetails(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: CategoryEntity?,
    @Relation(parentColumn = "id", entityColumn = "noteId") val attachments: List<AttachmentEntity>
)
data class NoteModel(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val category: CategoryEntity? = null,
    val tags: List<String> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: GtdStatus = GtdStatus.INBOX,
    val dueDay: Long? = null,
    val priority: Int = 0,
    val context: String = "",
    val waitingFor: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    val repeat: RepeatRule = RepeatRule.NONE,
    val readable: Boolean = true
) {
    val active get() = completedAt == null && deletedAt == null
    val actionable get() = status == GtdStatus.NEXT || status == GtdStatus.WAITING
    val displayTitle get() = title.ifBlank { content.lineSequence().firstOrNull()?.take(80).orEmpty().ifBlank { "Sem título" } }
}

object GtdRules {
    fun inCollection(note: NoteModel, collection: Collection, today: Long): Boolean = when (collection) {
        Collection.TRASH -> note.deletedAt != null
        Collection.COMPLETED -> note.deletedAt == null && note.completedAt != null
        Collection.TODAY -> note.active && note.actionable && note.dueDay?.let { it <= today } == true
        Collection.INBOX -> note.active && note.status == GtdStatus.INBOX
        Collection.NEXT -> note.active && note.status == GtdStatus.NEXT
        Collection.WAITING -> note.active && note.status == GtdStatus.WAITING
        Collection.SOMEDAY -> note.active && note.status == GtdStatus.SOMEDAY
        Collection.REFERENCE -> note.active && note.status == GtdStatus.REFERENCE
        Collection.PROJECTS, Collection.REVIEW -> note.active
    }
    fun search(note: NoteModel, query: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val text = listOf(note.title, note.content, note.context, note.waitingFor, note.category?.name.orEmpty(), note.tags.joinToString(" ")).joinToString(" ")
        return words.all { text.contains(it, ignoreCase = true) }
    }
    val ordering = compareByDescending<NoteModel> { it.priority }.thenBy { it.dueDay ?: Long.MAX_VALUE }.thenByDescending { it.updatedAt }
    fun nextDue(day: Long, rule: RepeatRule): Long = LocalDate.ofEpochDay(day).let {
        when (rule) { RepeatRule.DAILY -> it.plusDays(1); RepeatRule.WEEKLY -> it.plusWeeks(1); RepeatRule.MONTHLY -> it.plusMonths(1); RepeatRule.NONE -> it }
    }.toEpochDay()
}
