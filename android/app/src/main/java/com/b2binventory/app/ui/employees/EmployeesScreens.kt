package com.b2binventory.app.ui.employees

import com.b2binventory.app.ui.common.Text
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.settings.clickableItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val roles = listOf("ADMIN", "INVENTORY_MANAGER", "STAFF")

class EmployeesViewModel(private val api: ApiService) : ViewModel() {
    private val _members = MutableStateFlow(Loadable<List<Employee>>())
    val members = _members.asStateFlow()
    private val _invitations = MutableStateFlow(Loadable<List<Invitation>>())
    val invitations = _invitations.asStateFlow()
    private val _created = MutableStateFlow<Invitation?>(null)
    val created = _created.asStateFlow()
    private val _inviteError = MutableStateFlow<Map<String, String>>(emptyMap())
    val inviteError = _inviteError.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun load() {
        _members.load(viewModelScope) { api.employees().content }
        _invitations.load(viewModelScope) { api.invitations().content }
    }

    fun invite(email: String, role: String, onSuccess: () -> Unit) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _inviteError.value = mapOf("email" to "Enter a valid email")
            return
        }
        viewModelScope.launch {
            when (val r = apiCall { api.invite(InviteRequest(email.trim(), role)) }) {
                is ApiResult.Ok -> {
                    _inviteError.value = emptyMap()
                    _created.value = r.value
                    onSuccess()
                    load()
                }
                is ApiResult.Err -> _inviteError.value = r.fieldErrors.ifEmpty { mapOf("email" to r.message) }
            }
        }
    }

    fun dismissCreated() {
        _created.value = null
    }

    fun cancel(id: Long) {
        viewModelScope.launch {
            when (val r = apiCall { api.cancelInvitation(id).requireSuccess() }) {
                is ApiResult.Ok -> {
                    _messages.emit("Invitation cancelled")
                    load()
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }
}

@Composable
fun EmployeesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> EmployeesViewModel(c.api) }
    val members by vm.members.collectAsStateWithLifecycle()
    val invitations by vm.invitations.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()
    val inviteError by vm.inviteError.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showInvite by remember { mutableStateOf(false) }
    var cancelTarget by remember { mutableStateOf<Invitation?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(
        topBar = { AppTopBar("Employees", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showInvite = true }, icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Invite") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Members") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Pending invitations") })
            }
            if (tab == 0) {
                LoadableContent(members, vm::load) { list ->
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(list, key = { it.membershipId }) { e ->
                            ListItem(
                                headlineContent = { Text(e.fullName) },
                                supportingContent = { Text("${e.email}\n${Fmt.role(e.role)}") },
                                leadingContent = { Icon(Icons.Default.Person, null) },
                                trailingContent = {
                                    if (e.status != "ACTIVE") AssistChip(onClick = {}, label = { Text("Deactivated") })
                                },
                                modifier = Modifier.clickableItem { onOpen(e.membershipId) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                LoadableContent(invitations, vm::load) { list ->
                    if (list.isEmpty()) {
                        EmptyState("No pending invitations", "Invite team members with the Invite button.", Icons.Default.MailOutline)
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(list, key = { it.id }) { i ->
                                ListItem(
                                    headlineContent = { Text(i.email) },
                                    supportingContent = { Text("${Fmt.role(i.role)} · code ${i.code} · expires ${Fmt.dateTime(i.expiresAt)}") },
                                    trailingContent = {
                                        IconButton(onClick = { cancelTarget = i }) { Icon(Icons.Default.Close, "Cancel invitation") }
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

    if (showInvite) InviteDialog(inviteError, onInvite = { email, role -> vm.invite(email, role) { showInvite = false } },
        onDismiss = { showInvite = false })
    created?.let { CodeDialog(it, vm::dismissCreated) }
    cancelTarget?.let { inv ->
        ConfirmDialog("Cancel invitation?", "The code for ${inv.email} will stop working.", "Cancel invitation", true,
            onConfirm = { vm.cancel(inv.id); cancelTarget = null }, onDismiss = { cancelTarget = null })
    }
}

@Composable
private fun InviteDialog(errors: Map<String, String>, onInvite: (String, String) -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("STAFF") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite a team member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(email, { email = it }, "Email", error = errors["email"] ?: errors["role"], keyboardType = KeyboardType.Email)
                DropdownField("Role", roles, role, { role = it }, Fmt::role)
                Text("You will get a code to share with them. They enter it on the sign-in screen.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onInvite(email, role) }) { Text("Create invitation") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CodeDialog(invitation: Invitation, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val me = LocalMe.current
    val message = "You're invited to join ${me?.businessName} on the Inventory app as ${Fmt.role(invitation.role)}. Open the app, tap \"I have an invitation code\" and enter: ${invitation.code}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitation created") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Share this code with ${invitation.email}:")
                Text(invitation.code, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Valid until ${Fmt.dateTime(invitation.expiresAt)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }, "Share invitation"))
            }) { Text("Share") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(invitation.code)) }) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
    )
}

class EmployeeDetailViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val id: Long = checkNotNull(saved["id"])
    private val _employee = MutableStateFlow(Loadable<Employee>())
    val employee = _employee.asStateFlow()
    private val _catalog = MutableStateFlow<PermissionCatalog?>(null)
    val catalog = _catalog.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
        viewModelScope.launch { (apiCall { api.permissionCatalog() } as? ApiResult.Ok)?.let { _catalog.value = it.value } }
    }

    fun load() = _employee.load(viewModelScope) { api.employee(id) }

    private fun mutate(success: String, block: suspend () -> Employee) {
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> {
                    _employee.value = Loadable(r.value, false)
                    _messages.emit(success)
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }

    fun changeRole(role: String) = mutate("Role updated") { api.changeRole(id, ChangeRoleRequest(role)) }

    fun setPermission(permission: String, granted: Boolean) {
        val current = _employee.value.data ?: return
        val overrides = current.overrides.toMutableMap().apply { put(permission, granted) }
        mutate("Permissions updated") { api.setPermissions(id, PermissionOverridesRequest(overrides)) }
    }

    fun resetPermissions() = mutate("Restored role defaults") { api.setPermissions(id, PermissionOverridesRequest(emptyMap())) }

    fun deactivate() = mutate("Employee deactivated") { api.deactivate(id) }

    fun reactivate() = mutate("Employee reactivated") { api.reactivate(id) }
}

@Composable
fun EmployeeDetailScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, s -> EmployeeDetailViewModel(c.api, s) }
    val state by vm.employee.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDeactivate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(topBar = { AppTopBar("Employee", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LoadableContent(state, vm::load) { e ->
            val isSelf = e.membershipId == me?.membershipId
            FormColumn(padding) {
                SectionCard {
                    Text(e.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(e.email)
                    e.phone?.let { Text(it) }
                    Text(if (e.status == "ACTIVE") "Active" else "Deactivated",
                        color = if (e.status == "ACTIVE") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                }
                SectionCard("Role") {
                    DropdownField("Role", roles, e.role, { if (it != e.role) vm.changeRole(it) }, Fmt::role, enabled = !isSelf)
                    if (isSelf) Text("You cannot change your own role.", style = MaterialTheme.typography.bodySmall)
                }
                catalog?.let { c ->
                    SectionCard("Permissions") {
                        Text("Defaults come from the role. Changes here apply only to this person.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        c.permissions.forEach { p ->
                            val custom = e.overrides.containsKey(p.permission)
                            SwitchRow(p.label, p.permission in e.permissions, { vm.setPermission(p.permission, it) },
                                if (custom) "Custom for this person" else null,
                                enabled = !isSelf && !(e.role == "ADMIN" && p.permission == "EMPLOYEE_MANAGE"))
                        }
                        if (e.overrides.isNotEmpty() && !isSelf) {
                            TextButton(onClick = vm::resetPermissions) { Text("Restore role defaults") }
                        }
                    }
                }
                if (!isSelf) {
                    if (e.status == "ACTIVE") {
                        OutlinedButton(onClick = { confirmDeactivate = true }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text("Deactivate account")
                        }
                    } else {
                        Button(onClick = vm::reactivate, modifier = Modifier.fillMaxWidth()) { Text("Reactivate account") }
                    }
                }
            }
            if (confirmDeactivate) {
                ConfirmDialog("Deactivate ${e.fullName}?", "They will be signed out immediately and lose access to this business.",
                    "Deactivate", true, onConfirm = { confirmDeactivate = false; vm.deactivate() },
                    onDismiss = { confirmDeactivate = false })
            }
        }
    }
}
