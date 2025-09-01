package com.teampro.bleclient

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

var bleClient: BluetoothClient? = null

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val client = BluetoothClient(context = this, onDeviceConnected = {
            Log.d("BLE", "Устройство подключено:")
            _onResult?.invoke("Устройство подключено: ")
        }, onDataReceived = { data ->
            Log.d("BLE", "Ответ:  $data")
            Handler(Looper.getMainLooper()).post {
                _onResult?.invoke(data)
            }
        }, onError = { error ->
            Log.e("BLE", "Ошибка: $error")
        })

        bleClient = client
    }


    private var _onResult: ((String) -> Unit)? = null
    fun setOnResultListener(listener: ((String) -> Unit)?) {
        _onResult = listener
    }

}