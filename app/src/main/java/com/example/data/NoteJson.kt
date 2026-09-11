package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** Explicit, versioned interchange format; no reflection or executable types. */
object NoteJson {
    fun checklist(items: List<ChecklistItem>) = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
    }
    fun readChecklist(value: String): List<ChecklistItem> {
        if (value.isBlank()) return emptyList()
        val list = JSONArray(value)
        require(list.length() <= 200) { "Máximo de 200 itens por checklist." }
        return (0 until list.length()).map { i -> list.getJSONObject(i).let {
            ChecklistItem(it.getString("id"), it.getString("text").also { text -> require(text.length <= 1000) }, it.getBoolean("done"))
        } }
    }
    fun project(p: CategoryEntity) = JSONObject().put("id", p.id).put("name", p.name).put("color", p.color).put("outcome", p.outcome).put("archived", p.archived)
    private fun readProject(p: JSONObject) = CategoryEntity(p.getString("id"), p.getString("name"), p.getInt("color"), p.optString("outcome"), p.optBoolean("archived")).also {
        require(it.name.isNotBlank() && it.name.length <= 100 && it.outcome.length <= 2000)
    }
    fun note(n: NoteModel, includeAttachments: Boolean = false): JSONObject = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("content", n.content)
        put("project", n.category?.let(::project) ?: JSONObject.NULL)
        put("tags", JSONArray(n.tags)); put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
        put("status", n.status.name); put("dueDay", n.dueDay ?: JSONObject.NULL); put("priority", n.priority)
        put("context", n.context); put("waitingFor", n.waitingFor); put("checklist", checklist(n.checklist))
        put("completedAt", n.completedAt ?: JSONObject.NULL); put("deletedAt", n.deletedAt ?: JSONObject.NULL)
        put("repeat", n.repeat.name)
        if (includeAttachments) put("attachments", JSONArray().apply { n.attachments.forEach { a ->
            put(JSONObject().put("id", a.id).put("noteId", a.noteId).put("uri", a.uri).put("type", a.type).put("name", a.name))
        } })
    }
    fun readNote(p: JSONObject): NoteModel {
        val tags = p.getJSONArray("tags")
        require(tags.length() <= 50)
        val attachments = p.optJSONArray("attachments") ?: JSONArray()
        return NoteModel(
            id = p.getString("id"), title = p.getString("title"), content = p.getString("content"),
            category = p.optJSONObject("project")?.let(::readProject),
            tags = (0 until tags.length()).map { tags.getString(it).also { tag -> require(tag.length <= 100) } },
            createdAt = p.getLong("createdAt"), updatedAt = p.getLong("updatedAt"),
            status = GtdStatus.valueOf(p.getString("status")), dueDay = p.nullableLong("dueDay"),
            priority = p.getInt("priority"), context = p.getString("context"), waitingFor = p.getString("waitingFor"),
            checklist = readChecklist(p.getJSONArray("checklist").toString()),
            completedAt = p.nullableLong("completedAt"), deletedAt = p.nullableLong("deletedAt"),
            repeat = RepeatRule.valueOf(p.getString("repeat")),
            attachments = (0 until attachments.length()).map { i -> attachments.getJSONObject(i).let {
                AttachmentEntity(it.getString("id"), it.getString("noteId"), it.getString("uri"), it.getString("type"), it.getString("name"))
            } }
        ).also {
            require(it.title.length <= 500 && it.content.length <= 100_000 && it.priority in 0..3)
            require(it.context.length <= 100 && it.waitingFor.length <= 200)
            require(it.dueDay == null || it.dueDay in -719162L..2932896L)
            require(it.repeat == RepeatRule.NONE || (it.dueDay != null && it.actionable))
        }
    }
    fun backup(notes: List<NoteModel>, projects: List<CategoryEntity>) = JSONObject()
        .put("format", "ninivia").put("version", 1)
        .put("projects", JSONArray().apply { projects.forEach { put(project(it)) } })
        .put("notes", JSONArray().apply { notes.forEach { put(note(it)) } })
    fun readBackup(value: String): Pair<List<NoteModel>, List<CategoryEntity>> {
        val root = JSONObject(value)
        require(root.getString("format") == "ninivia" && root.getInt("version") == 1) { "Formato de backup não suportado." }
        val notes = root.getJSONArray("notes")
        val projects = root.getJSONArray("projects")
        require(notes.length() <= 10000 && projects.length() <= 1000) { "Backup excede o limite de importação." }
        val parsedNotes = (0 until notes.length()).map { readNote(notes.getJSONObject(it)).copy(attachments = emptyList()) }
        val parsedProjects = (0 until projects.length()).map { readProject(projects.getJSONObject(it)) }
        require(parsedNotes.map { it.id }.distinct().size == parsedNotes.size)
        require(parsedProjects.map { it.id }.distinct().size == parsedProjects.size)
        require(parsedNotes.all { n -> n.category == null || parsedProjects.any { it.id == n.category.id } })
        return parsedNotes to parsedProjects
    }
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
}
