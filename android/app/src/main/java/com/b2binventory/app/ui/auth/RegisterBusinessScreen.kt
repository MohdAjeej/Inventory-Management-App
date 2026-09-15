package com.b2binventory.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.RegisterRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBusinessScreen(
    onRegisterSuccess: (Long, Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var businessMobile by remember { mutableStateOf("") }
    var businessEmail by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var gstNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Business") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Business Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Business Name *") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = businessType,
                    onValueChange = { businessType = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Business Type *") },
                    placeholder = { Text("e.g., Wholesale, Retail") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = businessMobile,
                    onValueChange = { businessMobile = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Business Mobile *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
            
            item {
                OutlinedTextField(
                    value = businessEmail,
                    onValueChange = { businessEmail = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Business Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }
            
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Address") },
                    minLines = 2
                )
            }
            
            item {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("City") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = state,
                    onValueChange = { state = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("State") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Country") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = gstNumber,
                    onValueChange = { gstNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GST Number") },
                    singleLine = true
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Admin User Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your Name *") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your Email *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }
            
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (businessName.isBlank() || businessType.isBlank() || businessMobile.isBlank() ||
                            name.isBlank() || email.isBlank() || password.isBlank()) {
                            message = "Please fill all required fields (*)"
                            return@Button
                        }
                        
                        loading = true
                        message = ""
                        scope.launch {
                            try {
                                val response = ApiClient.apiService.registerBusiness(
                                    RegisterRequest(
                                        businessName = businessName,
                                        businessType = businessType,
                                        businessMobile = businessMobile,
                                        businessEmail = businessEmail.ifBlank { null },
                                        address = address.ifBlank { null },
                                        city = city.ifBlank { null },
                                        state = state.ifBlank { null },
                                        country = country.ifBlank { null },
                                        gstNumber = gstNumber.ifBlank { null },
                                        name = name,
                                        email = email,
                                        password = password
                                    )
                                )
                                val businessId = (response["businessId"] as? Double)?.toLong() ?: 0L
                                val userId = (response["userId"] as? Double)?.toLong() ?: 0L
                                onRegisterSuccess(businessId, userId)
                            } catch (e: Exception) {
                                message = e.message ?: "Registration failed. Please try again."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) "Registering..." else "Register Business")
                }
            }
            
            if (message.isNotBlank()) {
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
