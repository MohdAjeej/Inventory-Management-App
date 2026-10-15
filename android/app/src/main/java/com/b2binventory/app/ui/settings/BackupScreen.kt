package com.b2binventory.app.ui.settings

import com.b2binventory.app.ui.common.Text
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.R
import com.b2binventory.app.data.remote.ApiResult
import com.b2binventory.app.data.remote.ApiService
import com.b2binventory.app.data.remote.apiCall
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.reports.ExportSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import java.time.LocalDate

/** A backup file chosen for restore, with the business name read from its header. */
data class PickedBackup(val uri: Uri, val businessName: String?)

class BackupViewModel(private val api: ApiService, private val auth: AuthRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _restored = MutableStateFlow(false)
    val restored = _restored.asStateFlow()

    fun createBackup(context: Context, businessName: String, onSaved: (Uri) -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            val result = apiCall {
                val response = api.backup()
                if (!response.isSuccessful) throw HttpException(response)
                val body = response.body() ?: error("Empty file")
                val safe = businessName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "business" }.lowercase()
                val name = "inventory-backup-$safe-${LocalDate.now()}.json"
                withContext(Dispatchers.IO) { ExportSaver.save(context, body, name, JSON_MIME) } to name
            }
            _busy.value = false
            when (result) {
                is ApiResult.Ok -> {
                    _messages.emit(uiText(R.string.backup_saved, result.value.second))
                    onSaved(result.value.first)
                }
                is ApiResult.Err -> _messages.emit(UiText.Raw(result.message))
            }
        }
    }

    /** Reads just enough of the file to confirm it is a backup and show which business it holds. */
    suspend fun inspect(context: Context, uri: Uri): PickedBackup? = withContext(Dispatchers.IO) {
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                var n = 0
                while (n < buf.size) {
                    val read = input.read(buf, n, buf.size - n)
                    if (read < 0) break
                    n += read
                }
                String(buf, 0, n, Charsets.UTF_8)
            }
        }.getOrNull() ?: return@withContext null
        if (!head.contains("\"b2b-inventory-backup\"")) return@withContext null
        val name = BUSINESS_NAME.find(head)?.groupValues?.get(1)
        PickedBackup(uri, name)
    }

    fun notABackup() {
        _messages.tryEmit(uiText(R.string.backup_not_a_backup))
    }

    fun restore(context: Context, uri: Uri) {
        _busy.value = true
        viewModelScope.launch {
            val resolver = context.applicationContext.contentResolver
            val body = object : RequestBody() {
                override fun contentType(): MediaType = JSON_MIME.toMediaType()
                override fun writeTo(sink: BufferedSink) {
                    val input = resolver.openInputStream(uri) ?: throw java.io.IOException("Cannot open the file")
                    input.source().use { sink.writeAll(it) }
                }
            }
            val result = auth.restoreBackup(body)
            _busy.value = false
            when (result) {
                is ApiResult.Ok -> _restored.value = true
                is ApiResult.Err -> _messages.emit(UiText.Raw(result.message))
            }
        }
    }

    companion object {
        const val JSON_MIME = "application/json"
        private val BUSINESS_NAME = Regex(
            "\"business\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOption.DOT_MATCHES_ALL)
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    val vm = appViewModel { c, _ -> BackupViewModel(c.api, c.authRepository) }
    val container = appContainer()
    val me = LocalMe.current
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val restored by vm.restored.collectAsStateWithLifecycle()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<PickedBackup?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.resolve(context)) } }
    LaunchedEffect(restored) { if (restored) onRestored() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val info = vm.inspect(context, uri)
            if (info == null) vm.notABackup() else picked = info
        }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.backup_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!online) ErrorBanner(stringResource(R.string.backup_needs_connection))
            if (me?.can("BUSINESS_SETTINGS") == true) {
                SectionCard(stringResource(R.string.backup_create_title)) {
                    Text(stringResource(R.string.backup_create_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.createBackup(context, me.businessName) { ExportSaver.open(context, it, BackupViewModel.JSON_MIME) }
                        },
                        enabled = online && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Backup, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_create_action))
                    }
                }
            }
            SectionCard(stringResource(R.string.backup_restore_title)) {
                Text(stringResource(R.string.backup_restore_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf(BackupViewModel.JSON_MIME, "application/octet-stream", "text/plain")) },
                    enabled = online && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_restore_action))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    picked?.let { p ->
        ConfirmDialog(
            stringResource(R.string.backup_restore_confirm_title),
            stringResource(R.string.backup_restore_confirm_body,
                p.businessName ?: stringResource(R.string.backup_unknown_business)),
            stringResource(R.string.backup_restore_confirm_action),
            onConfirm = { picked = null; vm.restore(context, p.uri) },
            onDismiss = { picked = null },
        )
    }
}
