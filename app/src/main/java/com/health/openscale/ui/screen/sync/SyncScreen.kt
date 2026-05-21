package com.health.openscale.ui.screen.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.WeightRecord
import com.health.openscale.core.database.DatabaseRepository
import com.health.openscale.sync.HealthConnectSync
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.health.openscale.core.utils.LogManager

@Composable
fun SyncScreen(
    sharedViewModel: SharedViewModel,
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedUserId by sharedViewModel.selectedUserId.collectAsState()

    var syncStatus by remember { mutableStateOf("Ready to sync") }
    var isSyncing by remember { mutableStateOf(false) }

    val healthConnectClient = remember {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyWaterMassRecord::class)
    )

    val requestPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            syncStatus = "Permissions granted. Starting sync..."
            startSync(
                client = healthConnectClient,
                userId = selectedUserId,
                databaseRepository = syncViewModel.databaseRepository,
                onStatusUpdate = { syncStatus = it },
                onSyncingStateChange = { isSyncing = it },
                coroutineScope = coroutineScope
            )
        } else {
            syncStatus = "Permissions not granted."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Health Connect Sync",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = syncStatus,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (healthConnectClient == null) {
                    syncStatus = "Health Connect is not available on this device."
                    return@Button
                }

                if (selectedUserId == null || selectedUserId == -1) {
                    syncStatus = "No user selected."
                    return@Button
                }

                coroutineScope.launch {
                    val granted = healthConnectClient.permissionController.getGrantedPermissions()
                    if (granted.containsAll(permissions)) {
                        startSync(
                            client = healthConnectClient,
                            userId = selectedUserId,
                            databaseRepository = syncViewModel.databaseRepository,
                            onStatusUpdate = { syncStatus = it },
                            onSyncingStateChange = { isSyncing = it },
                            coroutineScope = this
                        )
                    } else {
                        requestPermissions.launch(permissions)
                    }
                }
            },
            enabled = !isSyncing
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Syncing...")
            } else {
                Text("Sync Now")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Note: This will write measurements to Health Connect. It does not read data from Health Connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun startSync(
    client: HealthConnectClient?,
    userId: Int?,
    databaseRepository: DatabaseRepository,
    onStatusUpdate: (String) -> Unit,
    onSyncingStateChange: (Boolean) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    if (client == null || userId == null || userId == -1) return

    coroutineScope.launch {
        onSyncingStateChange(true)
        onStatusUpdate("Reading measurements...")
        try {
            val measurements = databaseRepository.getMeasurementsWithValuesForUser(userId).first()
            if (measurements.isEmpty()) {
                onStatusUpdate("No measurements found to sync.")
                onSyncingStateChange(false)
                return@launch
            }

            onStatusUpdate("Syncing ${measurements.size} measurements...")
            val sync = HealthConnectSync(client)
            val result = sync.syncAll(measurements)
            
            if (result.isSuccess) {
                onStatusUpdate("Last synced: ${measurements.size} records on ${java.util.Date()}")
            } else {
                onStatusUpdate("Sync failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            LogManager.e("SyncScreen", "Sync error. Error: ${e.message}", e)
            onStatusUpdate("Error: ${e.message}")
        } finally {
            onSyncingStateChange(false)
        }
    }
}
