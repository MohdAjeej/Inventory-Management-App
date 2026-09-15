package com.b2binventory.app.ui.parties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class Party(
    val id: Long,
    val name: String,
    val type: String, // "Sundry Debtors" or "Sundry Creditors"
    val location: String,
    val balance: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(
    businessId: Long,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sample data
    val allParties = remember {
        listOf(
            Party(1, "Shri Radhe Krishna Paint & Hardware Stor", "Sundry Debtors", "Haryana", 0.00),
            Party(2, "Vishal", "Sundry Debtors", "Uttar Pradesh", 0.00),
            Party(3, "1", "Sundry Debtors", "", 0.00),
            Party(4, "2", "Sundry Debtors", "", 0.00),
            Party(5, "Mr Rana Pratap Singh", "Sundry Debtors", "", 0.00),
            Party(6, "Suru", "Sundry Debtors", "", 0.00),
            Party(7, "TTN", "Sundry Creditors", "Punjab", 0.00),
            Party(8, "Genrico", "Sundry Debtors", "", 0.00)
        )
    }
    
    val filteredParties = when (selectedTab) {
        0 -> allParties // All
        1 -> allParties.filter { it.balance > 0 } // Overdue (for demo)
        else -> allParties
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Party") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Filter */ }) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Create New Party */ },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Create New")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All (${allParties.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Overdue") }
                )
            }
            
            // Date Range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "15-09-2026 - 15-09-2026",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { /* PDF */ }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { /* Filter */ }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { /* Search */ }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            if (filteredParties.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            "Unsynced data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Parties List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredParties) { party ->
                        PartyListItem(party)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun PartyListItem(party: Party) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate to party details */ }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = party.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = party.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (party.location.isNotBlank()) {
                Text(
                    text = party.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%.2f".format(party.balance),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
