package com.example.build

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.build.ui.RecipeScreen
import com.example.build.ui.theme.BuildTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuildTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainAuthContent(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainAuthContent(modifier: Modifier = Modifier) {
    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth?.currentUser) }
    var isGuestMode by remember { mutableStateOf(false) }

    DisposableEffect(auth) {
        if (auth == null) {
            onDispose { }
        } else {
            val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                currentUser = firebaseAuth.currentUser
            }
            auth.addAuthStateListener(authStateListener)
            onDispose {
                auth.removeAuthStateListener(authStateListener)
            }
        }
    }

    val user = currentUser
    if (user != null || isGuestMode) {
        RecipeScreen(
            user = user,
            onSignOut = {
                auth?.signOut()
                isGuestMode = false
            },
            modifier = modifier
        )
    } else {
        AuthScreen(
            auth = auth,
            onContinueAsGuest = { isGuestMode = true },
            modifier = modifier
        )
    }
}

@Composable
fun AuthScreen(
    auth: FirebaseAuth?,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) "Create Account" else "Welcome Back",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let { error ->
            val isApiKeyError = error.contains("API key not valid", ignoreCase = true)
            Text(
                text = if (isApiKeyError) {
                    "Firebase Setup Required: Download your real 'google-services.json' from Firebase Console and replace the file at app/google-services.json."
                } else {
                    error
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both email and password."
                        return@Button
                    }
                    if (auth == null) {
                        errorMessage = "API key not valid. Please replace app/google-services.json with your actual Firebase configuration."
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Sign up successful!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val ex = task.exception?.localizedMessage ?: "Sign up failed."
                                    errorMessage = ex
                                }
                            }
                    } else {
                        auth.signInWithEmailAndPassword(email.trim(), password.trim())
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Sign in successful!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val ex = task.exception?.localizedMessage ?: "Sign in failed."
                                    errorMessage = ex
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isSignUp) "Sign Up" else "Sign In")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    errorMessage = null
                }
            ) {
                Text(
                    text = if (isSignUp)
                        "Already have an account? Sign In"
                    else
                        "Don't have an account? Sign Up"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue as Guest")
            }
        }
    }
}
