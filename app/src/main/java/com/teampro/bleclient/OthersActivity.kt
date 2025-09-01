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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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


class OthersActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            BleClientTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    OthersScreen(
                    )
                }
            }
        }
    }
}

@Composable
fun OthersScreen() {
    var typeCommand: CommandType by remember { mutableStateOf(CommandType.CREATE_PAYMENT) }
    var transactionMlusId: String by remember { mutableStateOf("") }

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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Others", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = transactionMlusId,
            onValueChange = { transactionMlusId = it },
            label = { Text("Введите ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (transactionMlusId.isNotBlank()) {
                    val operation = "{\"url\":\"/check_mplus_payment\",\"body\":{\"id\":$transactionMlusId}}"
                    resultText = "Отправлено: $operation"
                    CoroutineScope(Dispatchers.Main).launch {
                        bleClient?.sendCommand(operation)
                    }
                } else {
                    resultText = "❌ Введите ID!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверить M+")
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
fun OthersPreview() {
    BleClientTheme {
        OthersScreen()
    }
}