package com.b2binventory.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.ui.common.blankToNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FormState(
    val submitting: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val done: Boolean = false,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(FormState())
    val state = _state.asStateFlow()

    fun login(email: String, password: String) {
        val errors = buildMap {
            if (email.isBlank()) put("email", "Email is required")
            if (password.isBlank()) put("password", "Password is required")
        }
        if (errors.isNotEmpty()) {
            _state.value = FormState(fieldErrors = errors)
            return
        }
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            _state.value = when (val r = auth.login(email, password)) {
                is ApiResult.Ok -> FormState(done = true)
                is ApiResult.Err -> FormState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }
}

data class RegisterForm(
    val businessName: String = "",
    val businessType: String = "",
    val businessPhone: String = "",
    val businessEmail: String = "",
    val city: String = "",
    val country: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirm: String = "",
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(RegisterForm())
    val form = _form.asStateFlow()
    private val _state = MutableStateFlow(FormState())
    val state = _state.asStateFlow()
    private val _step = MutableStateFlow(0)
    val step = _step.asStateFlow()

    fun update(transform: (RegisterForm) -> RegisterForm) = _form.update(transform)

    fun next() {
        val f = _form.value
        val errors = buildMap {
            if (f.businessName.isBlank()) put("businessName", "Business name is required")
            if (f.businessEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(f.businessEmail.trim()).matches()) {
                put("businessEmail", "Email is invalid")
            }
        }
        _state.value = FormState(fieldErrors = errors)
        if (errors.isEmpty()) _step.value = 1
    }

    fun back() {
        _step.value = 0
    }

    fun submit() {
        val f = _form.value
        val errors = buildMap {
            if (f.fullName.isBlank()) put("fullName", "Your name is required")
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(f.email.trim()).matches()) put("email", "Enter a valid email")
            if (f.password.length < 8) put("password", "Password must be at least 8 characters")
            if (f.confirm != f.password) put("confirm", "Passwords do not match")
        }
        if (errors.isNotEmpty()) {
            _state.value = FormState(fieldErrors = errors)
            return
        }
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            val request = RegisterRequest(
                businessName = f.businessName.trim(), businessType = f.businessType.blankToNull(),
                businessEmail = f.businessEmail.blankToNull(), businessPhone = f.businessPhone.blankToNull(),
                city = f.city.blankToNull(), country = f.country.blankToNull(), fullName = f.fullName.trim(),
                email = f.email.trim(), phone = f.phone.blankToNull(), password = f.password,
                timezone = java.time.ZoneId.systemDefault().id,
            )
            _state.value = when (val r = auth.register(request)) {
                is ApiResult.Ok -> FormState(done = true)
                is ApiResult.Err -> {
                    if (r.fieldErrors.keys.any { it.startsWith("business") }) _step.value = 0
                    FormState(error = r.message, fieldErrors = r.fieldErrors)
                }
            }
        }
    }
}

class JoinViewModel(private val api: ApiService, private val auth: AuthRepository) : ViewModel() {
    private val _preview = MutableStateFlow<InvitationPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _state = MutableStateFlow(FormState())
    val state = _state.asStateFlow()

    fun check(code: String) {
        if (code.isBlank()) {
            _state.value = FormState(fieldErrors = mapOf("code" to "Enter the invitation code"))
            return
        }
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val r = apiCall { api.previewInvitation(code.trim().uppercase()) }) {
                is ApiResult.Ok -> {
                    _preview.value = r.value
                    _state.value = FormState()
                }
                is ApiResult.Err -> _state.value = FormState(fieldErrors = mapOf("code" to r.message))
            }
        }
    }

    fun reset() {
        _preview.value = null
        _state.value = FormState()
    }

    fun join(code: String, fullName: String, phone: String, password: String, confirm: String) {
        val existing = _preview.value?.existingUser == true
        val errors = buildMap {
            if (!existing && fullName.isBlank()) put("fullName", "Your name is required")
            if (password.length < 8) put("password", "Password must be at least 8 characters")
            if (!existing && confirm != password) put("confirm", "Passwords do not match")
        }
        if (errors.isNotEmpty()) {
            _state.value = FormState(fieldErrors = errors)
            return
        }
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            val request = AcceptInvitationRequest(code.trim().uppercase(), fullName.blankToNull(), phone.blankToNull(), password)
            _state.value = when (val r = auth.acceptInvitation(request)) {
                is ApiResult.Ok -> FormState(done = true)
                is ApiResult.Err -> FormState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }
}
