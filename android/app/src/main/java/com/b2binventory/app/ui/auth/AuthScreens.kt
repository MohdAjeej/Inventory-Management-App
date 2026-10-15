package com.b2binventory.app.ui.auth

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.b2binventory.app.ui.common.*

@Composable
fun LoginScreen(onSignedIn: () -> Unit, onRegister: () -> Unit, onJoin: () -> Unit) {
    val vm = appViewModel { c, _ -> LoginViewModel(c.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.done) { if (state.done) onSignedIn() }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.Inventory, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Inventory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Sign in to manage your stock", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            ErrorBanner(state.error)
            FormField(email, { email = it }, "Email", error = state.fieldErrors["email"], keyboardType = KeyboardType.Email)
            FormField(password, { password = it }, "Password", error = state.fieldErrors["password"], password = true)
            PrimaryButton("Sign in", { vm.login(email, password) }, loading = state.submitting)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Register a new business") }
            TextButton(onClick = onJoin) { Text("I have an invitation code") }
        }
    }
}

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> RegisterViewModel(c.authRepository) }
    val form by vm.form.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val step by vm.step.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onRegistered() }
    val e = state.fieldErrors

    Scaffold(topBar = {
        AppTopBar(if (step == 0) "Your business (1 of 2)" else "Your account (2 of 2)",
            onBack = { if (step == 1) vm.back() else onBack() })
    }) { padding ->
        FormColumn(padding) {
            ErrorBanner(state.error)
            if (step == 0) {
                Text("Tell us about the business. Any kind of business can use this app, and you can change these details later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(form.businessName, { v -> vm.update { it.copy(businessName = v) } }, "Business name", required = true, error = e["businessName"])
                FormField(form.businessType, { v -> vm.update { it.copy(businessType = v) } }, "Type of business",
                    placeholder = "e.g. Pharmacy, Bookstore, Hardware, Bakery", error = e["businessType"])
                FormField(form.businessPhone, { v -> vm.update { it.copy(businessPhone = v) } }, "Business phone", keyboardType = KeyboardType.Phone, error = e["businessPhone"])
                FormField(form.businessEmail, { v -> vm.update { it.copy(businessEmail = v) } }, "Business email", keyboardType = KeyboardType.Email, error = e["businessEmail"])
                FormField(form.city, { v -> vm.update { it.copy(city = v) } }, "City")
                FormField(form.country, { v -> vm.update { it.copy(country = v) } }, "Country")
                PrimaryButton("Continue", vm::next)
            } else {
                Text("You will be the administrator of this business.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(form.fullName, { v -> vm.update { it.copy(fullName = v) } }, "Your full name", required = true, error = e["fullName"])
                FormField(form.email, { v -> vm.update { it.copy(email = v) } }, "Email", required = true, keyboardType = KeyboardType.Email, error = e["email"])
                FormField(form.phone, { v -> vm.update { it.copy(phone = v) } }, "Phone", keyboardType = KeyboardType.Phone)
                FormField(form.password, { v -> vm.update { it.copy(password = v) } }, "Password", required = true, password = true,
                    error = e["password"], supporting = "At least 8 characters")
                FormField(form.confirm, { v -> vm.update { it.copy(confirm = v) } }, "Confirm password", required = true, password = true, error = e["confirm"])
                PrimaryButton("Create business", vm::submit, loading = state.submitting)
            }
        }
    }
}

@Composable
fun JoinScreen(onJoined: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> JoinViewModel(c.api, c.authRepository) }
    val preview by vm.preview.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.done) { if (state.done) onJoined() }
    val e = state.fieldErrors

    Scaffold(topBar = { AppTopBar("Join a business", onBack = onBack) }) { padding ->
        FormColumn(padding) {
            ErrorBanner(state.error)
            val p = preview
            if (p == null) {
                Text("Enter the invitation code your administrator shared with you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(code, { code = it.uppercase() }, "Invitation code", error = e["code"])
                PrimaryButton("Continue", { vm.check(code) }, loading = state.submitting)
            } else {
                SectionCard {
                    Text(p.businessName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("You are invited as ${Fmt.role(p.role)}")
                    Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.existingUser) {
                    Text("You already have an account. Enter its password to join.", textAlign = TextAlign.Start)
                    FormField(password, { password = it }, "Password", required = true, password = true, error = e["password"])
                } else {
                    FormField(fullName, { fullName = it }, "Your full name", required = true, error = e["fullName"])
                    FormField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone)
                    FormField(password, { password = it }, "Choose a password", required = true, password = true, error = e["password"],
                        supporting = "At least 8 characters")
                    FormField(confirm, { confirm = it }, "Confirm password", required = true, password = true, error = e["confirm"])
                }
                PrimaryButton("Join ${p.businessName}", { vm.join(code, fullName, phone, password, confirm) }, loading = state.submitting)
                TextButton(onClick = vm::reset) { Text("Use a different code") }
            }
        }
    }
}
