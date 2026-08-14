package com.red.sovereign.features.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onNext: (List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf(setOf<Contact>()) }

    // Mock Data
    val allContacts = listOf(
        Contact("1", "Ayman", Color(0xFF1E88E5)),
        Contact("2", "Ali", Color(0xFF43A047)),
        Contact("3", "Sara", Color(0xFFE53935)),
        Contact("4", "Tech Team Lead", Color(0xFF8E24AA)),
        Contact("5", "Designer", Color(0xFFFDD835))
    )

    val filteredContacts = allContacts.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("New Group", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${selectedContacts.size} selected", color = Color.Gray, fontSize = 14.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0A0A0A)
                    )
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(50.dp),
                    placeholder = { Text("Search contacts", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(25.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color(0xFF141414),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFFB71C1C),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = selectedContacts.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onNext(selectedContacts.map { it.id }) },
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Selected Contacts Row (Bento style small pills)
            AnimatedVisibility(visible = selectedContacts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedContacts.toList(), key = { it.id }) { contact ->
                        SelectedContactPill(
                            contact = contact,
                            onRemove = { selectedContacts = selectedContacts - contact }
                        )
                    }
                }
            }

            // Contact List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredContacts, key = { it.id }) { contact ->
                    val isSelected = selectedContacts.contains(contact)
                    ContactListItem(
                        contact = contact,
                        isSelected = isSelected,
                        onClick = {
                            selectedContacts = if (isSelected) {
                                selectedContacts - contact
                            } else {
                                selectedContacts + contact
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedContactPill(contact: Contact, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onRemove)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(contact.color),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.name.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(contact.name.split(" ").first(), color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ContactListItem(contact: Contact, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape).background(contact.color),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = contact.name,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        // Checkbox replacement (Modern sleek circle)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFFB71C1C) else Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

data class Contact(val id: String, val name: String, val color: Color)
