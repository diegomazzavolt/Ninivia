package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var repository: NoteRepository
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = NoteRepository(db, TestCipher)
    }
    @After fun close() { db.close() }
    @Test fun captureEditSaveSearchAndReopen() {
        compose.setContent { MyApplicationTheme { com.example.AppNavigation(repository) } }
        compose.onNodeWithTag("capture").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Salvo no aparelho").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("note_title").performTextInput("Planejar minha semana")
        compose.onNodeWithTag("note_content").performTextInput("Revisar os projetos e escolher a próxima ação.")
        compose.onNodeWithTag("save_button").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("capture").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Planejar minha semana").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search").performTextInput("semana")
        compose.onNodeWithText("Planejar minha semana").assertExists()
        screenshot("entrada")
        compose.onNodeWithText("Planejar minha semana").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Revisar os projetos e escolher a próxima ação.").fetchSemanticsNodes().isNotEmpty() }
        screenshot("editor")
    }
    @Test fun todayShowsOnlyActionableDueItems() {
        runBlocking {
            repository.saveNote(NoteModel(title = "Enviar proposta para Ana", status = GtdStatus.NEXT, dueDay = java.time.LocalDate.now().toEpochDay(), priority = 3, context = "trabalho"))
            repository.saveNote(NoteModel(title = "Uma ideia para depois", status = GtdStatus.SOMEDAY, dueDay = java.time.LocalDate.now().toEpochDay()))
        }
        compose.setContent { MyApplicationTheme { com.example.AppNavigation(repository) } }
        compose.onNodeWithTag("nav_TODAY").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Enviar proposta para Ana").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Uma ideia para depois").assertDoesNotExist()
        screenshot("hoje")
    }
    private fun screenshot(name: String) {
        val file = File("../artifacts/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(filePath = file.path)
    }
}
