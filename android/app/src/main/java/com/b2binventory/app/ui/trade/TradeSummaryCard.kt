package com.b2binventory.app.ui.trade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.ApiResult
import com.b2binventory.app.data.remote.ApiService
import com.b2binventory.app.data.remote.TradeSummary
import com.b2binventory.app.data.remote.apiCall
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.common.Text
import com.b2binventory.app.ui.navigation.Routes
import com.b2binventory.app.ui.theme.appColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TradeSummaryViewModel(private val api: ApiService) : ViewModel() {
    private val _summary = MutableStateFlow<TradeSummary?>(null)
    val summary = _summary.asStateFlow()

    fun refresh() {
        viewModelScope.launch { (apiCall { api.tradeSummary() } as? ApiResult.Ok)?.let { _summary.value = it.value } }
    }
}

/**
 * Dashboard card for the business network: invoices waiting for approval, invoices waiting on buyers, returns and
 * connection requests. Refreshes every minute while visible, so new invoices show up without restarting the app.
 */
@Composable
fun TradeSummaryCard(onOpen: (String) -> Unit) {
    val vm = appViewModel { c, _ -> TradeSummaryViewModel(c.api) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(60_000)
            }
        }
    }
    val s = summary ?: return
    val c = MaterialTheme.appColors
    val active = s.pendingApprovals + s.awaitingBuyer + s.returnsToReview + s.incomingConnections + s.drafts > 0

    SectionCard("Business invoices", actionLabel = "Network", onAction = { onOpen(Routes.NETWORK) }, contentPadding = 12.dp) {
        if (s.pendingApprovals > 0) {
            CountRow(Icons.Default.MarkEmailUnread, c.warning, "${s.pendingApprovals} purchase invoices need your approval",
                highlight = true) { onOpen(Routes.tradeInvoices("PURCHASES")) }
        }
        if (s.returnsToReview > 0) {
            CountRow(Icons.AutoMirrored.Filled.AssignmentReturn, c.warning, "${s.returnsToReview} returns to review") {
                onOpen(Routes.tradeInvoices("SALES"))
            }
        }
        if (s.incomingConnections > 0) {
            CountRow(Icons.Default.PersonAdd, MaterialTheme.colorScheme.primary, "${s.incomingConnections} businesses want to connect") {
                onOpen(Routes.NETWORK)
            }
        }
        if (s.awaitingBuyer > 0) {
            CountRow(Icons.Default.HourglassTop, MaterialTheme.colorScheme.primary, "${s.awaitingBuyer} invoices waiting for the buyer") {
                onOpen(Routes.tradeInvoices("SALES"))
            }
        }
        if (s.drafts > 0) {
            CountRow(Icons.Default.EditNote, MaterialTheme.colorScheme.onSurfaceVariant, "${s.drafts} draft invoices") {
                onOpen(Routes.tradeInvoices("SALES"))
            }
        }
        if (!active) {
            Text(if (s.connectedBusinesses == 0L) "Connect with your suppliers and customers to send and receive invoices that update stock automatically."
                else "${s.connectedBusinesses} connected businesses. Nothing is waiting right now.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountRow(icon: ImageVector, tint: Color, text: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}
