package com.teampro.bleclient

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teampro.bleclient.ui.theme.BleClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


class PaymentActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            BleClientTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    PaymentScreen(
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentScreen() {
    var typeCommand: CommandType by remember { mutableStateOf(CommandType.CREATE_PAYMENT) }
    var transactionId: String by remember { mutableStateOf("") }

    var resultText by remember { mutableStateOf("") }

    val app = LocalContext.current.applicationContext as MyApp

    DisposableEffect(Unit) {
        app.setOnResultListener { data ->
            Log.d("BLE", "Ответ: $data")
            when (typeCommand) {
                CommandType.CREATE_PAYMENT -> {
                    transactionId = JSONObject(data).getString("id")
                    resultText = "Оплата создана: $data"
                }

                CommandType.CHECK_PAYMENT -> {
                    resultText = "Проверка оплаты: $data"
                }

                CommandType.CANCEL_PAYMENT -> {
                    resultText = "Отмена оплаты: $data"
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Оплата", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = {
                val operation = "{\"url\":\"/create-payment\",\"body\":{\"amount\":0.01}}"
                resultText = "Отправлено: $operation"
                typeCommand = CommandType.CREATE_PAYMENT
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать оплату")
        }

        Button(
            onClick = {
                if (transactionId.isEmpty()) {
                    resultText = "Сначала создайте оплату"
                    return@Button
                }
                val operation =
                    "{\"url\":\"/check_payment_status\",\"body\":{\"id\":\"$transactionId\"}}"
                resultText = "Отправлено: $operation"
                typeCommand = CommandType.CHECK_PAYMENT
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверить оплату")
        }

        Button(
            onClick = {
                if (transactionId.isEmpty()) {
                    resultText = "Сначала создайте оплату"
                    return@Button
                }
                val operation =
                    "{\"url\":\"/cancel\",\"body\":{}}"
                resultText = "Отправлено: $operation"
                typeCommand = CommandType.CANCEL_PAYMENT
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отменить оплату")
        }

        if (resultText.isNotEmpty()) {
            Text(
                text = resultText,
                fontSize = 14.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BleClientTheme {
        PaymentScreen()
    }
}