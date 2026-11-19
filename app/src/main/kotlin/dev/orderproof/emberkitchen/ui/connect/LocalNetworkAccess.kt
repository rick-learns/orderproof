package dev.orderproof.emberkitchen.ui.connect

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * From API 37, connections to private addresses (a laptop's Wi-Fi IP, or 10.0.2.2 on an
 * emulator) need a runtime permission; without it they silently time out. The test backend
 * always lives on such an address, so the Connect screen asks before testing or saving.
 */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}

/** Returns a runner that performs an action once local-network access is granted, or reports a denial. */
@Composable
fun rememberLocalNetworkGate(onDenied: () -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else onDenied()
    }
    return { action ->
        if (LocalNetworkAccess.granted(context)) {
            action()
        } else {
            pending = action
            launcher.launch(LocalNetworkAccess.PERMISSION)
        }
    }
}
