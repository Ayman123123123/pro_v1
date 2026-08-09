package com.red.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("RED Data Sovereignty", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Backup your chats and media to a local encrypted file. Only you have the key.")
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { /* Export Logic */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Create Full Backup")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { /* Import Logic */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from File")
        }
    }
}
