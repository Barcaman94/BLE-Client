package com.teampro.bleclient

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teampro.bleclient.ui.theme.BleClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ShiftActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            BleClientTheme {
                Scaffold { innerPadding ->
                    ShiftScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

}

@Composable
fun ShiftScreen(
    modifier: Modifier = Modifier
) {
    var typeCommand: CommandType by remember { mutableStateOf(CommandType.REPORT_ZX_V2) }
    var resultText by remember { mutableStateOf("") }
    var cashiersShiftId by remember { mutableStateOf("") }

    val app = LocalContext.current.applicationContext as MyApp

    DisposableEffect(Unit) {
        app.setOnResultListener { data ->
            Log.d("BLE", "Ответ: $data")
            resultText = "Ответ: $data"
        }
        onDispose {
            app.setOnResultListener(null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Смены", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = {
                val operation =
                    "{\"url\":\"/start_work\",\"body\":{}}"
                resultText = "Отправлено: $operation"
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть смену")
        }

        Button(
            onClick = {
                val operation =
                    "{\"url\":\"/end_work\",\"body\":{}}"
                resultText = "Отправлено: $operation"
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Закрыть смену")
        }

        Button(
            onClick = {
                val operation =
                    "{\"url\":\"/shift_cash_register\",\"body\":{}}"
                resultText = "Отправлено: $operation"
                typeCommand = CommandType.CASH_REGISTER
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Получить Кассы")
        }

        OutlinedTextField(
            value = cashiersShiftId,
            onValueChange = { cashiersShiftId = it },
            label = { Text("Введите ID кассы") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (cashiersShiftId.isBlank()) {
                    resultText = "❌ Введите ID кассы!"
                } else {
                    val operation =
                        "{\"url\":\"/kkm_start_work\",\"body\":{\"id\":\"$cashiersShiftId\"}}"
                    resultText = "Отправлено: $operation"
                    CoroutineScope(Dispatchers.Main).launch {
                        bleClient?.sendCommand(operation)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть ККМ смену")
        }

        Button(
            onClick = {
                if (cashiersShiftId.isBlank()) {
                    resultText = "❌ Введите ID кассы!"
                } else {
                    val operation =
                        "{\"url\":\"/kkm_end_work\",\"body\":{\"id\":\"$cashiersShiftId\"}}"
                    resultText = "Отправлено: $operation"
                    CoroutineScope(Dispatchers.Main).launch {
                        bleClient?.sendCommand(operation)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Закрыть ККМ смену")
        }

        if (resultText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, Color.Gray)
                    .padding(8.dp)
            ) {
                Text(
                    text = resultText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShiftPreview() {
    BleClientTheme {
        ShiftScreen()
    }
}