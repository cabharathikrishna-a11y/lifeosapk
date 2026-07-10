package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel

@Composable
fun ProfileSetupView(viewModel: AppViewModel) {
    val currentUser = viewModel.currentUserRemote.collectAsState().value
    var name by remember(currentUser) { mutableStateOf(currentUser?.name ?: "") }
    var nickname by remember(currentUser) { mutableStateOf(currentUser?.nickname ?: "") }
    var emoji by remember(currentUser) { mutableStateOf(currentUser?.emoji ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isCheckingCloud by remember { mutableStateOf(true) }

    // On enter, trigger a refresh of the user's remote profile from Firebase
    LaunchedEffect(Unit) {
        isCheckingCloud = true
        viewModel.refreshCurrentUserProfile()
        // Wait a small delay to allow network to return
        kotlinx.coroutines.delay(2000)
        isCheckingCloud = false
    }

    // Auto-advance if we find a complete profile details from Firebase
    LaunchedEffect(currentUser) {
        if (currentUser != null && !currentUser.name.isNullOrEmpty() && !currentUser.emoji.isNullOrEmpty()) {
            isCheckingCloud = false
            viewModel.completeProfileSetup(
                currentUser.name ?: "",
                currentUser.nickname ?: "",
                currentUser.emoji ?: ""
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp).fillMaxWidth(0.9f)
        ) {
            Text("Complete Your Profile", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Please enter your details to register or continue", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (isCheckingCloud && (currentUser == null || currentUser.name.isNullOrEmpty())) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking for existing profile in cloud...", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (Mandatory)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfilePicEditor(
                initialValue = emoji.ifBlank { "👨‍💻" },
                onValueChange = { emoji = it }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            if (errorMsg != null) {
                Text(errorMsg!!, color = Color.Red, modifier = Modifier.padding(bottom = 12.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.refreshCurrentUserProfile()
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Sync Cloud")
                }
                
                Button(
                    onClick = {
                        if (name.isBlank() || emoji.isBlank()) {
                            errorMsg = "Name and Emoji are mandatory"
                            return@Button
                        }
                        viewModel.completeProfileSetup(name, nickname, emoji)
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save & Continue")
                }
            }
        }
    }
}
