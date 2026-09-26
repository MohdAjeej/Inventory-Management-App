package com.b2binventory.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.RegisterRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBusinessScreenPro(
    onRegisterSuccess: (Long, Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // Step 1: Business Info
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var selectedBusinessType by remember { mutableStateOf("") }
    var gstNumber by remember { mutableStateOf("") }
    var businessMobile by remember { mutableStateOf("") }
    var businessEmail by remember { mutableStateOf("") }
    var showBusinessTypeDropdown by remember { mutableStateOf(false) }
    
    // Step 2: Address
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    
    // Step 3: Admin Account
    var adminName by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var adminMobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    val businessTypes = listOf(
        "Retail Shop",
        "Wholesale",
        "Manufacturing",
        "Distribution",
        "E-commerce",
        "Restaurant/Cafe",
        "Pharmacy",
        "Electronics",
        "Clothing & Apparel",
        "Grocery Store",
        "Hardware Store",
        "Furniture",
        "Automotive",
        "Beauty & Cosmetics",
        "Bookstore",
        "Toy Store",
        "Jewelry",
        "Other"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF2196F3),
                                Color(0xFF64B5F6)
                            )
                        )
                    )
                    .padding(top = 8.dp)
            ) {
                Column {
                    // Back button and title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "9:41",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                    
                    // Logo and branding
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(text = "📦", fontSize = 28.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "B2B Inventory",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "MANAGEMENT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White.copy(alpha = 0.9f),
                                letterSpacing = 2.sp
                            )
                        }
                    }
                    
                    Text(
                        text = "Manage Today. Grow Tomorrow.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Create Your Business",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                
                Text(
                    text = "Register your business to start managing your inventory.",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )
                
                // Step Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StepIndicator(
                        number = 1,
                        label = "Business Info",
                        isActive = currentStep == 1,
                        isCompleted = currentStep > 1,
                        modifier = Modifier.weight(1f)
                    )
                    StepIndicator(
                        number = 2,
                        label = "Address",
                        isActive = currentStep == 2,
                        isCompleted = currentStep > 2,
                        modifier = Modifier.weight(1f)
                    )
                    StepIndicator(
                        number = 3,
                        label = "Admin Account",
                        isActive = currentStep == 3,
                        isCompleted = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Step header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (currentStep) {
                                    1 -> "Business Information"
                                    2 -> "Address Details"
                                    3 -> "Admin Account"
                                    else -> ""
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                            Text(
                                text = "Step $currentStep of 3",
                                fontSize = 13.sp,
                                color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Step content with animation
                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                        slideOutHorizontally { -it } + fadeOut()
                            },
                            label = "step_content"
                        ) { step ->
                            when (step) {
                                1 -> Step1BusinessInfo(
                                    businessName = businessName,
                                    onBusinessNameChange = { businessName = it; errorMessage = "" },
                                    selectedBusinessType = selectedBusinessType,
                                    onBusinessTypeChange = { selectedBusinessType = it; errorMessage = "" },
                                    businessTypes = businessTypes,
                                    showDropdown = showBusinessTypeDropdown,
                                    onShowDropdownChange = { showBusinessTypeDropdown = it },
                                    gstNumber = gstNumber,
                                    onGstNumberChange = { gstNumber = it },
                                    businessMobile = businessMobile,
                                    onBusinessMobileChange = { businessMobile = it; errorMessage = "" },
                                    businessEmail = businessEmail,
                                    onBusinessEmailChange = { businessEmail = it },
                                    focusManager = focusManager
                                )
                                
                                2 -> Step2Address(
                                    address = address,
                                    onAddressChange = { address = it; errorMessage = "" },
                                    city = city,
                                    onCityChange = { city = it },
                                    state = state,
                                    onStateChange = { state = it },
                                    pincode = pincode,
                                    onPincodeChange = { pincode = it },
                                    country = country,
                                    onCountryChange = { country = it },
                                    focusManager = focusManager
                                )
                                
                                3 -> Step3AdminAccount(
                                    adminName = adminName,
                                    onAdminNameChange = { adminName = it; errorMessage = "" },
                                    adminEmail = adminEmail,
                                    onAdminEmailChange = { adminEmail = it; errorMessage = "" },
                                    adminMobile = adminMobile,
                                    onAdminMobileChange = { adminMobile = it },
                                    password = password,
                                    onPasswordChange = { password = it; errorMessage = "" },
                                    confirmPassword = confirmPassword,
                                    onConfirmPasswordChange = { confirmPassword = it; errorMessage = "" },
                                    passwordVisible = passwordVisible,
                                    onPasswordVisibleChange = { passwordVisible = it },
                                    confirmPasswordVisible = confirmPasswordVisible,
                                    onConfirmPasswordVisibleChange = { confirmPasswordVisible = it },
                                    focusManager = focusManager
                                )
                            }
                        }
                        
                        // Error message
                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage,
                                        color = Color(0xFFD32F2F),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Navigation buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (currentStep > 1) Arrangement.SpaceBetween else Arrangement.End
                        ) {
                            if (currentStep > 1) {
                                OutlinedButton(
                                    onClick = { 
                                        currentStep--
                                        errorMessage = ""
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Back", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            
                            Button(
                                onClick = {
                                    when (currentStep) {
                                        1 -> {
                                            if (businessName.isBlank()) {
                                                errorMessage = "❌ Please enter business name"
                                                return@Button
                                            }
                                            if (selectedBusinessType.isBlank()) {
                                                errorMessage = "❌ Please select business type"
                                                return@Button
                                            }
                                            if (businessMobile.isBlank()) {
                                                errorMessage = "❌ Please enter mobile number"
                                                return@Button
                                            }
                                            currentStep = 2
                                            errorMessage = ""
                                        }
                                        2 -> {
                                            currentStep = 3
                                            errorMessage = ""
                                        }
                                        3 -> {
                                            if (adminName.isBlank()) {
                                                errorMessage = "❌ Please enter admin name"
                                                return@Button
                                            }
                                            if (adminEmail.isBlank()) {
                                                errorMessage = "❌ Please enter admin email"
                                                return@Button
                                            }
                                            if (password.isBlank()) {
                                                errorMessage = "❌ Please enter password"
                                                return@Button
                                            }
                                            if (password.length < 6) {
                                                errorMessage = "❌ Password must be at least 6 characters"
                                                return@Button
                                            }
                                            if (password != confirmPassword) {
                                                errorMessage = "❌ Passwords do not match"
                                                return@Button
                                            }
                                            
                                            loading = true
                                            errorMessage = ""
                                            scope.launch {
                                                try {
                                                    val response = ApiClient.apiService.registerBusiness(
                                                        RegisterRequest(
                                                            businessName = businessName.trim(),
                                                            businessType = selectedBusinessType,
                                                            businessMobile = businessMobile.trim(),
                                                            businessEmail = businessEmail.trim().ifBlank { null },
                                                            address = address.trim().ifBlank { null },
                                                            city = city.trim().ifBlank { null },
                                                            state = state.trim().ifBlank { null },
                                                            country = country.trim(),
                                                            gstNumber = gstNumber.trim().ifBlank { null },
                                                            name = adminName.trim(),
                                                            email = adminEmail.trim(),
                                                            password = password
                                                        )
                                                    )
                                                    
                                                    // Debug logging
                                                    android.util.Log.d("RegisterScreen", "Register response: $response")
                                                    
                                                    val businessId = when (val bid = response["businessId"]) {
                                                        is Number -> bid.toLong()
                                                        is String -> bid.toLongOrNull() ?: 0L
                                                        else -> 0L
                                                    }
                                                    
                                                    val userId = when (val uid = response["userId"]) {
                                                        is Number -> uid.toLong()
                                                        is String -> uid.toLongOrNull() ?: 0L
                                                        else -> 0L
                                                    }
                                                    
                                                    android.util.Log.d("RegisterScreen", "Parsed - businessId: $businessId, userId: $userId")
                                                    
                                                    if (businessId > 0 && userId > 0) {
                                                        onRegisterSuccess(businessId, userId)
                                                    } else {
                                                        errorMessage = "Invalid response from server. businessId=$businessId, userId=$userId"
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("RegisterScreen", "Registration error", e)
                                                    errorMessage = e.message ?: "Registration failed. Please try again."
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                enabled = !loading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2196F3)
                                )
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = if (currentStep == 3) "Submit" else "Continue",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Security note
                if (currentStep == 3) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Your information is secure and will only be used for business verification.",
                            fontSize = 11.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Footer
                Text(
                    text = "Trusted by Growing Businesses",
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StepIndicator(
    number: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(20.dp),
            color = when {
                isCompleted -> Color(0xFF4CAF50)
                isActive -> Color(0xFF2196F3)
                else -> Color(0xFFE0E0E0)
            }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        color = if (isActive) Color.White else Color(0xFF999999),
                        fontSize = 16.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isActive) Color(0xFF2196F3) else Color(0xFF999999),
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1BusinessInfo(
    businessName: String,
    onBusinessNameChange: (String) -> Unit,
    selectedBusinessType: String,
    onBusinessTypeChange: (String) -> Unit,
    businessTypes: List<String>,
    showDropdown: Boolean,
    onShowDropdownChange: (Boolean) -> Unit,
    gstNumber: String,
    onGstNumberChange: (String) -> Unit,
    businessMobile: String,
    onBusinessMobileChange: (String) -> Unit,
    businessEmail: String,
    onBusinessEmailChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Business Name
        Column {
            Text(
                text = "Business Name *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = businessName,
                onValueChange = onBusinessNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your business name", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
        
        // Business Type
        Column {
            Text(
                text = "Business Type *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = showDropdown,
                onExpandedChange = { onShowDropdownChange(!showDropdown) }
            ) {
                OutlinedTextField(
                    value = selectedBusinessType,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    placeholder = { Text("Select business type", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = Color(0xFF2196F3)
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { onShowDropdownChange(false) }
                ) {
                    businessTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                onBusinessTypeChange(type)
                                onShowDropdownChange(false)
                            }
                        )
                    }
                }
            }
        }
        
        // GST Number
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "GST Number",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(Optional)",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = gstNumber,
                onValueChange = onGstNumberChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter GST number", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
        
        // Business Mobile
        Column {
            Text(
                text = "Business Mobile Number *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = "+91",
                    onValueChange = {},
                    modifier = Modifier.width(80.dp),
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Color(0xFFE0E0E0),
                        disabledTextColor = Color(0xFF333333)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = businessMobile,
                    onValueChange = onBusinessMobileChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter mobile number", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF2196F3)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
            }
        }
        
        // Business Email
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Business Email",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(Optional)",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = businessEmail,
                onValueChange = onBusinessEmailChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter business email", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

@Composable
fun Step2Address(
    address: String,
    onAddressChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    state: String,
    onStateChange: (String) -> Unit,
    pincode: String,
    onPincodeChange: (String) -> Unit,
    country: String,
    onCountryChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Address
        Column {
            Text(
                text = "Street Address",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your business address", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
        
        // City and Pincode row
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "City",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = onCityChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("City", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pincode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pincode,
                    onValueChange = onPincodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Pincode", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
            }
        }
        
        // State
        Column {
            Text(
                text = "State",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state,
                onValueChange = onStateChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter state", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
        
        // Country
        Column {
            Text(
                text = "Country",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = country,
                onValueChange = onCountryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter country", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

@Composable
fun Step3AdminAccount(
    adminName: String,
    onAdminNameChange: (String) -> Unit,
    adminEmail: String,
    onAdminEmailChange: (String) -> Unit,
    adminMobile: String,
    onAdminMobileChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    confirmPasswordVisible: Boolean,
    onConfirmPasswordVisibleChange: (Boolean) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Admin Name
        Column {
            Text(
                text = "Full Name *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = adminName,
                onValueChange = onAdminNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your full name", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
        
        // Admin Email
        Column {
            Text(
                text = "Email Address *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = adminEmail,
                onValueChange = onAdminEmailChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your email address", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
        
        // Mobile
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mobile Number",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(Optional)",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = "+91",
                    onValueChange = {},
                    modifier = Modifier.width(80.dp),
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Color(0xFFE0E0E0),
                        disabledTextColor = Color(0xFF333333)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = adminMobile,
                    onValueChange = onAdminMobileChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter mobile number", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF2196F3)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
            }
        }
        
        // Password
        Column {
            Text(
                text = "Password *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Create a strong password", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color(0xFF666666)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
            Text(
                text = "Minimum 6 characters",
                fontSize = 11.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(top = 4.dp, start = 12.dp)
            )
        }
        
        // Confirm Password
        Column {
            Text(
                text = "Confirm Password *",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Re-enter your password", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF2196F3)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onConfirmPasswordVisibleChange(!confirmPasswordVisible) }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                            tint = Color(0xFF666666)
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}
