package com.jatm.androidphoneapi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jatm.androidphoneapi.apikey.ApiKeyUiState
import com.jatm.androidphoneapi.pairing.PairingRequestRecord
import com.jatm.androidphoneapi.pairing.PairingState
import com.jatm.androidphoneapi.pairing.TrustedClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val pairingRepository = remember {
                AppGraph.pairingRepository(applicationContext)
            }
            val apiKeyRepository = remember {
                AppGraph.apiKeyRepository(applicationContext)
            }
            val lifecycleState by ServerLifecycleRepository.state.collectAsState()
            val pairingState by pairingRepository.state.collectAsState()
            val apiKeyState by apiKeyRepository.state.collectAsState()

            AndroidPhoneApiApp(
                lifecycleState = lifecycleState,
                pairingState = pairingState,
                apiKeyState = apiKeyState,
                onStartServer = {
                    ContextCompat.startForegroundService(
                        this,
                        ApiServerForegroundService.startIntent(this),
                    )
                },
                onStopServer = {
                    ServerLifecycleRepository.markStopping()
                    stopService(ApiServerForegroundService.stopIntent(this))
                },
                onApprovePairing = pairingRepository::approvePairing,
                onDenyPairing = pairingRepository::denyPairing,
                onRevokeClient = pairingRepository::revokeClient,
                onApiEnabledChange = apiKeyRepository::setEnabled,
                onRevealApiKey = apiKeyRepository::presentKey,
                onResetApiKey = apiKeyRepository::resetKey,
            )
        }
    }
}

@Composable
fun AndroidPhoneApiApp(
    lifecycleState: ServerLifecycleState,
    pairingState: PairingState,
    apiKeyState: ApiKeyUiState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onApprovePairing: (String) -> Unit,
    onDenyPairing: (String) -> Unit,
    onRevokeClient: (String) -> Unit,
    onApiEnabledChange: (Boolean) -> Unit,
    onRevealApiKey: () -> Unit,
    onResetApiKey: () -> Unit,
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ServerHomeScreen(
                lifecycleState = lifecycleState,
                pairingState = pairingState,
                apiKeyState = apiKeyState,
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                onApprovePairing = onApprovePairing,
                onDenyPairing = onDenyPairing,
                onRevokeClient = onRevokeClient,
                onApiEnabledChange = onApiEnabledChange,
                onRevealApiKey = onRevealApiKey,
                onResetApiKey = onResetApiKey,
            )
        }
    }
}

@Composable
private fun ServerHomeScreen(
    lifecycleState: ServerLifecycleState,
    pairingState: PairingState,
    apiKeyState: ApiKeyUiState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onApprovePairing: (String) -> Unit,
    onDenyPairing: (String) -> Unit,
    onRevokeClient: (String) -> Unit,
    onApiEnabledChange: (Boolean) -> Unit,
    onRevealApiKey: () -> Unit,
    onResetApiKey: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Android Phone API",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Secure local-network server",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Server state",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = lifecycleState.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = lifecycleState.isRunning,
                onCheckedChange = { checked ->
                    if (checked) {
                        onStartServer()
                    } else {
                        onStopServer()
                    }
                },
                enabled = lifecycleState.status == ServerStatus.STOPPED ||
                    lifecycleState.status == ServerStatus.RUNNING,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onStartServer,
                enabled = lifecycleState.canStart,
            ) {
                Text("Start")
            }
            OutlinedButton(
                onClick = onStopServer,
                enabled = lifecycleState.canStop,
            ) {
                Text("Stop")
            }
        }

        Text(
            text = "Health endpoint: /api/v1/health",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        ApiKeyControlsSection(
            apiKeyState = apiKeyState,
            onApiEnabledChange = onApiEnabledChange,
            onRevealApiKey = onRevealApiKey,
            onResetApiKey = onResetApiKey,
        )

        HorizontalDivider()

        PairingRequestsSection(
            requests = pairingState.pendingRequests,
            onApprovePairing = onApprovePairing,
            onDenyPairing = onDenyPairing,
        )

        TrustedClientsSection(
            clients = pairingState.trustedClients,
            onRevokeClient = onRevokeClient,
        )
    }
}

@Composable
private fun ApiKeyControlsSection(
    apiKeyState: ApiKeyUiState,
    onApiEnabledChange: (Boolean) -> Unit,
    onRevealApiKey: () -> Unit,
    onResetApiKey: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "API key",
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (apiKeyState.enabled) "Requests can authenticate" else "Requests are denied",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = apiKeyState.enabled,
                onCheckedChange = onApiEnabledChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRevealApiKey) {
                Text("Reveal")
            }
            Button(onClick = onResetApiKey) {
                Text("Reset")
            }
        }
        apiKeyState.presentedKey?.let { key ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = key,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun PairingRequestsSection(
    requests: List<PairingRequestRecord>,
    onApprovePairing: (String) -> Unit,
    onDenyPairing: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Pending pairing",
            style = MaterialTheme.typography.titleLarge,
        )
        if (requests.isEmpty()) {
            Text(
                text = "No pending requests",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        requests.forEach { request ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = request.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Code ${request.verificationCode} · ${request.shortFingerprint()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onApprovePairing(request.id) }) {
                            Text("Approve")
                        }
                        OutlinedButton(onClick = { onDenyPairing(request.id) }) {
                            Text("Deny")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustedClientsSection(
    clients: List<TrustedClient>,
    onRevokeClient: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Trusted clients",
            style = MaterialTheme.typography.titleLarge,
        )
        if (clients.isEmpty()) {
            Text(
                text = "No trusted clients",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        clients.forEach { client ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = client.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${if (client.isActive) "Active" else "Revoked"} · ${client.shortFingerprint()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (client.isActive) {
                        OutlinedButton(onClick = { onRevokeClient(client.id) }) {
                            Text("Revoke")
                        }
                    }
                }
            }
        }
    }
}

private fun PairingRequestRecord.shortFingerprint(): String =
    publicKeyFingerprintSha256.take(23)

private fun TrustedClient.shortFingerprint(): String =
    publicKeyFingerprintSha256.take(23)
