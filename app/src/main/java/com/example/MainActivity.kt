package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.ui.HomeScreen
import com.example.ui.HomeViewModel
import com.example.ui.NoteDetailScreen
import com.example.ui.NoteDetailViewModel
import com.example.ui.SyncDialog
import com.example.ui.ViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(this)
        val repository = NoteRepository(database.noteDao())
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(repository)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(repository: NoteRepository) {
    val navController = rememberNavController()
    var showSyncDialog by remember { mutableStateOf(false) }

    if (showSyncDialog) {
        SyncDialog(onDismiss = { showSyncDialog = false })
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(repository))
            HomeScreen(
                viewModel = viewModel,
                onNavigateToNote = { id ->
                    val route = if (id == null) "note/new" else "note/$id"
                    navController.navigate(route)
                },
                onSyncClicked = { showSyncDialog = true }
            )
        }
        composable("note/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").takeIf { it != "new" }
            val viewModel: NoteDetailViewModel = viewModel(factory = ViewModelFactory(repository, id))
            NoteDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
