package com.b2binventory.app.ui.sync

import com.b2binventory.app.ui.common.Text
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.b2binventory.app.R
import com.b2binventory.app.data.sync.OfflineQueue
import com.b2binventory.app.data.sync.PendingOp
import com.b2binventory.app.data.sync.PendingType
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.launch

/** Tells the user a change was kept on the phone; a toast so it stays visible while the screen closes. */
fun showSavedOffline(context: Context) {
    Toast.makeText(context, context.getString(R.string.offline_saved), Toast.LENGTH_LONG).show()
}

/** Messages from ViewModels that use [OfflineQueue.OFFLINE_MESSAGE] are translated here. */
fun localizeMessage(context: Context, message: String): String =
    if (message == OfflineQueue.OFFLINE_MESSAGE) context.getString(R.string.offline_saved) else message

@Composable
fun pendingTypeLabel(type: PendingType): String = stringResource(
    when (type) {
        PendingType.SALE -> R.string.pending_type_sale
        PendingType.PURCHASE -> R.string.pending_type_purchase
        PendingType.SALE_PAYMENT -> R.string.pending_type_payment
        PendingType.STOCK_OPERATION -> R.string.pending_type_stock
        PendingType.TRANSFER -> R.string.pending_type_transfer
    }
)

/** Pending changes of the signed-in business. */
@Composable
fun rememberPendingOps(): List<PendingOp> {
    val container = appContainer()
    val ops by container.pendingOps.ops.collectAsStateWithLifecycle()
    val me = LocalMe.current
    return remember(ops, me?.businessId) { ops.filter { it.businessId == me?.businessId } }
}

/**
 * Shown at the bottom of every screen while offline or while changes wait to be sent. [padNavigationBar] is set
 * when no bottom navigation bar sits below it.
 */
@Composable
fun OfflineBanner(onOpenPending: () -> Unit, padNavigationBar: Boolean) {
    val container = appContainer()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val reachable by container.connectivity.serverReachable.collectAsStateWithLifecycle()
    val syncing by container.syncManager.syncing.collectAsStateWithLifecycle()
    val pending = rememberPendingOps()
    val offline = !online || !reachable
    val failed = pending.count { it.failed }
    val visible = offline || pending.isNotEmpty()

    AnimatedVisibility(visible) {
        val bg = if (failed > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        val fg = if (failed > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        Row(
            Modifier.fillMaxWidth().background(bg)
                .then(if (padNavigationBar) Modifier.navigationBarsPadding() else Modifier).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (offline) Icons.Default.CloudOff else Icons.Default.CloudSync, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val text = buildList {
                if (offline) add(stringResource(R.string.offline_banner))
                if (pending.isNotEmpty()) add(pluralStringResource(R.plurals.pending_count, pending.size, pending.size))
                if (failed > 0) add(pluralStringResource(R.plurals.pending_failed_count, failed, failed))
            }.joinToString(" · ")
            Text(text, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (syncing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = fg)
            } else if (pending.isNotEmpty()) {
                TextButton(onClick = onOpenPending, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.pending_view), color = fg)
                }
            } else if (offline) {
                TextButton(onClick = { container.syncManager.syncNow(); container.connectivity.reportServer(true) },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.action_retry), color = fg)
                }
            }
        }
    }
}

@Composable
fun PendingSyncScreen(onBack: () -> Unit) {
    val container = appContainer()
    val sync = container.syncManager
    val syncing by sync.syncing.collectAsStateWithLifecycle()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val pending = rememberPendingOps()
    val scope = rememberCoroutineScope()
    var discarding by remember { mutableStateOf<PendingOp?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.pending_title), onBack = onBack, actions = {
                if (syncing) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                else TextButton(onClick = { sync.syncNow() }, enabled = online && pending.isNotEmpty()) {
                    Text(stringResource(R.string.pending_sync_now))
                }
            })
        },
    ) { padding ->
        if (pending.isEmpty()) {
            EmptyState(stringResource(R.string.pending_empty_title), stringResource(R.string.pending_empty_body),
                Icons.Default.CloudSync, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(stringResource(R.string.pending_explainer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(pending, key = { it.id }) { op ->
                ListItem(
                    leadingContent = {
                        Icon(if (op.failed) Icons.Default.ErrorOutline else Icons.Default.Schedule, null,
                            tint = if (op.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    headlineContent = { Text(pendingTypeLabel(op.type)) },
                    supportingContent = {
                        Column {
                            Text(op.detail)
                            Text(Fmt.dateTime(op.createdAt), style = MaterialTheme.typography.bodySmall)
                            op.error?.let {
                                Text(stringResource(R.string.pending_refused, it), color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            if (op.failed) TextButton(onClick = { scope.launch { sync.retry(op.id) } }) {
                                Text(stringResource(R.string.action_retry))
                            }
                            TextButton(onClick = { discarding = op }) { Text(stringResource(R.string.pending_discard)) }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    discarding?.let { op ->
        ConfirmDialog(
            stringResource(R.string.pending_discard_title),
            stringResource(R.string.pending_discard_body, pendingTypeLabel(op.type), op.detail),
            stringResource(R.string.pending_discard),
            destructive = true,
            onConfirm = { discarding = null; scope.launch { sync.discard(op.id) } },
            onDismiss = { discarding = null },
        )
    }
}
