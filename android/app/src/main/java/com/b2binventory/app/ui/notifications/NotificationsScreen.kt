package com.b2binventory.app.ui.notifications

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.settings.clickableItem
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

class NotificationsViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<NotificationDto>>())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) { api.notifications().content }

    fun markRead(n: NotificationDto) {
        if (n.read) return
        viewModelScope.launch {
            apiCall { api.markRead(n.id).requireSuccess() }
            _state.value = _state.value.copy(data = _state.value.data?.map { if (it.id == n.id) it.copy(read = true) else it })
        }
    }

    fun markAll() {
        viewModelScope.launch {
            if (apiCall { api.markAllRead().requireSuccess() } is ApiResult.Ok) load()
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenProduct: (Long) -> Unit, onOpenTradeInvoice: (Long) -> Unit = {},
                        onOpenNetwork: () -> Unit = {}) {
    val vm = appViewModel { c, _ -> NotificationsViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        AppTopBar("Notifications", onBack = onBack) {
            if (state.data?.any { !it.read } == true) TextButton(onClick = vm::markAll) { Text("Mark all read") }
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { list ->
                if (list.isEmpty()) {
                    EmptyState("You're all caught up", "Stock alerts and team updates appear here.", Icons.Default.NotificationsNone)
                } else {
                    LazyColumn {
                        items(list, key = { it.id }) { n ->
                            val icon = when (n.type) {
                                "OUT_OF_STOCK" -> Icons.Default.RemoveShoppingCart
                                "LOW_STOCK" -> Icons.Default.Warning
                                "TRADE_INVOICE_RECEIVED" -> Icons.Default.MarkEmailUnread
                                "CONNECTION_REQUEST", "CONNECTION_ACCEPTED" -> Icons.Default.Hub
                                else -> if (n.type.startsWith("TRADE_")) Icons.Default.ReceiptLong else Icons.Default.Groups
                            }
                            val tint = when (n.type) {
                                "OUT_OF_STOCK" -> StatusColors.out
                                "LOW_STOCK" -> StatusColors.low
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val productId = n.payload["productId"]?.jsonPrimitive?.longOrNull
                            val tradeInvoiceId = n.payload["tradeInvoiceId"]?.jsonPrimitive?.longOrNull
                            val connection = n.type.startsWith("CONNECTION_")
                            ListItem(
                                headlineContent = { Text(n.title, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold) },
                                supportingContent = {
                                    Column {
                                        n.body?.let { Text(it) }
                                        Text(Fmt.dateTime(n.createdAt), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                leadingContent = { Icon(icon, null, tint = tint) },
                                trailingContent = { if (!n.read) Badge() },
                                modifier = Modifier.clickableItem {
                                    vm.markRead(n)
                                    when {
                                        tradeInvoiceId != null -> onOpenTradeInvoice(tradeInvoiceId)
                                        connection -> onOpenNetwork()
                                        productId != null -> onOpenProduct(productId)
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
