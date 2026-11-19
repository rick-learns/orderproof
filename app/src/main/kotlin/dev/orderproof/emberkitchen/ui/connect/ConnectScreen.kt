package dev.orderproof.emberkitchen.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.BuildConfig
import dev.orderproof.emberkitchen.candidate.CandidateBuild
import dev.orderproof.emberkitchen.data.settings.ConnectionSettings
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.components.EmberScaffold

@Composable
fun ConnectScreen(container: AppContainer, onConnected: () -> Unit) {
    val vm: ConnectViewModel = viewModel { ConnectViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val withLocalNetwork = rememberLocalNetworkGate(onDenied = vm::onLocalNetworkDenied)

    EmberScaffold(title = "Connect to Ember Kitchen") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Enter the address of the Ember Kitchen test backend. On a phone, use your computer's " +
                    "Wi-Fi address, for example http://192.168.1.20:8787. On an Android emulator, use " +
                    "${ConnectionSettings.EMULATOR_HINT}.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = state.url,
                onValueChange = vm::onUrlChange,
                label = { Text("Backend address") },
                singleLine = true,
                isError = state.url.isNotBlank() && !state.valid,
                supportingText = { if (state.url.isNotBlank() && !state.valid) Text("Use http://host:port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.CONNECT_URL),
            )
            TextButton(onClick = { vm.onUrlChange(ConnectionSettings.EMULATOR_HINT) }) { Text("Use emulator address") }
            OutlinedButton(
                onClick = { withLocalNetwork(vm::test) },
                enabled = state.valid && !state.checking,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.CONNECT_TEST),
            ) { Text(if (state.checking) "Checking…" else "Test connection") }
            state.status?.let {
                Text(
                    it,
                    color = if (state.reachable) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TestTags.CONNECT_STATUS).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                onClick = { withLocalNetwork { vm.save(onConnected) } },
                enabled = state.valid,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.CONNECT_SAVE),
            ) { Text("Save and continue") }
            Text(
                "Build ${BuildConfig.VERSION_NAME} · candidate ${CandidateBuild.LABEL} · commit ${BuildConfig.GIT_COMMIT}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "All orders and payments are fictional. No real card is ever charged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
