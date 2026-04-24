package com.example.doorapp

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          DoorApp()
        }
      }
    }
  }
}

@Composable
private fun DoorApp(viewModel: MainViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var missingPermissions by remember { mutableStateOf(BlePermissions.missing(context)) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    missingPermissions = BlePermissions.missing(context)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(text = "Door App", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Versione: ${BuildConfig.VERSION_NAME}")
    Text(text = "Build: ${BuildConfig.BUILD_TIMESTAMP}")
    Text(text = "Device: ${BleDoorConfig.deviceName}")
    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = {
        val missing = BlePermissions.missing(context)
        missingPermissions = missing
        if (missing.isNotEmpty()) {
          launcher.launch(missing)
        } else {
          viewModel.openDoor()
        }
      },
      enabled = !uiState.isBusy,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = if (uiState.isBusy) "Operazione in corso" else "Apri porta")
    }

    Button(
      onClick = {
        val missing = BlePermissions.missing(context)
        missingPermissions = missing
        if (missing.isNotEmpty()) {
          launcher.launch(missing)
        } else {
          viewModel.scanBle()
        }
      },
      enabled = !uiState.isBusy,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = "Scansiona BLE")
    }

    if (missingPermissions.isNotEmpty()) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Permessi BLE richiesti: scan e connect" else "Permesso posizione richiesto per BLE")
    }

    if (uiState.isBusy) {
      CircularProgressIndicator()
    }

    Text(text = "Stato: ${uiState.status}")
    if (uiState.challenge.isNotEmpty()) Text(text = "Challenge: ${uiState.challenge}")
    if (uiState.lastResponse.isNotEmpty()) Text(text = "Response: ${uiState.lastResponse}")
    if (uiState.debug.isNotEmpty()) Text(text = "Debug: ${uiState.debug}")
    if (uiState.log.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = "Log BLE:")
      Text(text = uiState.log)
    }
    if (uiState.scanResults.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = "Periferiche BLE viste:")
      uiState.scanResults.forEach { Text(text = it) }
    }
  }
}
