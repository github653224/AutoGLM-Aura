package com.autoglm.autoagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoglm.autoagent.ui.HomeScreen
import com.autoglm.autoagent.ui.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Permissions for Voice and Overlay
        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.INTERNET // Usually granted automatically but good practice to check if risky
            )
        )
        
        setContent {
            AutoGLMApp()
        }
    }
}

@Composable
fun AutoGLMApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = hiltViewModel(),
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
