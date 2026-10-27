/*
 * Feature Module: UI
 * File: LoadState.kt
 * Description: Generic async UI state and ViewModel helpers.
 */

package com.ssethhyy.journey.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.appContainer
import kotlinx.coroutines.CancellationException

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T, val refreshing: Boolean = false) : LoadState<T>
    data class Error(val error: Throwable) : LoadState<Nothing>

    val dataOrNull: T? get() = (this as? Success)?.data
}

/** Runs [block] and wraps the outcome, rethrowing cancellation. */
suspend fun <T> loadCatching(block: suspend () -> T): LoadState<T> = try {
    LoadState.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    LoadState.Error(e)
}

/** Creates a ViewModel scoped to the current nav entry, with access to the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(key = key) { create(container) }
}
