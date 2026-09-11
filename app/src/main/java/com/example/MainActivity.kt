package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = NoteRepository(AppDatabase.getDatabase(this))
        setContent { MyApplicationTheme { AppNavigation(repository) } }
    }
}
@Composable
fun AppNavigation(repository: NoteRepository) {
    val nav = rememberNavController()
    val application = LocalContext.current.applicationContext as Application
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm: HomeViewModel = viewModel(factory = ViewModelFactory(application, repository))
            HomeScreen(vm, onNavigateToNote = { id, project ->
                nav.navigate("note/" + (id ?: "new") + (project?.let { "?project=$it" } ?: ""))
            })
        }
        composable("note/{id}?project={project}", arguments = listOf(navArgument("project") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val id = entry.arguments?.getString("id")?.takeUnless { it == "new" }
            val project = entry.arguments?.getString("project")
            val vm: NoteDetailViewModel = viewModel(factory = ViewModelFactory(application, repository, id, project))
            NoteDetailScreen(vm) { nav.popBackStack() }
        }
    }
}
