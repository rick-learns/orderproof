package dev.orderproof.emberkitchen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import dev.orderproof.emberkitchen.ui.navigation.AppNavHost
import dev.orderproof.emberkitchen.ui.theme.EmberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EmberKitchenApp).container
        setContent {
            EmberTheme {
                // Exposes semantics test tags as resource ids so out-of-process UI Automator tests can find them.
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    AppNavHost(container)
                }
            }
        }
    }
}
