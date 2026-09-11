package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.data.Collection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pt = Locale.forLanguageTag("pt-BR")
fun dateLabel(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd MMM", pt))
fun collectionIcon(c: Collection): ImageVector = when(c) {
    Collection.INBOX -> Icons.Outlined.Inbox
    Collection.TODAY -> Icons.Outlined.WbSunny
    Collection.NEXT -> Icons.Outlined.CheckCircle
    Collection.PROJECTS -> Icons.Outlined.FolderOpen
    Collection.REVIEW -> Icons.AutoMirrored.Outlined.FactCheck
    Collection.WAITING -> Icons.Outlined.Schedule
    Collection.SOMEDAY -> Icons.Outlined.Lightbulb
    Collection.REFERENCE -> Icons.Outlined.Bookmarks
    Collection.COMPLETED -> Icons.Outlined.TaskAlt
    Collection.TRASH -> Icons.Outlined.DeleteOutline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigateToNote: (String?, String?) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var collectionName by rememberSaveable { mutableStateOf(Collection.INBOX.name) }
    val collection = Collection.valueOf(collectionName)
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var contextFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var showProjectEditor by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<CategoryEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<NoteModel?>(null) }
    var today by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now().toEpochDay(); delay(30_000) } }
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    fun select(c: Collection) { collectionName = c.name; projectId = null; contextFilter = null; query = "" }
    BackHandler(drawer.isOpen || projectId != null || collection != Collection.INBOX) {
        if (drawer.isOpen) scope.launch { drawer.close() }
        else if (projectId != null) projectId = null
        else select(Collection.INBOX)
    }
    val selectedProject = state.projects.find { it.id == projectId }
    val notes = state.notes.filter {
        (if (projectId != null) it.active && it.category?.id == projectId else GtdRules.inCollection(it, collection, today)) &&
            GtdRules.search(it, query) && (contextFilter == null || it.context == contextFilter)
    }.sortedWith(GtdRules.ordering)
    val contexts = state.notes.filter { it.active }.map { it.context }.filter { it.isNotBlank() }.distinct().sorted()
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Column(Modifier.padding(24.dp)) {
                Text("ninivia", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Espaço para pensar. Clareza para agir.", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(Collection.entries) { c ->
                    NavigationDrawerItem(
                        label = { Text(c.label) }, icon = { Icon(collectionIcon(c), null) },
                        selected = collection == c,
                        badge = { if (c != Collection.PROJECTS && c != Collection.REVIEW) Text(state.notes.count { GtdRules.inCollection(it, c, today) }.toString()) },
                        onClick = { select(c); scope.launch { drawer.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            TextButton(onClick = { showBackup = true; scope.launch { drawer.close() } }, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Outlined.Backup, null); Spacer(Modifier.width(12.dp)); Text("Backup e restauração")
            }
        }
    }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(title = { Text("ninivia", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Abrir menu") } },
                    actions = { IconButton(onClick = { showBackup = true }) { Icon(Icons.Outlined.Backup, "Backup e restauração") } })
            },
            bottomBar = {
                NavigationBar {
                    listOf(Collection.INBOX, Collection.TODAY, Collection.NEXT, Collection.PROJECTS, Collection.REVIEW).forEach { c ->
                        NavigationBarItem(selected = c == collection, onClick = { select(c) }, modifier = Modifier.testTag("nav_" + c.name),
                            icon = { Icon(collectionIcon(c), null) }, label = { Text(c.label, maxLines = 1) })
                    }
                }
            },
            floatingActionButton = {
                if (collection != Collection.TRASH && collection != Collection.COMPLETED) {
                    ExtendedFloatingActionButton(onClick = { onNavigateToNote(null, projectId) },
                        icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Capturar") }, modifier = Modifier.testTag("capture"))
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("home_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", pt)).uppercase(pt),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Text(selectedProject?.name ?: when(collection) { Collection.INBOX -> "Sua mente, mais leve."; Collection.TODAY -> "Um dia de cada vez."; Collection.REVIEW -> "Encontre sua clareza."; else -> collection.label },
                            style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(selectedProject?.outcome?.ifBlank { "Defina o resultado e escolha a próxima ação." } ?: when(collection) {
                            Collection.INBOX -> "Tudo começa com uma captura. Organize quando estiver pronto."
                            Collection.TODAY -> "Ações com prazo hoje e pendências anteriores."
                            Collection.NEXT -> "Ações claras, prontas para começar."
                            Collection.PROJECTS -> "Um resultado desejado. Uma próxima ação."
                            Collection.REVIEW -> "Alguns minutos para cuidar do seu sistema."
                            Collection.WAITING -> "Acompanhe o que depende de outras pessoas."
                            Collection.SOMEDAY -> "Guarde possibilidades sem ocupar sua atenção."
                            Collection.REFERENCE -> "Seu acervo de notas e conhecimento."
                            Collection.COMPLETED -> "Veja o que já saiu do papel."
                            Collection.TRASH -> "Restaure um item ou exclua definitivamente."
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                if (collection == Collection.REVIEW) {
                    item { ReviewPanel(viewModel, state, onSelect = { select(it) }) }
                } else if (collection == Collection.PROJECTS && projectId == null) {
                    item {
                        OutlinedButton(onClick = { editingProject = null; showProjectEditor = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Novo projeto")
                        }
                    }
                    if (state.projects.isEmpty() && !state.loading) item { EmptyState("Dê um lugar aos seus planos", "Crie um projeto para resultados que precisam de mais de uma ação.") }
                    items(state.projects.sortedBy { it.archived }, key = { it.id }) { p ->
                        val actions = state.notes.filter { it.category?.id == p.id && it.deletedAt == null }
                        val completed = actions.count { it.completedAt != null }
                        val hasNext = actions.any { it.active && it.status == GtdStatus.NEXT }
                        OutlinedCard(onClick = { projectId = p.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { editingProject = p; showProjectEditor = true }) { Icon(Icons.Outlined.Edit, "Editar projeto " + p.name) }
                                }
                                if (p.outcome.isNotBlank()) Text(p.outcome, style = MaterialTheme.typography.bodyMedium)
                                Text(if (p.archived) "Projeto arquivado" else if (!hasNext) "Defina uma próxima ação" else "$completed de ${actions.size} itens concluídos",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                LinearProgressIndicator(progress = { if (actions.isEmpty()) 0f else completed.toFloat() / actions.size }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else {
                    if (selectedProject != null) item {
                        Row {
                            TextButton(onClick = { projectId = null }) { Text("Todos os projetos") }
                            TextButton(onClick = { editingProject = selectedProject; showProjectEditor = true }) { Text("Editar projeto") }
                        }
                    }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("search"),
                            placeholder = { Text("Buscar nesta lista") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Limpar busca") } },
                            shape = RoundedCornerShape(16.dp), singleLine = true)
                    }
                    if (contexts.isNotEmpty() && collection != Collection.TRASH) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = contextFilter == null, onClick = { contextFilter = null }, label = { Text("Todos os contextos") }) }
                            items(contexts) { context -> FilterChip(selected = contextFilter == context, onClick = { contextFilter = context }, label = { Text("@$context") }) }
                        }
                    }
                    item {
                        Text("${notes.size} " + if (notes.size == 1) "ITEM" else "ITENS", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (notes.isEmpty() && !state.loading) item {
                        EmptyState(if (query.isNotBlank() || contextFilter != null) "Nenhum resultado" else if (collection == Collection.INBOX) "Uma coisa a menos na cabeça" else "Tudo tranquilo por aqui",
                            if (query.isNotBlank() || contextFilter != null) "Tente outro termo ou contexto." else if (collection == Collection.INBOX) "Toque em Capturar para guardar uma ideia, tarefa ou nota. Depois escolha o que fazer com ela." else "Os itens desta lista aparecerão aqui quando você os organizar.")
                    }
                    items(notes, key = { it.id }) { note ->
                        NoteCard(note, today,
                            onClick = { onNavigateToNote(note.id, null) }, onComplete = { viewModel.complete(note.id) },
                            onTrash = { viewModel.trash(note.id) }, onRestore = { viewModel.trash(note.id, true) },
                            onDelete = { confirmDelete = note })
                    }
                }
            }
        }
    }
    if (showProjectEditor) ProjectDialog(editingProject, onDismiss = { showProjectEditor = false }) { p ->
        viewModel.saveProject(p) { showProjectEditor = false }
    }
    confirmDelete?.let { n ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Excluir permanentemente?") },
            text = { Text("“${n.displayTitle}” será removido. Esta ação não pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(n.id); confirmDelete = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } })
    }
    if (showBackup) BackupDialog(viewModel) { showBackup = false }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Spa, null, Modifier.padding(22.dp).size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NoteCard(note: NoteModel, today: Long, onClick: () -> Unit, onComplete: () -> Unit, onTrash: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("note_" + note.id),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            if (note.readable && note.deletedAt == null && note.status != GtdStatus.REFERENCE) {
                Checkbox(checked = note.completedAt != null, onCheckedChange = { onComplete() }, modifier = Modifier.testTag("complete_" + note.id))
            } else Icon(Icons.Outlined.Description, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(note.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (note.completedAt != null) TextDecoration.LineThrough else null)
                if (note.content.isNotBlank()) Text(note.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val detail = buildList {
                    note.category?.let { add(it.name) }
                    if (note.context.isNotBlank()) add("@" + note.context)
                    if (note.status == GtdStatus.WAITING && note.waitingFor.isNotBlank()) add("Com " + note.waitingFor)
                    if (note.checklist.isNotEmpty()) add("${note.checklist.count { it.done }}/${note.checklist.size}")
                    if (note.attachments.isNotEmpty()) add("${note.attachments.size} anexo(s)")
                }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    note.dueDay?.let { day ->
                        val overdue = note.active && note.actionable && day < today
                        Text((if (overdue) "Atrasada · " else "") + dateLabel(day), style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    if (note.priority > 0) Text(listOf("", "Baixa", "Média", "Alta")[note.priority], style = MaterialTheme.typography.labelSmall,
                        color = if (note.priority == 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (note.repeat != RepeatRule.NONE) Icon(Icons.Outlined.Repeat, "Recorrente", Modifier.size(16.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Opções de " + note.displayTitle) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (note.deletedAt != null) {
                        DropdownMenuItem(text = { Text("Restaurar") }, onClick = { menu = false; onRestore() })
                        DropdownMenuItem(text = { Text("Excluir definitivamente") }, onClick = { menu = false; onDelete() })
                    } else DropdownMenuItem(text = { Text("Mover para lixeira") }, onClick = { menu = false; onTrash() })
                }
            }
        }
    }
}
