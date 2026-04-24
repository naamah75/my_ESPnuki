package com.example.doorapp

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val controller = BleDoorController(application.applicationContext)
  private val prefs = application.getSharedPreferences("door_app", Context.MODE_PRIVATE)

  private val _uiState = MutableStateFlow(
    DoorUiState(
      biometricProtectionEnabled = prefs.getBoolean("biometric_protection", false),
      biometricAvailable = isBiometricAvailable(application.applicationContext),
    ),
  )
  val uiState: StateFlow<DoorUiState> = _uiState.asStateFlow()

  fun navigateTo(screen: AppScreen) {
    _uiState.value = _uiState.value.copy(currentScreen = screen)
  }

  fun setBiometricProtection(enabled: Boolean) {
    prefs.edit().putBoolean("biometric_protection", enabled).apply()
    _uiState.value = _uiState.value.copy(biometricProtectionEnabled = enabled)
  }

  fun openDoor() {
    if (_uiState.value.isBusy) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isBusy = true,
        status = "connessione in corso",
        challenge = "",
        debug = "",
        lastResponse = "",
        scanResults = emptyList(),
        peripheralFields = _uiState.value.peripheralFields,
        log = "[start] apertura richiesta",
      )
      when (val result = controller.openDoor { line -> appendLog(line) }) {
        is BleDoorController.Result.Success -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = "sblocco in corso",
            challenge = result.challenge,
            debug = result.debug,
            lastResponse = result.response,
            log = _uiState.value.log,
            peripheralFields = _uiState.value.peripheralFields,
            lastSeenRssi = result.discovery.rssi,
            bleConnectable = result.discovery.connectable,
          )
          monitorRelayPulse()
        }

        is BleDoorController.Result.Failure -> {
          _uiState.value = _uiState.value.copy(isBusy = false, status = result.message, log = result.log)
        }
      }
    }
  }

  fun scanBle() {
    if (_uiState.value.isBusy) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isBusy = true,
        status = "Scansione BLE in corso...",
        scanResults = emptyList(),
        peripheralFields = _uiState.value.peripheralFields,
        log = "[start] scansione BLE richiesta",
      )
      when (val result = controller.scanNearby { line -> appendLog(line) }) {
        is BleDoorController.BleScanResult.Success -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = "Scansione completata: ${result.devices.size} device",
            scanResults = result.devices,
            log = _uiState.value.log,
          )
        }

        is BleDoorController.BleScanResult.Failure -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = result.message,
            scanResults = result.devices,
            log = result.log,
          )
        }
      }
    }
  }

  fun refreshPeripheralData() {
    if (_uiState.value.isBusy) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isBusy = true,
        status = "connessione in corso",
        log = "[start] lettura dati periferica richiesta",
      )
      when (val result = controller.readSnapshot { line -> appendLog(line) }) {
        is BleDoorController.SnapshotResult.Success -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = "idle",
            peripheralFields = result.fields,
            log = result.log,
            lastSeenRssi = result.discovery.rssi,
            bleConnectable = result.discovery.connectable,
          )
        }

        is BleDoorController.SnapshotResult.Failure -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = result.message,
            log = result.log,
          )
        }
      }
    }
  }

  fun setAutoOpen(enabled: Boolean) {
    if (_uiState.value.isBusy) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isBusy = true,
        status = if (enabled) "Abilitazione automazione apertura..." else "Disabilitazione automazione apertura...",
        log = "[start] comando automazione apertura",
      )
      val command = if (enabled) "AUTO_ON" else "AUTO_OFF"
      val message = if (enabled) "Automazione apertura abilitata" else "Automazione apertura disabilitata"
      when (val result = controller.sendCommand(command, message) { line -> appendLog(line) }) {
        is BleDoorController.CommandResult.Success -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = result.message,
            log = result.log,
            lastSeenRssi = result.discovery.rssi,
            bleConnectable = result.discovery.connectable,
          )
          refreshPeripheralData()
        }

        is BleDoorController.CommandResult.Failure -> {
          _uiState.value = _uiState.value.copy(isBusy = false, status = result.message, log = result.log)
        }
      }
    }
  }

  fun restartDevice() {
    if (_uiState.value.isBusy) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isBusy = true,
        status = "Riavvio periferica in corso...",
        log = "[start] comando restart",
      )
      when (val result = controller.sendCommand("RESTART", "Riavvio periferica richiesto") { line -> appendLog(line) }) {
        is BleDoorController.CommandResult.Success -> {
          _uiState.value = _uiState.value.copy(
            isBusy = false,
            status = result.message,
            log = result.log,
            lastSeenRssi = result.discovery.rssi,
            bleConnectable = result.discovery.connectable,
          )
        }

        is BleDoorController.CommandResult.Failure -> {
          _uiState.value = _uiState.value.copy(isBusy = false, status = result.message, log = result.log)
        }
      }
    }
  }

  fun clearVisualUnlock() {
    if (_uiState.value.visualUnlockedUntilMillis == 0L) return
    _uiState.value = _uiState.value.copy(visualUnlockedUntilMillis = 0)
  }

  private suspend fun monitorRelayPulse() {
    var sawUnlocked = false
    repeat(7) {
      delay(350)
      when (val result = controller.readSnapshot { }) {
        is BleDoorController.SnapshotResult.Success -> {
          val relayActive = result.fields.firstOrNull { it.label == "Relay attivo" }?.value == "On"
          _uiState.value = _uiState.value.copy(
            peripheralFields = result.fields,
            lastSeenRssi = result.discovery.rssi,
            bleConnectable = result.discovery.connectable,
            status = when {
              relayActive -> {
                sawUnlocked = true
                "sblocco in corso"
              }

              sawUnlocked -> "blocco in corso"
              else -> _uiState.value.status
            },
          )
          if (sawUnlocked && !relayActive) {
            delay(900)
            _uiState.value = _uiState.value.copy(status = "idle")
            return
          }
        }

        is BleDoorController.SnapshotResult.Failure -> return
      }
    }
    _uiState.value = _uiState.value.copy(status = "idle")
  }

  private fun appendLog(line: String) {
    _uiState.value = _uiState.value.copy(
      log = if (_uiState.value.log.isBlank()) line else _uiState.value.log + "\n" + line,
    )
  }
}

private fun isBiometricAvailable(context: Context): Boolean {
  val manager = BiometricManager.from(context)
  val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
  return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
}
