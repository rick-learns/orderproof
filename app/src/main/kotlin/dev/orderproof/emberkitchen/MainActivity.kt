package dev.orderproof.emberkitchen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.orderproof.emberkitchen.ui.navigation.AppNavHost
import dev.orderproof.emberkitchen.ui.theme.EmberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EmberKitchenApp).container
        setContent {
            EmberTheme {
                AppNavHost(container)
            }
        }
    }
}
