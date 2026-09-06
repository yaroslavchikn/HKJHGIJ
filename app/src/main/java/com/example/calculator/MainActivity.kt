package com.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.calculator.ui.CalculatorScreen
import com.example.calculator.ui.theme.CalculatorTheme
import com.example.calculator.vault.VaultScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            CalculatorTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "calculator"
                ) {
                    composable("calculator") {
                        CalculatorScreen(
                            onSecret = {
                                navController.navigate("vault") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable("vault") {
                        VaultScreen(
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
