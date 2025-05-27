package com.example.taskapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.taskapplication.ui.AppNavigation
import com.example.taskapplication.ui.auth.AuthEvent
import com.example.taskapplication.ui.auth.AuthViewModel
import com.example.taskapplication.ui.theme.TaskApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure Google Sign In
        try {
            val webClientId = getString(R.string.web_client_id)
            Log.d(TAG, "Configuring Google Sign In with Web Client ID: $webClientId")

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d(TAG, "Google Sign In client configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring Google Sign In", e)
            Toast.makeText(this, "Error configuring Google Sign In: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Set up the ActivityResultLauncher for Google Sign In
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleGoogleSignInResult(task)
        }

        // Configure Firebase Messaging
        configureFirebaseMessaging()

        // Observe auth events
        lifecycleScope.launch {
            authViewModel.authEvent.collectLatest { event ->
                when (event) {
                    is AuthEvent.GoogleSignInRequested -> {
                        launchGoogleSignIn()
                    }
                    else -> {}
                }
            }
        }

        setContent {
            TaskApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun launchGoogleSignIn() {
        try {
            Log.d(TAG, "Launching Google Sign In")
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Google Sign In", e)
            Toast.makeText(this, "Error launching Google Sign In: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            // Log account details for debugging
            Log.d(TAG, "Google Sign In successful, account details:")
            Log.d(TAG, "- Display Name: ${account.displayName}")
            Log.d(TAG, "- Email: ${account.email}")
            Log.d(TAG, "- ID: ${account.id}")
            Log.d(TAG, "- Has ID Token: ${account.idToken != null}")

            // Google Sign In was successful, authenticate with the server
            val idToken = account.idToken
            if (idToken != null) {
                Log.d(TAG, "Google Sign In token: ${idToken.take(10)}...")
                lifecycleScope.launch {
                    authViewModel.loginWithGoogle(idToken)
                }
            } else {
                Log.e(TAG, "Google Sign In failed: ID Token is null")
                Toast.makeText(this, "Google Sign In failed: ID Token is null. Make sure your Firebase project is configured correctly.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            // Google Sign In failed
            Log.e(TAG, "Google Sign In failed with status code: ${e.statusCode}", e)
            val errorMessage = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign in was cancelled"
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error occurred"
                else -> "Error: ${e.message} (Status code: ${e.statusCode})"
            }
            Log.e(TAG, "Google Sign In error message: $errorMessage")
            Toast.makeText(this, "Google Sign In failed: $errorMessage", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Unexpected error
            Log.e(TAG, "Unexpected error during Google Sign In", e)
            Toast.makeText(this, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}