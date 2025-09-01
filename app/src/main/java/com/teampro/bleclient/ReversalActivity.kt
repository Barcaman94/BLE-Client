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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teampro.bleclient.ui.theme.BleClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ReversalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            BleClientTheme {
                Scaffold { innerPadding ->
                    ReversalScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun ReversalScreen(
    modifier: Modifier = Modifier
) {
    var typeCommand: CommandType by remember { mutableStateOf(CommandType.REPORT_ZX_V2) }
    var resultText by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    val app = LocalContext.current.applicationContext as MyApp

    DisposableEffect(Unit) {
        app.setOnResultListener { data ->
            Log.d("BLE", "Ответ: $data")
            when (typeCommand) {
                CommandType.REPORT_ZX_V2 -> {
                    resultText = "Отчет v2: $data"
                }

                CommandType.REPORT_ZX -> {
                    resultText = "Отчет : $data"
                }

                else -> {
                    resultText = "Ответ: $data"
                }
            }
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
        Text(text = "Возврат", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            label = { Text("Введите ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Введите Сумму") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                when {
                    id.isBlank() -> {
                        resultText = "❌ Введите ID!"
                    }
                    amount.isBlank() -> {
                        resultText = "❌ Введите сумму!"
                    }
                    amount.toDoubleOrNull() == null -> {
                        resultText = "❌ Сумма должна быть числом!"
                    }
                    else -> {
                        val operation =
                            "{\"url\":\"/reversal\",\"body\":{\"mbank_transaction_id\":\"$id\",\"amount\":\"$amount\"}}"
                        resultText = "Отправлено: $operation"
                        typeCommand = CommandType.REPORT_ZX_V2
                        CoroutineScope(Dispatchers.Main).launch {
                            bleClient?.sendCommand(operation)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Возврат")
        }

        Button(
            onClick = {
                when {
                    id.isBlank() -> {
                        resultText = "❌ Введите ID!"
                    }
                    amount.isBlank() -> {
                        resultText = "❌ Введите сумму!"
                    }
                    amount.toDoubleOrNull() == null -> {
                        resultText = "❌ Сумма должна быть числом!"
                    }
                    else -> {
                        val operation =
                            "{\"url\":\"/v2/reversal\",\"body\":{\"mbank_transaction_id\":\"$id\",\"amount\":\"$amount\"}}"
                        resultText = "Отправлено: $operation"
                        typeCommand = CommandType.REPORT_ZX
                        CoroutineScope(Dispatchers.Main).launch {
                            bleClient?.sendCommand(operation)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Возврат v2")
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
fun ReversalPreview() {
    BleClientTheme {
        ReversalScreen(
        )
    }
}