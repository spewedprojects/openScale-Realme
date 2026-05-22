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
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.health.openscale.core.database.DatabaseRepository
import com.health.openscale.sync.HealthConnectSync
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.health.openscale.core.utils.LogManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncScreen(
    sharedViewModel: SharedViewModel,
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedUserId by sharedViewModel.selectedUserId.collectAsState()

    val isSyncEnabled by syncViewModel.isSyncEnabled.collectAsState()
    val lastSyncTimestamp by syncViewModel.lastSyncTimestamp.collectAsState()

    var syncStatus by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var hasAllPermissions by remember { mutableStateOf(false) }

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
        HealthPermission.getWritePermission(BodyWaterMassRecord::class),
        HealthPermission.getWritePermission(BoneMassRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class)
    )

    val requestPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            hasAllPermissions = true
            syncStatus = "Permissions granted."
        } else {
            hasAllPermissions = false
            syncStatus = "Permissions not granted."
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (healthConnectClient != null) {
                    coroutineScope.launch {
                        val granted = healthConnectClient.permissionController.getGrantedPermissions()
                        hasAllPermissions = granted.containsAll(permissions)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Enable Health Connect Sync")
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isSyncEnabled,
                onCheckedChange = { enabled ->
                    syncViewModel.setSyncEnabled(enabled)
                    if (enabled && healthConnectClient != null) {
                        coroutineScope.launch {
                            val granted = healthConnectClient.permissionController.getGrantedPermissions()
                            hasAllPermissions = granted.containsAll(permissions)
                        }
                    }
                }
            )
        }

        val lastSyncedText = if (lastSyncTimestamp == 0L) {
            "Never synced"
        } else {
            "Last synced: " + SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastSyncTimestamp))
        }
        Text(
            text = lastSyncedText,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (healthConnectClient == null) {
            Text(
                text = "Health Connect is not available on this device.",
                color = MaterialTheme.colorScheme.error
            )
        } else if (isSyncEnabled) {
            if (!hasAllPermissions) {
                Button(
                    onClick = { requestPermissions.launch(permissions) },
                    enabled = !isSyncing
                ) {
                    Text("Request Permissions")
                }
            } else {
                Button(
                    onClick = {
                        if (selectedUserId == null || selectedUserId == -1) {
                            syncStatus = "No user selected."
                            return@Button
                        }
                        startSync(
                            client = healthConnectClient,
                            userId = selectedUserId,
                            databaseRepository = syncViewModel.databaseRepository,
                            onStatusUpdate = { syncStatus = it },
                            onSyncingStateChange = { isSyncing = it },
                            onSyncSuccess = { timestamp -> syncViewModel.setLastSyncTimestamp(timestamp) },
                            coroutineScope = coroutineScope
                        )
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
            }
        } else {
            // Disabled state: Buttons hidden, but according to prompt:
            // "When the toggle is switched off: disable sync, hide both buttons"
            // So we show nothing here.
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (syncStatus.isNotEmpty()) {
            Text(
                text = syncStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

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
    onSyncSuccess: (Long) -> Unit,
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
                val now = System.currentTimeMillis()
                onSyncSuccess(now)
                onStatusUpdate("Sync completed successfully.")
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
