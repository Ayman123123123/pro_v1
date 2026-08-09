package com.red.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Placeholder for Logo
            Surface(
                modifier = Modifier.size(120.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("DC", fontSize = 48.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("RED", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Secure. Local. Unified.", color = MaterialTheme.colorScheme.secondary)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNavigateToRegister,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Register")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Login")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
