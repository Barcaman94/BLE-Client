package com.teampro.bleclient.util

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class BluetoothDataTransferService(
    private val socket: BluetoothSocket
) {
    private val _results = MutableSharedFlow<BluetoothMessage>()
    val results: SharedFlow<BluetoothMessage> get() = _results.asSharedFlow()

//    fun listenForIncomingMessages(): Flow<BluetoothMessage> = flow {
//        val reader = BufferedReader(InputStreamReader(socket.inputStream))
//        val buffer = StringBuilder()
//        var openBraces = 0
//        var openBrackets = 0
//
//        while (true) {
//            val ch = try { reader.read() } catch (e: IOException) { break } ?: break
//            buffer.append(ch.toChar())
//
//            when (ch.toChar()) {
//                '{' -> openBraces++
//                '}' -> openBraces--
//                '[' -> openBrackets++
//                ']' -> openBrackets--
//            }
//
//            // Если все скобки закрыты, считаем сообщение полным
//            if (openBraces == 0 && openBrackets == 0 && buffer.isNotBlank()) {
//                val content = buffer.toString().trim()
//                // Пропускаем отдельные строки типа "Kozen P10", если они не JSON
//                if (content.startsWith("{") || content.startsWith("[")) {
//                    emit(content.toBluetoothMessage(isFromLocalUser = false))
//                } else {
//                    Log.d("BluetoothDataTransferService", "Ignored fragment: $content")
//                }
//                buffer.clear()
//            }
//        }
//    }.flowOn(Dispatchers.IO)

//    fun listenForIncomingMessages(): Flow<BluetoothMessage> {
//        return flow {
//            if (!socket.isConnected) return@flow
//
//            val reader = BufferedReader(InputStreamReader(socket.inputStream))
//
//            while (true) {
//                val line = try {
//                    reader.readLine()
//                } catch (e: IOException) {
//                    Log.d("BluetoothDataTransferService", "Connection lost: ${e.message}")
//                    break
//                }
//
//                if (line == null) {
//                    Log.d("BluetoothDataTransferService", "Client closed connection")
//                    break
//                }
//
//                Log.d("BluetoothDataTransferService", "Received raw: $line")
//                emit(line.toBluetoothMessage(isFromLocalUser = false))
//            }
//        }.flowOn(Dispatchers.IO)
//    }

    fun listenForIncomingMessages(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            if (!socket.isConnected) return@launch

            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val buffer = StringBuilder()
            var openBraces = 0
            var openBrackets = 0

            try {
                while (true) {
                    val ch = reader.read() ?: break
                    buffer.append(ch.toChar())

                    when (ch.toChar()) {
                        '{' -> openBraces++
                        '}' -> openBraces--
                        '[' -> openBrackets++
                        ']' -> openBrackets--
                    }

                    if (openBraces == 0 && openBrackets == 0 && buffer.isNotBlank()) {
                        val content = buffer.toString().trim()

                        if (content.startsWith("{") || content.startsWith("[")) {
                            _results.emit(content.toBluetoothMessage(isFromLocalUser = false))
                            Log.d("BluetoothClient", "Received JSON: $content")
                        } else {
                            Log.d("BluetoothClient", "Ignored non-JSON fragment: $content")
                        }

                        buffer.clear()
                    }
                }
            } catch (e: IOException) {
                Log.d("BluetoothClient", "Connection lost: ${e.message}")
            } finally {
                Log.d("BluetoothClient", "Client closed connection")
            }
        }
    }

    suspend fun sendMessage(bytes: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket.outputStream.write(bytes)
                socket.outputStream.write("\n".toByteArray())
                socket.outputStream.flush()
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext false
            }
            true
        }
    }
}