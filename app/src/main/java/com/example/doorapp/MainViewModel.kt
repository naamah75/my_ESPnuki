package com.example.doorapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val controller = BleDoorController(application.applicationContext)

  private val _uiState = MutableStateFlow(DoorUiState())
  val uiState: StateFlow<DoorUiState> = _uiState.asStateFlow()

  fun openDoor() {
    if (_uiState.value.isBusy) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isBusy = true,
        status = "Connessione BLE in corso...",
        challenge = "",
        debug = "",
        lastResponse = "",
        scanResults = emptyList(),
        log = "[start] apertura richiesta",
      )
      when (val result = controller.openDoor { line -> appendLog(line) }) {
        is BleDoorController.Result.Success -> {
          _uiState.value = DoorUiState(
            isBusy = false,
            status = result.status,
            challenge = result.challenge,
            debug = result.debug,
            lastResponse = result.response,
            log = _uiState.value.log,
          )
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

  private fun appendLog(line: String) {
    _uiState.value = _uiState.value.copy(
      log = if (_uiState.value.log.isBlank()) line else _uiState.value.log + "\n" + line,
    )
  }
}
