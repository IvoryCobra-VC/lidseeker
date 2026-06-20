package com.lidseeker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lidseeker.app.LidseekerApp
import com.lidseeker.app.data.Repository

/** Builds a ViewModel factory that injects the app-wide [Repository]. */
inline fun <reified VM : ViewModel> repoFactory(crossinline create: (Repository) -> VM) =
    viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as LidseekerApp
            create(app.repository)
        }
    }
