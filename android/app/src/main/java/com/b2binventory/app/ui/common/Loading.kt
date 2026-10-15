package com.b2binventory.app.ui.common

import com.b2binventory.app.data.remote.ApiResult
import com.b2binventory.app.data.remote.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads into a [Loadable] state flow, keeping the previous data visible while refreshing. */
fun <T> MutableStateFlow<Loadable<T>>.load(scope: CoroutineScope, block: suspend () -> T) {
    update { it.copy(loading = true, error = null) }
    scope.launch {
        when (val r = apiCall(block)) {
            is ApiResult.Ok -> value = Loadable(r.value, loading = false)
            is ApiResult.Err -> update { it.copy(loading = false, error = r.message) }
        }
    }
}
