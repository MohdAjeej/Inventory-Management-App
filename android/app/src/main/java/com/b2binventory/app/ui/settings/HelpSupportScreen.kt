package com.b2binventory.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FAQItem(
    val question: String,
    val answer: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onNavigateBack: () -> Unit
) {
    val faqs = remember {
        listOf(
            FAQItem(
                "How do I add a new product?",
                "Go to Inventory → Tap the '+' button → Fill in product details → Save"
            ),
            FAQItem(
                "How to adjust stock levels?",
                "Open product details → Tap 'Adjust Stock' → Enter quantity and reason → Confirm"
            ),
            FAQItem(
                "How to view stock history?",
                "Product Details → Tap 'View History' or go to Dashboard → Stock History"
            ),
            FAQItem(
                "How to manage categories?",
                "Go to Dashboard → Categories → Add/Edit/Delete categories"
            ),
            FAQItem(
                "What are low stock alerts?",
                "When product quantity falls below minimum stock level, you'll see alerts"
            ),
            FAQItem(
                "How to export data?",
                "Go to Reports → Select report type → Tap 'Export' button"
            ),
            FAQItem(
                "How to add team members?",
                "Settings → Team Members → Tap '+' → Enter details → Assign role"
            ),
            FAQItem(
                "How to reset password?",
                "Login screen → 'Forgot Password?' → Enter email → Follow instructions"
            )
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contact Support Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Need Help?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Our support team is here to help you",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { /* TODO: Open email */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Email")
                            }
                            Button(
                                onClick = { /* TODO: Open phone */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Call")
                            }
                        }
                    }
                }
            }
            
            // Quick Links
            item {
                Text(
                    text = "Quick Links",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        QuickLinkItem(
                            icon = Icons.Default.MenuBook,
                            title = "User Guide",
                            subtitle = "Learn how to use the app",
                            onClick = { }
                        )
                        Divider()
                        QuickLinkItem(
                            icon = Icons.Default.OndemandVideo,
                            title = "Video Tutorials",
                            subtitle = "Watch step-by-step tutorials",
                            onClick = { }
                        )
                        Divider()
                        QuickLinkItem(
                            icon = Icons.Default.BugReport,
                            title = "Report a Bug",
                            subtitle = "Help us improve the app",
                            onClick = { }
                        )
                        Divider()
                        QuickLinkItem(
                            icon = Icons.Default.Feedback,
                            title = "Send Feedback",
                            subtitle = "Share your suggestions",
                            onClick = { }
                        )
                    }
                }
            }
            
            // FAQs
            item {
                Text(
                    text = "Frequently Asked Questions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(faqs) { faq ->
                FAQCard(faq)
            }
            
            // Contact Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Contact Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ContactInfoRow(Icons.Default.Email, "support@b2binventory.com")
                        Spacer(modifier = Modifier.height(8.dp))
                        ContactInfoRow(Icons.Default.Phone, "+91 1234567890")
                        Spacer(modifier = Modifier.height(8.dp))
                        ContactInfoRow(Icons.Default.Language, "www.b2binventory.com")
                        Spacer(modifier = Modifier.height(8.dp))
                        ContactInfoRow(Icons.Default.Schedule, "Mon-Fri, 9:00 AM - 6:00 PM")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLinkItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FAQCard(faq: FAQItem) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = faq.question,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = faq.answer,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp
        )
    }
}
