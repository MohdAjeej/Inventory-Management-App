package com.b2binventory.app.ui.trade

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.common.Text
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProductLinksViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<ProductLink>>())
    val state = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun load() = _state.load(viewModelScope) { api.productLinks() }

    fun unlink(id: Long) {
        viewModelScope.launch {
            when (val r = apiCall { api.deleteProductLink(id).requireSuccess() }) {
                is ApiResult.Ok -> { _messages.emit("Link removed. You will be asked again next time."); load() }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }
}

/** Which supplier product is received into which of my products; created when invoices are approved. */
@Composable
fun ProductLinksScreen(onBack: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> ProductLinksViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    var unlinking by remember { mutableStateOf<ProductLink?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }

    Scaffold(topBar = { AppTopBar("Product links", onBack = onBack, subtitle = "Supplier products and where they go in your stock") },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { list ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (list.isEmpty()) item {
                        EmptyState("No product links yet",
                            "When you approve a supplier's invoice, the app remembers which of your products each item went into.",
                            Icons.Default.Link)
                    }
                    items(list, key = { it.id }) { l ->
                        AppCard(onClick = { onOpenProduct(l.buyerProductId) }) {
                            Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    androidx.compose.material3.Text(l.seller.name, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    androidx.compose.material3.Text(l.sellerProductName ?: "—", style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        androidx.compose.material3.Text(l.buyerProductName + (l.buyerVariantName?.let { " · $it" } ?: ""),
                                            style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                                IconButton(onClick = { unlinking = l }) { Icon(Icons.Default.LinkOff, tr("Remove link")) }
                            }
                        }
                    }
                }
            }
        }
    }
    unlinking?.let { l ->
        ConfirmDialog("Remove this link?", "Stock already received stays. Next time you will choose the product again.", "Remove",
            destructive = true, onConfirm = { vm.unlink(l.id); unlinking = null }, onDismiss = { unlinking = null })
    }
}
