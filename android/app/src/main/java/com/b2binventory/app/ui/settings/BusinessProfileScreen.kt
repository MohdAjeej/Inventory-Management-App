package com.b2binventory.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Business
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(
    businessId: Long,
    onNavigateBack: () -> Unit
) {
    var business by remember { mutableStateOf<Business?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Edit fields
    var editName by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("") }
    var editMobile by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editAddress by remember { mutableStateOf("") }
    var editCity by remember { mutableStateOf("") }
    var editState by remember { mutableStateOf("") }
    var editCountry by remember { mutableStateOf("") }
    var editGstNumber by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    // Load business data
    fun loadBusiness() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                business = ApiClient.apiService.getBusinessById(businessId)
                business?.let {
                    editName = it.name ?: ""
                    editType = it.type ?: ""
                    editMobile = it.mobile ?: ""
                    editEmail = it.email ?: ""
                    editAddress = it.address ?: ""
                    editCity = it.city ?: ""
                    editState = it.state ?: ""
                    editCountry = it.country ?: ""
                    editGstNumber = it.gstNumber ?: ""
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load business: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadBusiness()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isEditing) "Edit Business Profile" else "Business Profile",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            isEditing = false
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!isLoading && business != null) {
                        if (isEditing) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        // TODO: Implement update API call
                                        isEditing = false
                                        loadBusiness()
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to update: ${e.message}"
                                    }
                                }
                            }) {
                                Text("Save", color = Color.White)
                            }
                        } else {
                            TextButton(onClick = { isEditing = true }) {
                                Text("Edit", color = Color.White)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { loadBusiness() }) {
                            Text("Retry")
                        }
                    }
                }
                business != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isEditing) {
                            // Edit Mode
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Business Name*") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = editType,
                                onValueChange = { editType = it },
                                label = { Text("Business Type") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = editMobile,
                                onValueChange = { editMobile = it },
                                label = { Text("Mobile Number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = editEmail,
                                onValueChange = { editEmail = it },
                                label = { Text("Email") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = editAddress,
                                onValueChange = { editAddress = it },
                                label = { Text("Address") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editCity,
                                    onValueChange = { editCity = it },
                                    label = { Text("City") },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                OutlinedTextField(
                                    value = editState,
                                    onValueChange = { editState = it },
                                    label = { Text("State") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            OutlinedTextField(
                                value = editCountry,
                                onValueChange = { editCountry = it },
                                label = { Text("Country") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = editGstNumber,
                                onValueChange = { editGstNumber = it },
                                label = { Text("GST Number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // View Mode
                            business!!.name?.let { InfoCard("Business Name", it) }
                            business!!.type?.let { InfoCard("Business Type", it) }
                            business!!.mobile?.let { InfoCard("Mobile", it) }
                            business!!.email?.let { InfoCard("Email", it) }
                            business!!.address?.let { InfoCard("Address", it) }
                            
                            if (business!!.city != null || business!!.state != null) {
                                InfoCard(
                                    "Location",
                                    "${business!!.city ?: ""}, ${business!!.state ?: ""}"
                                )
                            }
                            
                            business!!.country?.let { InfoCard("Country", it) }
                            business!!.gstNumber?.let { InfoCard("GST Number", it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
