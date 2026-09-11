package com.example.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(viewModel: NoteDetailViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val note = state.note
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var showDate by remember { mutableStateOf(false) }
    var checklistText by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val editable = note.readable && !state.loading && note.deletedAt == null
    fun close() { viewModel.saveAndClose(onNavigateBack) }
    BackHandler { close() }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else "Anexo"
                    } ?: "Anexo"
                    name to (context.contentResolver.getType(uri) ?: "application/octet-stream")
                }
                viewModel.addAttachment(uri.toString(), info.second, info.first)
            } catch (_: Exception) { snackbar.showSnackbar("Não foi possível manter acesso a esse arquivo. Escolha outro local.") }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (note.status == GtdStatus.REFERENCE) "Nota de referência" else "Organizar captura", style = MaterialTheme.typography.titleMedium)
                        Text(when { state.loading -> "Carregando…"; state.error != null -> "Não salvo"; state.saving -> "Salvando…"; state.dirty -> "Alterações pendentes"; else -> "Salvo no aparelho" },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
                actions = {
                    TextButton(onClick = { close() }, enabled = !state.loading && !state.saving, modifier = Modifier.testTag("save_button")) { Text("Concluir") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mais opções") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Compartilhar texto") }, enabled = note.readable, onClick = {
                                menu = false
                                val text = note.displayTitle + "\n\n" + note.content
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar nota"))
                            })
                            if (state.error != null) DropdownMenuItem(text = { Text("Sair sem salvar alterações") }, onClick = { menu = false; showDiscard = true })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!note.readable) Text(note.content, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(note.title, { text -> viewModel.update { it.copy(title = text.take(500)) } },
                label = { Text("Título") }, placeholder = { Text("O que está na sua cabeça?") }, enabled = editable,
                modifier = Modifier.fillMaxWidth().testTag("note_title"), textStyle = MaterialTheme.typography.titleLarge, maxLines = 3)
            OutlinedTextField(note.content, { text -> viewModel.update { it.copy(content = text.take(100_000)) } },
                label = { Text("Notas e detalhes") }, placeholder = { Text("Anote livremente. Você pode organizar depois.") },
                enabled = editable, modifier = Modifier.fillMaxWidth().testTag("note_content"), minLines = 5)
            HorizontalDivider()
            Text("O que isso significa?", style = MaterialTheme.typography.titleMedium)
            Text(note.status.guidance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionField("Organizar em", note.status, GtdStatus.entries.toList(), { it.label }, editable) { status ->
                viewModel.update { it.copy(status = status, repeat = if (status == GtdStatus.NEXT || status == GtdStatus.WAITING) it.repeat else RepeatRule.NONE) }
            }
            if (note.status == GtdStatus.INBOX) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Text("Exige uma ação? Se leva menos de 2 minutos, faça agora. Se tem várias etapas, associe a um projeto e defina a próxima ação.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            SelectionField("Projeto", note.category?.id, listOf<String?>(null) + categories.filter { !it.archived || it.id == note.category?.id }.map { it.id },
                { id -> categories.find { it.id == id }?.name ?: "Sem projeto" }, editable) { id ->
                viewModel.update { it.copy(category = categories.find { c -> c.id == id }) }
            }
            if (note.status == GtdStatus.WAITING) {
                OutlinedTextField(note.waitingFor, { text -> viewModel.update { it.copy(waitingFor = text.take(200)) } },
                    label = { Text("Aguardando quem ou o quê?") }, enabled = editable, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDate = true }, enabled = editable, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Event, null); Spacer(Modifier.width(8.dp))
                    Text(note.dueDay?.let(::dateLabel) ?: "Definir prazo")
                }
                if (note.dueDay != null) IconButton(enabled = editable, onClick = { viewModel.update { it.copy(dueDay = null, repeat = RepeatRule.NONE) } }) {
                    Icon(Icons.Outlined.EventBusy, "Remover prazo")
                }
            }
            if (note.actionable && note.dueDay != null) {
                SelectionField("Repetição", note.repeat, RepeatRule.entries.toList(), { it.label }, editable) { rule -> viewModel.update { it.copy(repeat = rule) } }
            }
            SelectionField("Prioridade", note.priority, listOf(0, 1, 2, 3), { listOf("Sem prioridade", "Baixa", "Média", "Alta")[it] }, editable) { value ->
                viewModel.update { it.copy(priority = value) }
            }
            OutlinedTextField(note.context, { text -> viewModel.update { it.copy(context = text.removePrefix("@").take(100)) } },
                label = { Text("Contexto") }, placeholder = { Text("casa, trabalho, computador…") }, singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
            var tagsInput by remember(note.id) { mutableStateOf(note.tags.joinToString(", ")) }
            OutlinedTextField(tagsInput, { text ->
                tagsInput = text.take(1000)
                viewModel.update { it.copy(tags = tagsInput.split(",").map { tag -> tag.trim().take(100) }.filter { tag -> tag.isNotBlank() }.distinct().take(50)) }
            }, label = { Text("Etiquetas separadas por vírgula") }, enabled = editable, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("Pequenos passos", style = MaterialTheme.typography.titleMedium)
            note.checklist.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.done, enabled = editable, onCheckedChange = { checked -> viewModel.update { it.copy(checklist = it.checklist.map { c -> if (c.id == item.id) c.copy(done = checked) else c }) } })
                    Text(item.text, Modifier.weight(1f))
                    IconButton(enabled = editable, onClick = { viewModel.update { it.copy(checklist = it.checklist.filterNot { c -> c.id == item.id }) } }) {
                        Icon(Icons.Outlined.Close, "Remover passo " + item.text)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(checklistText, { checklistText = it.take(1000) }, enabled = editable, label = { Text("Adicionar um passo") }, modifier = Modifier.weight(1f))
                IconButton(enabled = editable && checklistText.isNotBlank() && note.checklist.size < 200, onClick = {
                    val text = checklistText.trim()
                    viewModel.update { it.copy(checklist = it.checklist + ChecklistItem(text = text)) }; checklistText = ""
                }) { Icon(Icons.Outlined.Add, "Adicionar passo") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Anexos", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { document.launch(arrayOf("image/*", "application/pdf", "text/plain")) }, enabled = editable && note.attachments.size < 30) {
                    Icon(Icons.Outlined.AttachFile, null); Text("Adicionar")
                }
            }
            note.attachments.forEach { attachment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        try {
                            val mime = when(attachment.type) { "IMAGE" -> "image/*"; "PDF" -> "application/pdf"; "DOCUMENT" -> "*/*"; else -> attachment.type }
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(attachment.uri), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        } catch (_: Exception) { scope.launch { snackbar.showSnackbar("Arquivo indisponível. Anexe novamente ou instale um aplicativo que possa abri-lo.") } }
                    }, modifier = Modifier.weight(1f)) { Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    IconButton(enabled = editable, onClick = { viewModel.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == attachment.id }) } }) {
                        Icon(Icons.Outlined.Close, "Remover anexo " + attachment.name)
                    }
                }
            }
            Text("Alterações são salvas automaticamente. Você pode usar o Ninivia sem conexão.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = note.dueDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                dateState.selectedDateMillis?.let { millis ->
                    viewModel.update { it.copy(dueDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                }; showDate = false
            }, enabled = dateState.selectedDateMillis != null) { Text("Definir data") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancelar") } }) { DatePicker(dateState, title = { Text("Prazo", Modifier.padding(24.dp)) }) }
    }
    if (showDiscard) AlertDialog(onDismissRequest = { showDiscard = false }, title = { Text("Sair sem salvar?") },
        text = { Text("As alterações que não foram salvas serão descartadas.") },
        confirmButton = { TextButton(onClick = { viewModel.discard(); onNavigateBack() }) { Text("Sair") } },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Continuar editando") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectionField(label: String, selected: T, options: List<T>, display: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(display(selected), {}, readOnly = true, enabled = enabled,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { value -> DropdownMenuItem(text = { Text(display(value)) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}
