package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.data.NoteRepository

class ViewModelFactory(
    private val application: Application, private val repository: NoteRepository,
    private val noteId: String? = null, private val projectId: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(application, repository) as T
        modelClass.isAssignableFrom(NoteDetailViewModel::class.java) -> NoteDetailViewModel(repository, noteId, extras.createSavedStateHandle(), projectId) as T
        else -> error("ViewModel desconhecido: " + modelClass.name)
    }
}
