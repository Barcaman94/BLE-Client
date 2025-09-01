package com.teampro.bleclient

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.teampro.bleclient.util.BluetoothDataTransferService
import com.teampro.bleclient.util.BluetoothDeviceDomain
import com.teampro.bleclient.util.BluetoothMessage
import com.teampro.bleclient.util.FoundDeviceReceiver
import com.teampro.bleclient.util.toBluetoothDeviceDomain
import com.teampro.bleclient.util.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mitapp.pro.remote_access.data.ble.ConnectionResult
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothClient(
    private val context: Context,
    private val onDeviceConnected: () -> Unit,
    private val onDataReceived: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val TAG = "BluetoothClient"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private var dataTransferService: BluetoothDataTransferService? = null
    private var currentClientSocket: BluetoothSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> get() = _isConnected.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> get() = _errors.asSharedFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> get() = _pairedDevices.asStateFlow()

    private var isScanning = false

    private val handler = Handler(Looper.getMainLooper())

    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if (newDevice !in devices) devices + newDevice else devices
        }
    }

    init {
        updatePairedDevices()
        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )
    }

    fun startScan(targetName: String) {
        if (!bluetoothAdapter.isEnabled) {
            onError("Bluetooth выключен")
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("Bluetooth выключен")
            }
            return
        }

        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            onError("Нет разрешения на сканирование Bluetooth")
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("Нет разрешения на сканирование Bluetooth")
            }
            return
        }

        _scannedDevices.update { emptyList() }

        bluetoothAdapter.startDiscovery()
        isScanning = true

        handler.postDelayed({
            if (isScanning) {
                stopDiscovery()
                onError("Устройство с именем $targetName не найдено")
                CoroutineScope(Dispatchers.IO).launch {
                    _errors.emit("Устройство с именем $targetName не найдено")
                }
            }
        }, 10000)

        CoroutineScope(Dispatchers.IO).launch {
            _scannedDevices.collect { devices ->
                val targetDevice =
                    devices.find { it.name?.contains(targetName, ignoreCase = true) == true }
                if (targetDevice != null) {
                    stopDiscovery()
                    connectToDevice(targetDevice).collect()
                }
            }
        }
    }

    fun startDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()
    }

    fun stopDiscovery() {
        if (isScanning && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            bluetoothAdapter.cancelDiscovery()
            isScanning = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    fun connectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult> = flow {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            val errorMsg = "Нет разрешения на подключение Bluetooth"
            onError(errorMsg)
            _errors.emit(errorMsg)
            emit(ConnectionResult.Error(errorMsg))
            return@flow
        }

        currentClientSocket = bluetoothAdapter
            ?.getRemoteDevice(device.address)
            ?.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE_UUID))

        stopDiscovery()

        val socket = currentClientSocket ?: run {
            val errorMsg = "Не удалось создать сокет"
            onError(errorMsg)
            _errors.emit(errorMsg)
            emit(ConnectionResult.Error(errorMsg))
            return@flow
        }

        try {
            socket.connect()
            emit(ConnectionResult.ConnectionEstablished)
            _isConnected.update { true }
            onDeviceConnected()

            dataTransferService = BluetoothDataTransferService(socket)
            dataTransferService!!.listenForIncomingMessages(scope)

            emitAll(
                dataTransferService!!.results.map { message ->
                    Log.d("BluetoothClient", "message mapped: $message")
                    onDataReceived(message.message)
                    ConnectionResult.TransferSucceeded(message)
                }.catch { e ->
                    val errorMsg = "Ошибка получения данных: ${e.message}"
                    Log.e("BluetoothClient", errorMsg, e)
                    emit(ConnectionResult.Error(errorMsg))
                }
            )

        } catch (e: IOException) {
            socket.close()
            currentClientSocket = null
            val errorMsg = "Подключение прервано: ${e.message}"
            onError(errorMsg)
            _errors.emit(errorMsg)
            emit(ConnectionResult.Error(errorMsg))
        } finally {
            closeConnection()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendCommand(command: String): BluetoothMessage? {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            val errorMsg = "Нет разрешения на подключение Bluetooth"
            onError(errorMsg)
            _errors.emit(errorMsg)
            return null
        }

        val service = dataTransferService ?: run {
            val errorMsg = "Не подключен к устройству"
            onError(errorMsg)
            _errors.emit(errorMsg)
            return null
        }

        val bluetoothMessage = BluetoothMessage(
            message = command.trimEnd('\n') + "\n",
            senderName = bluetoothAdapter?.name ?: "Unknown name",
            isFromLocalUser = true
        )

        val sent = service.sendMessage(bluetoothMessage.toByteArray())
        if (!sent) {
            val errorMsg = "Не удалось отправить сообщение"
            onError(errorMsg)
            _errors.emit(errorMsg)
            return null
        }

        val response = service.results.firstOrNull()

        return response ?: bluetoothMessage
    }

    fun closeConnection() {
        currentClientSocket?.close()
        currentClientSocket = null
        dataTransferService = null
        _isConnected.update { false }
        job.cancel()
    }

    fun release() {
        context.unregisterReceiver(foundDeviceReceiver)
        closeConnection()
        handler.removeCallbacksAndMessages(null)
    }

    private fun updatePairedDevices() {
//        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
//            return
//        }
        bluetoothAdapter
            ?.bondedDevices
            ?.map { it.toBluetoothDeviceDomain() }
            ?.also { devices ->
                _pairedDevices.update { devices }
            }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
    }
}