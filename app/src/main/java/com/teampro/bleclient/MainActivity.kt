package com.teampro.bleclient

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.teampro.bleclient.ui.theme.BleClientTheme
import com.teampro.bleclient.util.BluetoothDevice
import com.teampro.bleclient.util.BluetoothUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mitapp.pro.remote_access.data.ble.ConnectionResult

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothController: BluetoothClient
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow(BluetoothUiState())
    private lateinit var state: StateFlow<BluetoothUiState>
    private var deviceConnectionJob: Job? = null
    private var isReturningFromOtherActivity = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys
        if (deniedPermissions.isEmpty()) {
            Log.d("Permissions", "All permissions granted")
            startScan()
        } else {
            val permanentlyDenied = deniedPermissions.any { permission ->
                !shouldShowRequestPermissionRationale(permission)
            }
            if (permanentlyDenied) {
                _state.update {
                    it.copy(
                        errorMessage = "Some permissions are permanently denied. Please enable them in Settings.",
                        showPermissionDialog = true
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        errorMessage = "Required permissions denied: ${deniedPermissions.joinToString()}",
                        showPermissionRationale = deniedPermissions.any { shouldShowRequestPermissionRationale(it) }
                    )
                }
            }
            Log.e("Permissions", "Denied permissions: ${deniedPermissions.joinToString()}")
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            val showRationale = missingPermissions.any { shouldShowRequestPermissionRationale(it) }
            if (showRationale) {
                _state.update { it.copy(showPermissionRationale = true) }
            } else {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            startScan()
        }
    }

    private fun setupBluetoothStateObservers() {
        bluetoothController.isConnected
            .onEach { isConnected ->
                _state.update { it.copy(isConnected = isConnected) }
            }
            .launchIn(scope)

        bluetoothController.errors
            .onEach { error ->
                _state.update { it.copy(errorMessage = error) }
            }
            .launchIn(scope)
    }

    private fun startScan() {
        if (!this::bluetoothController.isInitialized) {
            _state.update { it.copy(errorMessage = "Bluetooth client not initialized") }
            return
        }
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled != true) {
            _state.update { it.copy(errorMessage = "Bluetooth is disabled. Please enable it.") }
            return
        }
        try {
            _state.update { it.copy(isScanning = true) }
            bluetoothController.startDiscovery()
        } catch (e: SecurityException) {
            _state.update { it.copy(errorMessage = "Permission denied for scanning", showPermissionRationale = true) }
        }
    }

    private fun stopScan() {
        if (!this::bluetoothController.isInitialized) {
            _state.update { it.copy(errorMessage = "Bluetooth client not initialized") }
            return
        }
        try {
            bluetoothController.stopDiscovery()
            _state.update { it.copy(isScanning = false) }
        } catch (e: SecurityException) {
            _state.update { it.copy(errorMessage = "Permission denied for stopping scan") }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!this::bluetoothController.isInitialized) {
            _state.update { it.copy(errorMessage = "Bluetooth client not initialized") }
            return
        }
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob?.cancel()
        deviceConnectionJob = bluetoothController
            .connectToDevice(device)
            .listen()
    }

    private fun disconnectFromDevice() {
        if (!this::bluetoothController.isInitialized) {
            _state.update { it.copy(errorMessage = "Bluetooth client not initialized") }
            return
        }
        deviceConnectionJob?.cancel()
        try {
            bluetoothController.closeConnection()
            _state.update {
                it.copy(
                    isConnecting = false,
                    isConnected = false,
                    errorMessage = null,
                    messages = emptyList()
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Failed to disconnect: ${e.message}") }
        }
    }

    private fun Flow<ConnectionResult>.listen(): Job {
        return onEach { result ->
            when (result) {
                ConnectionResult.ConnectionEstablished -> {
                    _state.update {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            errorMessage = null
                        )
                    }
                }
                is ConnectionResult.TransferSucceeded -> {
                    _state.update {
                        it.copy(messages = it.messages + result.message)
                    }
                }
                is ConnectionResult.Error -> {
                    _state.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
            .catch { throwable ->
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = throwable.message
                    )
                }
            }
            .launchIn(scope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isReturningFromOtherActivity = false

        // Handle back button press
        onBackPressedDispatcher.addCallback(this) {
            val uiState = if (this@MainActivity::state.isInitialized) {
                state.value
            } else {
                BluetoothUiState()
            }
            if (uiState.isConnected) {
                // If MainScreen is shown, disconnect and show DeviceScreen
                disconnectFromDevice()
            } else {
                // If DeviceScreen is shown, close the app
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isReturningFromOtherActivity && this::bluetoothController.isInitialized) {
            disconnectFromDevice()
            _state.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    messages = emptyList()
                )
            }
        }

        if (bleClient == null) {
            _state.update { it.copy(errorMessage = "Bluetooth client not initialized") }
            Log.e("MainActivity", "bleClient is null")
        } else {
            bluetoothController = bleClient as BluetoothClient
            state = combine(
                bluetoothController.scannedDevices,
                bluetoothController.pairedDevices,
                _state
            ) { scannedDevices, pairedDevices, state ->
                state.copy(
                    scannedDevices = scannedDevices,
                    pairedDevices = pairedDevices,
                    messages = if (state.isConnected) state.messages else emptyList()
                )
            }.stateIn(scope, SharingStarted.WhileSubscribed(5000), _state.value)
            setupBluetoothStateObservers()
        }

        setContent {
            BleClientTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!this@MainActivity::state.isInitialized) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "Error: Bluetooth client not initialized")
                        }
                    } else {
                        val uiState = state.collectAsState().value
                        when {
                            uiState.isConnecting -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Text(text = "Connecting...")
                                }
                            }
                            uiState.isConnected -> {
                                MainScreen(onDisconnect = ::disconnectFromDevice)
                            }
                            else -> {
                                DeviceScreen(
                                    state = uiState,
                                    onStartScan = ::startScan,
                                    onStopScan = ::stopScan,
                                    onDeviceClick = ::connectToDevice
                                )
                            }
                        }
                        if (uiState.showPermissionRationale) {
                            PermissionRationaleDialog(
                                onConfirm = { checkAndRequestPermissions() },
                                onDismiss = { _state.update { it.copy(showPermissionRationale = false) } }
                            )
                        }
                        if (uiState.showPermissionDialog) {
                            PermissionSettingsDialog(
                                onConfirm = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(intent)
                                    _state.update { it.copy(showPermissionDialog = false) }
                                },
                                onDismiss = { _state.update { it.copy(showPermissionDialog = false) } }
                            )
                        }
                    }
                }
            }
        }

        checkAndRequestPermissions()
    }

    override fun onPause() {
        super.onPause()
        isReturningFromOtherActivity = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::bluetoothController.isInitialized) {
            stopScan()
            disconnectFromDevice()
        }
        scope.cancel()
    }
}

@Composable
fun PermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = { Text("This app needs Bluetooth and location permissions to scan and connect to devices. Please grant the required permissions.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionSettingsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Denied") },
        text = { Text("Some permissions are permanently denied. Please enable them in the app settings to continue.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MainScreen(onDisconnect: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "BLE Client", style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = {
                context.startActivity(Intent(context, PaymentActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Payment")
        }
        Button(
            onClick = {
                context.startActivity(Intent(context, ReportActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Z X Reports")
        }
        Button(
            onClick = {
                context.startActivity(Intent(context, CashoutActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cashout")
        }
        Button(
            onClick = {
                context.startActivity(Intent(context, ReversalActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reversal")
        }
        Button(
            onClick = {
                context.startActivity(Intent(context, ShiftActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Shifts")
        }
        Button(
            onClick = {
                context.startActivity(Intent(context, OthersActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Others")
        }
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BleClientTheme {
        MainScreen(onDisconnect = {})
    }
}