package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.data.Collection
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProjectDialog(project: CategoryEntity?, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit) {
    var name by rememberSaveable { mutableStateOf(project?.name.orEmpty()) }
    var outcome by rememberSaveable { mutableStateOf(project?.outcome.orEmpty()) }
    var archived by rememberSaveable { mutableStateOf(project?.archived ?: false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (project == null) "Novo projeto" else "Editar projeto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Um projeto é um resultado que exige mais de uma ação.")
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Nome do projeto") }, singleLine = true)
                OutlinedTextField(outcome, { outcome = it.take(2000) }, label = { Text("Como será quando estiver pronto?") }, minLines = 3)
                if (project != null) Row { Checkbox(archived, { archived = it }); Text("Arquivar projeto", Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave((project ?: CategoryEntity(name = name)).copy(name = name, outcome = outcome, archived = archived)) }) { Text("Salvar projeto") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun ReviewPanel(viewModel: HomeViewModel, state: HomeState, onSelect: (Collection) -> Unit) {
    val checks by viewModel.reviewChecks.collectAsStateWithLifecycle()
    val lastReview by viewModel.lastReview.collectAsStateWithLifecycle()
    val steps = listOf(
        Triple("Esvazie a mente", "Capture ideias, compromissos e pendências soltas.", Collection.INBOX),
        Triple("Esclareça a entrada", "Escolha: próxima ação, aguardando, algum dia ou referência.", Collection.INBOX),
        Triple("Revise os projetos", "Cada projeto ativo precisa de uma próxima ação.", Collection.PROJECTS),
        Triple("Acompanhe e escolha", "Revise prazos e o que está com outras pessoas.", Collection.WAITING),
        Triple("Abra espaço para o futuro", "Retome ideias e decida o que merece atenção.", Collection.SOMEDAY)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${checks.size} de 5 passos", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { checks.size / 5f }, modifier = Modifier.fillMaxWidth())
                Text(if (lastReview == 0L) "Sua primeira revisão começa aqui." else "Última revisão: " + dateLabel(Instant.ofEpochMilli(lastReview).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        val stalled = state.projects.count { p -> !p.archived && state.notes.none { it.active && it.category?.id == p.id && it.status == GtdStatus.NEXT } }
        if (stalled > 0) Text("$stalled projeto(s) sem próxima ação.", color = MaterialTheme.colorScheme.primary)
        steps.forEachIndexed { index, step ->
            OutlinedCard {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Checkbox(index in checks, { viewModel.checkReview(index, it) })
                        Column(Modifier.weight(1f).padding(top = 10.dp)) {
                            Text(step.first, style = MaterialTheme.typography.titleMedium)
                            Text(step.second, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { onSelect(step.third) }, modifier = Modifier.padding(start = 40.dp)) { Text("Abrir " + step.third.label.lowercase()) }
                }
            }
        }
        Button(onClick = viewModel::finishReview, enabled = checks.size == 5, modifier = Modifier.fillMaxWidth()) { Text("Concluir revisão semanal") }
    }
}

@Composable
fun BackupDialog(viewModel: HomeViewModel, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { pendingPassword?.fill('\u0000') } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingPassword
        pendingPassword = null
        if (uri != null && pass != null) viewModel.backup(uri, pass, false) else pass?.fill('\u0000')
        password = ""; confirmation = ""
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pass = pendingPassword
        pendingPassword = null
        if (uri != null && pass != null) viewModel.backup(uri, pass, true) else pass?.fill('\u0000')
        password = ""; confirmation = ""
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Seus dados, com você") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("O Ninivia funciona sem conta e sem internet. Crie backups para recuperar suas notas em outro aparelho.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !importing, onClick = { importing = false }, enabled = !busy, label = { Text("Exportar") })
                    FilterChip(selected = importing, onClick = { importing = true }, enabled = !busy, label = { Text("Importar") })
                }
                Text(if (importing) "A importação cria cópias e mantém os itens atuais. Importar o mesmo arquivo outra vez cria duplicatas."
                    else "Inclui notas, tarefas, projetos e checklists. Os arquivos anexados não são incluídos; guarde-os separadamente.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text("Senha do backup") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (!importing) {
                    OutlinedTextField(confirmation, { confirmation = it }, enabled = !busy, label = { Text("Repita a senha") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Text("Use pelo menos 8 caracteres. Guarde a senha: não é possível recuperá-la.", style = MaterialTheme.typography.bodySmall)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && pendingPassword == null && password.length >= 8 && (importing || password == confirmation),
                onClick = {
                    pendingPassword = password.toCharArray()
                    if (importing) importer.launch(arrayOf("*/*")) else exporter.launch("Ninivia-" + java.time.LocalDate.now() + ".ninivia")
                }) { Text(if (busy) "Processando…" else if (importing) "Escolher backup" else "Salvar backup") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Fechar") } })
}
