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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teampro.bleclient.ui.theme.BleClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


class CashoutActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            BleClientTheme {
                Scaffold { innerPadding ->
                    CashoutScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

}

@Composable
fun CashoutScreen(
    modifier: Modifier = Modifier
) {
    var typeCommand: CommandType by remember { mutableStateOf(CommandType.CREATE_CASHOUT) }
    var resultText by remember { mutableStateOf("") }

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
        Text(text = "Cashout", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = {
                val operation =
                    "{\"url\":\"/cash-out\",\"body\":{\"amount\":\"1\",\"get_last_status_transaction\":false}}"
                resultText = "Отправлено: $operation"
                typeCommand = CommandType.CREATE_CASHOUT
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать")
        }

        Button(
            onClick = {
                val operation =
                    "{\"url\":\"/check\",\"body\":{}}"
                resultText = "Отправлено: $operation"
                typeCommand = CommandType.CHECK_CASHOUT
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient?.sendCommand(operation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверка")
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
fun CashoutPreview() {
    BleClientTheme {
        CashoutScreen()
    }
}