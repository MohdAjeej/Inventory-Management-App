package com.b2binventory.app.ui.settings

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.data.session.SessionStore
import com.b2binventory.app.ui.auth.FormState
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.theme.appColors
import androidx.compose.material.icons.automirrored.filled.Logout
import com.b2binventory.app.ui.sync.rememberPendingOps
import com.b2binventory.app.R
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val api: ApiService,
    private val auth: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _profile = MutableStateFlow(FormState())
    val profile = _profile.asStateFlow()
    private val _password = MutableStateFlow(FormState())
    val password = _password.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _switched = MutableStateFlow(false)
    val switched = _switched.asStateFlow()

    fun saveProfile(name: String, phone: String) {
        if (name.isBlank()) {
            _profile.value = FormState(fieldErrors = mapOf("fullName" to "Name is required"))
            return
        }
        _profile.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val r = apiCall { api.updateProfile(UpdateProfileRequest(name.trim(), phone.blankToNull())) }) {
                is ApiResult.Ok -> {
                    sessionStore.updateMe(r.value)
                    _profile.value = FormState()
                    _messages.emit("Profile saved")
                }
                is ApiResult.Err -> _profile.value = FormState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }

    fun changePassword(current: String, new: String, confirm: String, onDone: () -> Unit) {
        val errors = buildMap {
            if (current.isBlank()) put("currentPassword", "Enter your current password")
            if (new.length < 8) put("newPassword", "At least 8 characters")
            if (confirm != new) put("confirm", "Passwords do not match")
        }
        if (errors.isNotEmpty()) {
            _password.value = FormState(fieldErrors = errors)
            return
        }
        _password.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val r = apiCall { api.changePassword(ChangePasswordRequest(current, new)).requireSuccess() }) {
                is ApiResult.Ok -> {
                    _password.value = FormState()
                    _messages.emit("Password changed. Please sign in again.")
                    auth.logout()
                    onDone()
                }
                is ApiResult.Err -> _password.value = FormState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }

    fun switchTo(businessId: Long) {
        viewModelScope.launch {
            when (val r = auth.switchBusiness(businessId)) {
                is ApiResult.Ok -> _switched.value = true
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onSwitched: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { c, _ -> ProfileViewModel(c.api, c.authRepository, c.sessionStore) }
    val me = LocalMe.current ?: return
    val profile by vm.profile.collectAsStateWithLifecycle()
    val password by vm.password.collectAsStateWithLifecycle()
    val switched by vm.switched.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LaunchedEffect(switched) { if (switched) onSwitched() }
    val unsynced = rememberPendingOps().size
    var confirmLogout by remember { mutableStateOf(false) }
    if (confirmLogout) {
        ConfirmDialog("Sign out?", pluralStringResource(R.plurals.logout_pending_warning, unsynced, unsynced), "Sign out",
            destructive = true, onConfirm = { confirmLogout = false; vm.logout(onSignedOut) },
            onDismiss = { confirmLogout = false })
    }

    var name by rememberSaveable { mutableStateOf(me.fullName) }
    var phone by rememberSaveable { mutableStateOf(me.phone ?: "") }
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { AppTopBar("Profile", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        FormColumn(padding) {
            SectionCard("Your details") {
                ErrorBanner(profile.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me.fullName, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        androidx.compose.material3.Text(me.email, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Fmt.role(me.role)} at ${me.businessName}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FormField(name, { name = it }, "Full name", required = true, error = profile.fieldErrors["fullName"])
                FormField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone)
                PrimaryButton("Save", { vm.saveProfile(name, phone) }, loading = profile.submitting)
            }
            if (me.memberships.size > 1) {
                SectionCard("Your businesses") {
                    me.memberships.forEach { m ->
                        val current = m.businessId == me.businessId
                        AppCard(containerColor = if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                            else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(m.businessName, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    androidx.compose.material3.Text(m.businessName, style = MaterialTheme.typography.titleSmall)
                                    m.businessType?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    RoleBadge(m.role)
                                }
                                if (current) Pill("Current", MaterialTheme.appColors.success, Icons.Default.Check)
                                else FilledTonalButton(onClick = { vm.switchTo(m.businessId) }) { Text("Switch") }
                            }
                        }
                    }
                }
            }
            SectionCard("Change password") {
                ErrorBanner(password.error)
                FormField(current, { current = it }, "Current password", password = true, error = password.fieldErrors["currentPassword"])
                FormField(new, { new = it }, "New password", password = true, error = password.fieldErrors["newPassword"])
                FormField(confirm, { confirm = it }, "Confirm new password", password = true, error = password.fieldErrors["confirm"])
                OutlinedButton(onClick = { vm.changePassword(current, new, confirm, onSignedOut) },
                    enabled = !password.submitting, modifier = Modifier.fillMaxWidth()) { Text("Change password") }
            }
            DangerButton("Sign out", { if (unsynced > 0) confirmLogout = true else vm.logout(onSignedOut) },
                icon = Icons.AutoMirrored.Filled.Logout)
            Spacer(Modifier.height(24.dp))
        }
    }
}
