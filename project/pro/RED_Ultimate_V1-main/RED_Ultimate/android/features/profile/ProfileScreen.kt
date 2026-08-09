package com.red.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen() {
    var name by remember { mutableStateOf("Engineer Master") }
    var about by remember { mutableStateOf("Building the future of secure chat.") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Surface(modifier = Modifier.size(120.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) { Text("EM", style = MaterialTheme.typography.headlineLarge) }
            }
            IconButton(
                onClick = { /* Pick Image & Crop */ },
                modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape).size(36.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = about, onValueChange = { about = it }, label = { Text("About") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = { /* Save Profile */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Profile")
        }
    }
}
