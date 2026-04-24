package com.example.doorapp

data class PeripheralField(
  val label: String,
  val value: String,
)

enum class AppScreen {
  HOME,
  ACTIONS,
  SETTINGS,
  INFO,
}

data class DoorUiState(
  val isBusy: Boolean = false,
  val status: String = "Idle",
  val challenge: String = "",
  val debug: String = "",
  val lastResponse: String = "",
  val log: String = "",
  val scanResults: List<String> = emptyList(),
  val peripheralFields: List<PeripheralField> = emptyList(),
  val currentScreen: AppScreen = AppScreen.HOME,
  val visualUnlockedUntilMillis: Long = 0,
  val lastSeenRssi: Int? = null,
  val bleConnectable: Boolean? = null,
  val bleSharedSecret: String = "",
  val bleSharedSecretConfigured: Boolean = false,
  val biometricProtectionEnabled: Boolean = false,
  val biometricAvailable: Boolean = false,
)
