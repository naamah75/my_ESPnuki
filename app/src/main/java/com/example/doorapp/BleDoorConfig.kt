package com.example.doorapp

import java.util.UUID

object BleDoorConfig {
  const val deviceName = "ibeacon-detector-test"
  const val sharedSecret = "door-test-secret-2026"

  val serviceUuid: UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
  val challengeUuid: UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")
  val responseUuid: UUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")
  val statusUuid: UUID = UUID.fromString("19b10003-e8f2-537e-4f6c-d104768a1214")
  val debugUuid: UUID = UUID.fromString("19b10004-e8f2-537e-4f6c-d104768a1214")
}
