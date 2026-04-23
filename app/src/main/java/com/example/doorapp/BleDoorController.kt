package com.example.doorapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.os.Build
import android.location.LocationManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleDoorController(private val context: Context) {
  sealed interface BleScanResult {
    data class Success(val devices: List<String>) : BleScanResult
    data class Failure(val message: String, val log: String, val devices: List<String>) : BleScanResult
  }

  sealed interface Result {
    data class Success(
      val challenge: String,
      val response: String,
      val status: String,
      val debug: String,
    ) : Result

    data class Failure(val message: String, val log: String) : Result
  }

  @SuppressLint("MissingPermission")
  suspend fun openDoor(onLog: (String) -> Unit): Result {
    val lines = mutableListOf<String>()
    fun log(message: String) {
      lines += message
      onLog(message)
    }

    if (!BlePermissions.hasAll(context)) {
      return Result.Failure("Concedi i permessi Bluetooth all'app", lines.joinToString("\n"))
    }

    val manager = context.getSystemService(BluetoothManager::class.java)
      ?: return Result.Failure("BluetoothManager non disponibile", lines.joinToString("\n"))
    val adapter: BluetoothAdapter = manager.adapter
      ?: return Result.Failure("Bluetooth non disponibile", lines.joinToString("\n"))

    log("[bt] adapter enabled=${adapter.isEnabled} state=${adapterStateName(adapter.state)}")
    log("[bt] scanner available=${adapter.bluetoothLeScanner != null}")
    log("[bt] location enabled=${isLocationEnabled(context)}")

    if (!adapter.isEnabled) {
      return Result.Failure("Attiva il Bluetooth sul telefono", lines.joinToString("\n"))
    }

    return withContext(Dispatchers.IO) {
      try {
        withTimeout(25_000) {
          log("[scan] avvio ricerca ${BleDoorConfig.deviceName}")
          val device = scanForDevice(adapter, ::log)
            ?: run {
              log("[scan] target non trovato, avvio scansione estesa di debug")
              val nearby = scanAllDevices(adapter, ::log, durationMs = 5_000)
              val suffix = if (nearby.isEmpty()) "nessun device BLE rilevato" else "visti ${nearby.size} device BLE"
              return@withTimeout Result.Failure("Device ${BleDoorConfig.deviceName} non trovato ($suffix)", lines.joinToString("\n"))
            }

          log("[scan] trovato device name=${device.name ?: "<null>"} address=${device.address}")

          val session = GattSession(context, device, ::log)
          try {
            log("[gatt] connect start")
            session.connect()
            log("[gatt] connect ok")

            val challenge = session.readAscii(BleDoorConfig.challengeUuid)
            if (challenge.isBlank()) {
              return@withTimeout Result.Failure("Challenge vuota", lines.joinToString("\n"))
            }
            log("[gatt] challenge=$challenge")

            val response = Fnv1a.responseFor(challenge, BleDoorConfig.sharedSecret)
            log("[gatt] response=$response")
            session.writeAscii(BleDoorConfig.responseUuid, response)
            log("[gatt] write response ok")

            val status = session.readAscii(BleDoorConfig.statusUuid)
            log("[gatt] status=$status")
            val debug = session.readAscii(BleDoorConfig.debugUuid)
            log("[gatt] debug=$debug")

            if (status == "OK") {
              Result.Success(
                challenge = challenge,
                response = response,
                status = "Porta aperta",
                debug = debug.ifBlank { "OK" },
              )
            } else {
              Result.Failure(
                "Esito BLE: ${status.ifBlank { "sconosciuto" }}${if (debug.isNotBlank()) " | $debug" else ""}",
                lines.joinToString("\n"),
              )
            }
          } finally {
            log("[gatt] close")
            session.close()
          }
        }
      } catch (e: TimeoutCancellationException) {
        log("[error] timeout durante ricerca o sessione BLE")
        Result.Failure(
          "Timeout BLE: device non trovato o non risponde",
          lines.joinToString("\n"),
        )
      } catch (e: Exception) {
        log("[error] ${e::class.java.simpleName}: ${e.message ?: "Errore BLE"}")
        Result.Failure(e.message ?: "Errore BLE", lines.joinToString("\n"))
      }
    }
  }

  suspend fun scanNearby(onLog: (String) -> Unit): BleScanResult {
    val lines = mutableListOf<String>()
    fun log(message: String) {
      lines += message
      onLog(message)
    }

    if (!BlePermissions.hasAll(context)) {
      return BleScanResult.Failure("Concedi i permessi Bluetooth all'app", lines.joinToString("\n"), emptyList())
    }

    val manager = context.getSystemService(BluetoothManager::class.java)
      ?: return BleScanResult.Failure("BluetoothManager non disponibile", lines.joinToString("\n"), emptyList())
    val adapter: BluetoothAdapter = manager.adapter
      ?: return BleScanResult.Failure("Bluetooth non disponibile", lines.joinToString("\n"), emptyList())

    log("[bt] adapter enabled=${adapter.isEnabled} state=${adapterStateName(adapter.state)}")
    log("[bt] scanner available=${adapter.bluetoothLeScanner != null}")
    log("[bt] location enabled=${isLocationEnabled(context)}")

    if (!adapter.isEnabled) {
      return BleScanResult.Failure("Attiva il Bluetooth sul telefono", lines.joinToString("\n"), emptyList())
    }

    return withContext(Dispatchers.IO) {
      try {
        val devices = scanAllDevices(adapter, ::log, durationMs = 8_000)
        BleScanResult.Success(devices)
      } catch (e: Exception) {
        log("[error] ${e::class.java.simpleName}: ${e.message ?: "Errore BLE"}")
        BleScanResult.Failure(e.message ?: "Errore BLE", lines.joinToString("\n"), emptyList())
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun scanForDevice(adapter: BluetoothAdapter, log: (String) -> Unit): BluetoothDevice? {
    val scanner = adapter.bluetoothLeScanner ?: return null
    val found = CompletableDeferred<BluetoothDevice?>()
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()

    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val advName = record?.deviceName
        val serviceUuids = record?.serviceUuids.orEmpty()
        log("[scan] result cb=$callbackType rssi=${result.rssi} name=${device.name ?: "<null>"} adv=${advName ?: "<null>"} addr=${device.address} uuids=${serviceUuids.joinToString { it.uuid.toString() }} raw=${scanRecordSummary(record)}")
        val matchesName = device.name == BleDoorConfig.deviceName || advName == BleDoorConfig.deviceName
        val matchesService = serviceUuids.any { it.uuid == BleDoorConfig.serviceUuid }
        if ((matchesName || matchesService) && !found.isCompleted) {
          log("[scan] target match by ${if (matchesName && matchesService) "name+service" else if (matchesName) "name" else "service"}")
          found.complete(device)
        }
      }

      override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
        results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
      }

      override fun onScanFailed(errorCode: Int) {
        log("[scan] failed code=$errorCode reason=${scanFailureReason(errorCode)}")
        if (!found.isCompleted) {
          found.completeExceptionally(IllegalStateException("Scan BLE fallita: $errorCode ${scanFailureReason(errorCode)}"))
        }
      }
    }

    log("[scan] start filters=none mode=LOW_LATENCY target=${BleDoorConfig.deviceName}|${BleDoorConfig.serviceUuid}")
    scanner.startScan(null, settings, callback)
    return try {
      withTimeout(10_000) { found.await() }
    } finally {
      log("[scan] stop")
      scanner.stopScan(callback)
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun scanAllDevices(adapter: BluetoothAdapter, log: (String) -> Unit, durationMs: Long): List<String> {
    val scanner = adapter.bluetoothLeScanner ?: return emptyList()
    val devices = linkedSetOf<String>()
    val done = CompletableDeferred<Unit>()
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()

    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val advName = record?.deviceName ?: "<null>"
        val serviceUuids = record?.serviceUuids.orEmpty().joinToString { it.uuid.toString() }
        val line = "name=${device.name ?: "<null>"} adv=$advName addr=${device.address} rssi=${result.rssi} uuids=$serviceUuids raw=${scanRecordSummary(record)}"
        if (devices.add(line)) {
          log("[scan-all] $line")
        }
      }

      override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
        results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
      }

      override fun onScanFailed(errorCode: Int) {
        log("[scan-all] failed code=$errorCode reason=${scanFailureReason(errorCode)}")
        if (!done.isCompleted) {
          done.completeExceptionally(IllegalStateException("Scan BLE fallita: $errorCode ${scanFailureReason(errorCode)}"))
        }
      }
    }

    log("[scan-all] start mode=LOW_LATENCY duration_ms=$durationMs")
    scanner.startScan(null, settings, callback)
    return try {
      withTimeout(durationMs + 2_000) {
        delay(durationMs)
        done.complete(Unit)
        done.await()
      }
      devices.toList()
    } finally {
      log("[scan-all] stop")
      scanner.stopScan(callback)
    }
  }
}

@SuppressLint("MissingPermission")
private class GattSession(
  private val context: Context,
  private val device: BluetoothDevice,
  private val log: (String) -> Unit,
) {
  private var gatt: BluetoothGatt? = null
  private var service: BluetoothGattService? = null

  private var connectDeferred: CompletableDeferred<Unit>? = null
  private var readDeferred: CompletableDeferred<ByteArray>? = null
  private var readUuid: UUID? = null
  private var writeDeferred: CompletableDeferred<Unit>? = null
  private var writeUuid: UUID? = null

  private val callback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      log("[callback] connection status=$status newState=$newState")
      when {
        status != BluetoothGatt.GATT_SUCCESS -> {
          connectDeferred?.completeExceptionally(IllegalStateException("Connessione GATT fallita: $status"))
          readDeferred?.completeExceptionally(IllegalStateException("Connessione persa durante read: $status"))
          writeDeferred?.completeExceptionally(IllegalStateException("Connessione persa durante write: $status"))
        }

        newState == BluetoothProfile.STATE_CONNECTED -> {
          if (!gatt.discoverServices()) {
            connectDeferred?.completeExceptionally(IllegalStateException("discoverServices fallita"))
          }
        }

        newState == BluetoothProfile.STATE_DISCONNECTED -> {
          val error = IllegalStateException("Device BLE disconnesso")
          connectDeferred?.completeExceptionally(error)
          readDeferred?.completeExceptionally(error)
          writeDeferred?.completeExceptionally(error)
        }
      }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      log("[callback] services discovered status=$status")
      if (status == BluetoothGatt.GATT_SUCCESS) {
        service = gatt.getService(BleDoorConfig.serviceUuid)
        if (service != null) {
          val uuids = service!!.characteristics.joinToString { it.uuid.toString() }
          log("[gatt] service found characteristics=$uuids")
          connectDeferred?.complete(Unit)
        } else {
          connectDeferred?.completeExceptionally(IllegalStateException("Servizio BLE non trovato"))
        }
      } else {
        connectDeferred?.completeExceptionally(IllegalStateException("Services discovery fallita: $status"))
      }
    }

    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
      status: Int,
    ) {
      log("[callback] read uuid=${characteristic.uuid} status=$status bytes=${value.size}")
      if (characteristic.uuid != readUuid || readDeferred == null || readDeferred!!.isCompleted) return
      if (status == BluetoothGatt.GATT_SUCCESS) {
        readDeferred?.complete(value)
      } else {
        readDeferred?.completeExceptionally(IllegalStateException("Read fallita: $status"))
      }
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int,
    ) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
      log("[callback] read(old) uuid=${characteristic.uuid} status=$status bytes=${characteristic.value?.size ?: 0}")
      if (characteristic.uuid != readUuid || readDeferred == null || readDeferred!!.isCompleted) return
      if (status == BluetoothGatt.GATT_SUCCESS) {
        readDeferred?.complete(characteristic.value ?: byteArrayOf())
      } else {
        readDeferred?.completeExceptionally(IllegalStateException("Read fallita: $status"))
      }
    }

    override fun onCharacteristicWrite(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int,
    ) {
      log("[callback] write uuid=${characteristic.uuid} status=$status")
      if (characteristic.uuid != writeUuid || writeDeferred == null || writeDeferred!!.isCompleted) return
      if (status == BluetoothGatt.GATT_SUCCESS) {
        writeDeferred?.complete(Unit)
      } else {
        writeDeferred?.completeExceptionally(IllegalStateException("Write fallita: $status"))
      }
    }
  }

  suspend fun connect() {
    val deferred = CompletableDeferred<Unit>()
    connectDeferred = deferred
    gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    withTimeout(10_000) { deferred.await() }
  }

  suspend fun readAscii(uuid: UUID): String {
    val currentGatt = gatt ?: throw IllegalStateException("Gatt non inizializzato")
    val currentService = service ?: throw IllegalStateException("Servizio BLE non inizializzato")
    val characteristic = currentService.getCharacteristic(uuid)
      ?: throw IllegalStateException("Characteristic $uuid non trovata")
    log("[gatt] read request uuid=$uuid props=${characteristic.properties}")

    readUuid = uuid
    val deferred = CompletableDeferred<ByteArray>()
    readDeferred = deferred

    if (!currentGatt.readCharacteristic(characteristic)) {
      throw IllegalStateException("Avvio lettura fallito")
    }

    val data = withTimeout(5_000) { deferred.await() }
    return data.toString(StandardCharsets.UTF_8).trim()
  }

  suspend fun writeAscii(uuid: UUID, value: String) {
    val currentGatt = gatt ?: throw IllegalStateException("Gatt non inizializzato")
    val currentService = service ?: throw IllegalStateException("Servizio BLE non inizializzato")
    val characteristic = currentService.getCharacteristic(uuid)
      ?: throw IllegalStateException("Characteristic $uuid non trovata")
    log("[gatt] write request uuid=$uuid props=${characteristic.properties} value=$value")

    writeUuid = uuid
    val deferred = CompletableDeferred<Unit>()
    writeDeferred = deferred
    val bytes = value.toByteArray(StandardCharsets.UTF_8)

    val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      currentGatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
    } else {
      @Suppress("DEPRECATION")
      run {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = bytes
        currentGatt.writeCharacteristic(characteristic)
      }
    }

    if (!started) {
      throw IllegalStateException("Avvio scrittura fallito")
    }

    withTimeout(5_000) { deferred.await() }
  }

  fun close() {
    gatt?.disconnect()
    gatt?.close()
    gatt = null
    service = null
  }
}

private fun scanFailureReason(errorCode: Int): String = when (errorCode) {
  ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "already_started"
  ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "app_registration_failed"
  ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "feature_unsupported"
  ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "internal_error"
  5 -> "out_of_hardware_resources"
  6 -> "scanning_too_frequently"
  else -> "unknown"
}

private fun scanRecordSummary(record: ScanRecord?): String {
  if (record == null) return "<null>"
  val bytes = record.bytes ?: return "<empty>"
  return bytes.take(24).joinToString(separator = "") { "%02X".format(it) }
}

private fun adapterStateName(state: Int): String = when (state) {
  BluetoothAdapter.STATE_OFF -> "OFF"
  BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
  BluetoothAdapter.STATE_ON -> "ON"
  BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
  else -> state.toString()
}

private fun isLocationEnabled(context: Context): Boolean {
  val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
  return try {
    locationManager.isLocationEnabled
  } catch (_: Exception) {
    false
  }
}
