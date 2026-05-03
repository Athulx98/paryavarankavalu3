package com.paryavarankavalu.paryavarankavalu.uii.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.paryavarankavalu.paryavarankavalu.ui.theme.Forest900
import com.paryavarankavalu.paryavarankavalu.ui.theme.GreenPrimary
import com.paryavarankavalu.paryavarankavalu.ui.theme.Sage400
import com.paryavarankavalu.paryavarankavalu.ui.theme.Sage50
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel

@Composable
fun AuthScreen(navController: NavController, viewModel: MainViewModel) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(GreenPrimary)) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                Text(text = "Welcome, Eco Spot", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(text = "Let's make our Home\nbetter together.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = if (isLogin) "Sign in" else "Sign up", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Forest900)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isLogin) "New here? " else "Already have an account? ", color = Sage400, fontSize = 14.sp)
                Text(text = if (isLogin) "Create account" else "Login", color = GreenPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { isLogin = !isLogin })
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!isLogin) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Sage50, unfocusedContainerColor = Sage50, focusedIndicatorColor = GreenPrimary)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Enter your email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Sage50, unfocusedContainerColor = Sage50, focusedIndicatorColor = GreenPrimary)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Create password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Sage50, unfocusedContainerColor = Sage50, focusedIndicatorColor = GreenPrimary)
            )

            if (error != null) {
                Text(text = error!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        error = "Please fill in all fields"
                        return@Button
                    }
                    isLoading = true
                    if (isLogin) {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { 
                                isLoading = false
                                navController.navigate("home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                            .addOnFailureListener { 
                                isLoading = false
                                error = it.message 
                            }
                    } else {
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { result ->
                                result.user?.let { user ->
                                    viewModel.createUserProfile(user.uid, email, displayName)
                                }
                                isLoading = false
                                navController.navigate("home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                            .addOnFailureListener { 
                                isLoading = false
                                error = it.message 
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(if (isLogin) "Sign In" else "Sign Up", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
