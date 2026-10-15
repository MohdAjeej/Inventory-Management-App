package com.b2binventory.app.ui.settings

import com.b2binventory.app.ui.common.Text
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.ui.auth.FormState
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BusinessSettingsViewModel(
    private val api: ApiService,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _business = MutableStateFlow(Loadable<Business>())
    val business = _business.asStateFlow()
    private val _settings = MutableStateFlow(Loadable<BusinessSettings>())
    val settings = _settings.asStateFlow()
    private val _form = MutableStateFlow(FormState())
    val form = _form.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _business.load(viewModelScope) { api.business() }
        _settings.load(viewModelScope) { api.settings() }
    }

    fun saveBusiness(request: UpdateBusinessRequest) {
        if (request.name.isBlank()) {
            _form.value = FormState(fieldErrors = mapOf("name" to "Business name is required"))
            return
        }
        _form.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val r = apiCall { api.updateBusiness(request) }) {
                is ApiResult.Ok -> {
                    _business.value = Loadable(r.value, false)
                    _form.value = FormState()
                    auth.refreshMe()
                    _messages.emit("Business details saved")
                }
                is ApiResult.Err -> _form.value = FormState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }

    fun saveSettings(settings: BusinessSettings) {
        viewModelScope.launch {
            when (val r = apiCall { api.updateSettings(settings) }) {
                is ApiResult.Ok -> {
                    _settings.value = Loadable(r.value, false)
                    _messages.emit("Preferences saved")
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }

    fun uploadLogo(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val part = ImageParts.fromUri(context, uri) ?: run {
                _messages.emit("Could not read the selected image")
                return@launch
            }
            when (val r = apiCall { api.uploadLogo(part) }) {
                is ApiResult.Ok -> {
                    _business.value = Loadable(r.value, false)
                    _messages.emit("Logo updated")
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }
}

@Composable
fun BusinessSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> BusinessSettingsViewModel(c.api, c.authRepository) }
    val container = appContainer()
    val business by vm.business.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val canEdit = me?.can("BUSINESS_SETTINGS") == true
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.uploadLogo(context, uri)
    }

    Scaffold(topBar = { AppTopBar("Business settings", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LoadableContent(business, vm::load) { b ->
            var name by remember(b) { mutableStateOf(b.name) }
            var type by remember(b) { mutableStateOf(b.businessType ?: "") }
            var email by remember(b) { mutableStateOf(b.email ?: "") }
            var phone by remember(b) { mutableStateOf(b.phone ?: "") }
            var address by remember(b) { mutableStateOf(b.address ?: "") }
            var city by remember(b) { mutableStateOf(b.city ?: "") }
            var state by remember(b) { mutableStateOf(b.state ?: "") }
            var country by remember(b) { mutableStateOf(b.country ?: "") }
            var postal by remember(b) { mutableStateOf(b.postalCode ?: "") }
            FormColumn(padding) {
                if (!canEdit) {
                    Text("Only members with the business settings permission can change these details.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SectionCard("Business profile") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (b.logoUrl != null) {
                            AsyncImage(container.fileUrl(b.logoUrl), "Logo", Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Store, null, Modifier.size(64.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        if (canEdit) {
                            OutlinedButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text(if (b.logoUrl == null) "Add logo" else "Change logo") }
                        }
                    }
                    ErrorBanner(form.error)
                    val e = form.fieldErrors
                    FormField(name, { name = it }, "Business name", required = true, error = e["name"], enabled = canEdit)
                    FormField(type, { type = it }, "Type of business", enabled = canEdit,
                        supporting = "Descriptive only; every feature is available to every type of business")
                    FormField(email, { email = it }, "Email", error = e["email"], keyboardType = KeyboardType.Email, enabled = canEdit)
                    FormField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone, enabled = canEdit)
                    FormField(address, { address = it }, "Address", enabled = canEdit, singleLine = false)
                    FormField(city, { city = it }, "City", enabled = canEdit)
                    FormField(state, { state = it }, "State / region", enabled = canEdit)
                    FormField(country, { country = it }, "Country", enabled = canEdit)
                    FormField(postal, { postal = it }, "Postal code", enabled = canEdit)
                    if (canEdit) {
                        PrimaryButton("Save business details", {
                            vm.saveBusiness(UpdateBusinessRequest(name, type.blankToNull(), email.blankToNull(), phone.blankToNull(),
                                address.blankToNull(), city.blankToNull(), state.blankToNull(), country.blankToNull(), postal.blankToNull()))
                        }, loading = form.submitting)
                    }
                }
                settings.data?.let { s -> PreferencesCard(s, canEdit, vm::saveSettings) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val commonZones = listOf("Asia/Kolkata", "Asia/Dubai", "Asia/Singapore", "Asia/Karachi", "Asia/Dhaka",
    "Europe/London", "Europe/Berlin", "America/New_York", "America/Chicago", "America/Los_Angeles", "Australia/Sydney", "UTC",
    java.time.ZoneId.systemDefault().id)

private val commonCurrencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SAR", "SGD", "PKR", "BDT", "AUD", "CAD")

@Composable
private fun PreferencesCard(s: BusinessSettings, canEdit: Boolean, onSave: (BusinessSettings) -> Unit) {
    var current by remember(s) { mutableStateOf(s) }
    var threshold by remember(s) { mutableStateOf(s.defaultLowStockThreshold.toString()) }
    val thresholdValue = threshold.toIntOrNull()
    SectionCard("Inventory preferences") {
        FormField(threshold, { threshold = it.filter(Char::isDigit) }, "Default low-stock threshold",
            keyboardType = KeyboardType.Number, enabled = canEdit,
            error = if (thresholdValue == null) "Enter a number" else null,
            supporting = "Used when a product has no minimum stock of its own")
        SwitchRow("Low-stock alerts", current.notifyLowStock, { current = current.copy(notifyLowStock = it) },
            "Notify the team when products run low or out", enabled = canEdit)
        SwitchRow("Allow negative stock", current.allowNegativeStock, { current = current.copy(allowNegativeStock = it) },
            "Permit reductions below zero (not recommended)", enabled = canEdit)
        SwitchRow("Batch / lot tracking", current.trackBatches, { current = current.copy(trackBatches = it) },
            "Record lot numbers for products that need them", enabled = canEdit)
        SwitchRow("Expiry dates", current.trackExpiry, { current = current.copy(trackExpiry = it) },
            "Require an expiry date for each batch", enabled = canEdit && current.trackBatches)
        SwitchRow("Serial numbers", current.trackSerials, { current = current.copy(trackSerials = it) },
            "Track individual items by serial number", enabled = canEdit)
        SwitchRow("Damaged & reserved stock", current.trackDamagedReserved, { current = current.copy(trackDamagedReserved = it) },
            "Separate unusable or held stock from available stock", enabled = canEdit)
        val zones = remember(current.timezone) { (commonZones + current.timezone).distinct() }
        DropdownField("Timezone", zones, current.timezone, { current = current.copy(timezone = it) }, { it }, enabled = canEdit)
        Text("Days in sales reports start and end at midnight in this timezone.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val currencies = remember(current.currencyCode) { (commonCurrencies + current.currencyCode).distinct() }
        DropdownField("Currency", currencies, current.currencyCode, { current = current.copy(currencyCode = it) }, { it },
            enabled = canEdit)
        if (canEdit) {
            Button(onClick = {
                onSave(current.copy(defaultLowStockThreshold = thresholdValue ?: s.defaultLowStockThreshold,
                    trackExpiry = current.trackExpiry && current.trackBatches))
            }, enabled = thresholdValue != null, modifier = Modifier.fillMaxWidth()) { Text("Save preferences") }
        }
    }
}
