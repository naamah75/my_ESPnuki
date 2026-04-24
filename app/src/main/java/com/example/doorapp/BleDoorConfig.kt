package com.example.doorapp

import android.content.Context
import java.util.UUID

object BleDoorConfig {
  private const val prefsName = "door_app"
  private const val bleSecretKey = "ble_shared_secret"

  const val deviceName = "ibeacon-detector-test"

  fun sharedSecret(context: Context): String {
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val storedSecret = prefs.getString(bleSecretKey, "").orEmpty().trim()
    if (storedSecret.isNotBlank()) return storedSecret
    return BuildConfig.BLE_SHARED_SECRET.trim()
  }

  fun saveSharedSecret(context: Context, value: String) {
    context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
      .edit()
      .putString(bleSecretKey, value.trim())
      .apply()
  }

  fun hasSharedSecret(context: Context): Boolean = sharedSecret(context).isNotBlank()

  val serviceUuid: UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
  val challengeUuid: UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")
  val responseUuid: UUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")
  val statusUuid: UUID = UUID.fromString("19b10003-e8f2-537e-4f6c-d104768a1214")
  val debugUuid: UUID = UUID.fromString("19b10004-e8f2-537e-4f6c-d104768a1214")
  val snapshotUuid: UUID = UUID.fromString("19b10005-e8f2-537e-4f6c-d104768a1214")
  val commandUuid: UUID = UUID.fromString("19b10006-e8f2-537e-4f6c-d104768a1214")
}
