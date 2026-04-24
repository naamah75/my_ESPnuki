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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.location.LocationManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleDoorController(private val context: Context) {
  data class DeviceMatch(
    val device: BluetoothDevice,
    val rssi: Int,
    val connectable: Boolean,
  )

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
      val discovery: DeviceMatch,
    ) : Result

    data class Failure(val message: String, val log: String) : Result
  }

  sealed interface SnapshotResult {
    data class Success(val fields: List<PeripheralField>, val log: String, val discovery: DeviceMatch) : SnapshotResult

    data class Failure(val message: String, val log: String) : SnapshotResult
  }

  sealed interface CommandResult {
    data class Success(val message: String, val log: String, val discovery: DeviceMatch) : CommandResult

    data class Failure(val message: String, val log: String) : CommandResult
  }

  @SuppressLint("MissingPermission")
  suspend fun openDoor(onLog: (String) -> Unit): Result {
    val lines = mutableListOf<String>()
    fun log(message: String) {
      lines += message
      onLog(message)
    }

    val sharedSecret = BleDoorConfig.sharedSecret(context)
    if (sharedSecret.isBlank()) {
      return Result.Failure("Configura il BLE shared secret nelle impostazioni prima di usare lo sblocco", lines.joinToString("\n"))
    }

    if (!BlePermissions.hasAll(context)) {
      return Result.Failure("Concedi i permessi Bluetooth all'app", lines.joinToString("\n"))
    }

    val manager = context.getSystemService(BluetoothManager::class.java)
      ?: return Result.Failure("BluetoothManager non disponibile", lines.joinToString("\n"))
    val adapter: BluetoothAdapter = manager.adapter
      ?: return Result.Failure("Bluetooth non disponibile", lines.joinToString("\n"))

    logEnvironment(context, adapter, ::log)

    if (!adapter.isEnabled) {
      return Result.Failure("Attiva il Bluetooth sul telefono", lines.joinToString("\n"))
    }

    return withContext(Dispatchers.IO) {
      try {
        withTimeout(25_000) {
          log("[scan] avvio ricerca ${BleDoorConfig.deviceName}")
          val discovery = scanForDevice(adapter, ::log)
            ?: run {
              log("[scan] target non trovato, avvio scansione estesa di debug")
              val nearby = scanAllDevices(adapter, ::log, durationMs = 5_000)
              val suffix = if (nearby.isEmpty()) "nessun device BLE rilevato" else "visti ${nearby.size} device BLE"
              return@withTimeout Result.Failure("Device ${BleDoorConfig.deviceName} non trovato ($suffix)", lines.joinToString("\n"))
            }
          val device = discovery.device

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

            val response = Fnv1a.responseFor(challenge, sharedSecret)
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
                discovery = discovery,
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

    logEnvironment(context, adapter, ::log)

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
  private suspend fun scanForDevice(adapter: BluetoothAdapter, log: (String) -> Unit): DeviceMatch? {
    val scanner = adapter.bluetoothLeScanner ?: return null
    val found = CompletableDeferred<DeviceMatch?>()
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()
    val serviceFilter = ScanFilter.Builder()
      .setServiceUuid(ParcelUuid(BleDoorConfig.serviceUuid))
      .build()

    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val advName = record?.deviceName
        val serviceUuids = record?.serviceUuids.orEmpty()
        log("[scan] result cb=$callbackType ${scanResultSummary(result, record, advName, serviceUuids)}")
        val matchesName = device.name == BleDoorConfig.deviceName || advName == BleDoorConfig.deviceName
        val matchesService = serviceUuids.any { it.uuid == BleDoorConfig.serviceUuid }
        if ((matchesName || matchesService) && !found.isCompleted) {
          log("[scan] target match by ${if (matchesName && matchesService) "name+service" else if (matchesName) "name" else "service"}")
          found.complete(DeviceMatch(device = device, rssi = result.rssi, connectable = result.isConnectable))
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

    log("[scan] start pass=service-filter ${scanSettingsSummary(settings)} service=${BleDoorConfig.serviceUuid}")
    val filteredResult = try {
      scanner.startScan(listOf(serviceFilter), settings, callback)
      withTimeout(8_000) { found.await() }
    } catch (_: TimeoutCancellationException) {
      log("[scan] service-filter timeout, provo pass unfiltered")
      null
    } finally {
      log("[scan] stop pass=service-filter")
      scanner.stopScan(callback)
    }

    if (filteredResult != null) return filteredResult

    log("[scan] start pass=unfiltered ${scanSettingsSummary(settings)} target=${BleDoorConfig.deviceName}|${BleDoorConfig.serviceUuid}")
    val modernResult = try {
      scanner.startScan(null, settings, callback)
      withTimeout(6_000) { found.await() }
    } catch (_: TimeoutCancellationException) {
      log("[scan] unfiltered timeout, provo fallback legacy")
      null
    } finally {
      log("[scan] stop pass=unfiltered")
      scanner.stopScan(callback)
    }

    if (modernResult != null) return modernResult

    log("[scan-legacy] fallback start")
    return scanForDeviceLegacy(adapter, log)
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
        val advName = record?.deviceName
        val serviceUuids = record?.serviceUuids.orEmpty()
        val line = scanResultSummary(result, record, advName, serviceUuids)
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

    log("[scan-all] start ${scanSettingsSummary(settings)} duration_ms=$durationMs")
    val devicesFound = try {
      scanner.startScan(null, settings, callback)
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

    if (devicesFound.isNotEmpty()) return devicesFound

    log("[scan-all-legacy] fallback start")
    return scanAllDevicesLegacy(adapter, log, durationMs)
  }

  @Suppress("DEPRECATION")
  @SuppressLint("MissingPermission")
  private suspend fun scanForDeviceLegacy(adapter: BluetoothAdapter, log: (String) -> Unit): DeviceMatch? {
    val found = CompletableDeferred<DeviceMatch?>()
    val callback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
      val advName = parseAdvName(scanRecord)
      log("[scan-legacy] result rssi=$rssi name=${device.name ?: "<null>"} adv=${advName ?: "<null>"} addr=${device.address} raw=${bytesSummary(scanRecord)}")
      val matchesName = device.name == BleDoorConfig.deviceName || advName == BleDoorConfig.deviceName
      if (matchesName && !found.isCompleted) {
        log("[scan-legacy] target match by name")
        found.complete(DeviceMatch(device = device, rssi = rssi, connectable = true))
      }
    }

    if (!adapter.startLeScan(callback)) {
      log("[scan-legacy] start failed")
      return null
    }

    return try {
      withTimeout(5_000) { found.await() }
    } catch (_: TimeoutCancellationException) {
      log("[scan-legacy] timeout")
      null
    } finally {
      log("[scan-legacy] stop")
      adapter.stopLeScan(callback)
    }
  }

  @Suppress("DEPRECATION")
  @SuppressLint("MissingPermission")
  private suspend fun scanAllDevicesLegacy(adapter: BluetoothAdapter, log: (String) -> Unit, durationMs: Long): List<String> {
    val devices = linkedSetOf<String>()
    val callback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
      val advName = parseAdvName(scanRecord) ?: "<null>"
      val line = "name=${device.name ?: "<null>"} adv=$advName addr=${device.address} rssi=$rssi raw=${bytesSummary(scanRecord)}"
      if (devices.add(line)) {
        log("[scan-all-legacy] $line")
      }
    }

    if (!adapter.startLeScan(callback)) {
      log("[scan-all-legacy] start failed")
      return emptyList()
    }

    return try {
      delay(durationMs)
      log("[scan-all-legacy] collected=${devices.size}")
      devices.toList()
    } finally {
      log("[scan-all-legacy] stop")
      adapter.stopLeScan(callback)
    }
  }

  @SuppressLint("MissingPermission")
  suspend fun readSnapshot(onLog: (String) -> Unit): SnapshotResult {
    val lines = mutableListOf<String>()
    fun log(message: String) {
      lines += message
      onLog(message)
    }

    if (!BlePermissions.hasAll(context)) {
      return SnapshotResult.Failure("Concedi i permessi Bluetooth all'app", lines.joinToString("\n"))
    }

    val manager = context.getSystemService(BluetoothManager::class.java)
      ?: return SnapshotResult.Failure("BluetoothManager non disponibile", lines.joinToString("\n"))
    val adapter: BluetoothAdapter = manager.adapter
      ?: return SnapshotResult.Failure("Bluetooth non disponibile", lines.joinToString("\n"))

    logEnvironment(context, adapter, ::log)

    if (!adapter.isEnabled) {
      return SnapshotResult.Failure("Attiva il Bluetooth sul telefono", lines.joinToString("\n"))
    }

    return try {
      withBleSession(adapter, ::log, timeoutMs = 20_000) { session, discovery ->
        val payload = session.readAscii(BleDoorConfig.snapshotUuid)
        log("[snapshot] bytes=${payload.length}")
        SnapshotResult.Success(parseSnapshotFields(payload), lines.joinToString("\n"), discovery)
      }
    } catch (e: Exception) {
      log("[error] ${e.message ?: "Errore BLE"}")
      SnapshotResult.Failure(e.message ?: "Errore BLE", lines.joinToString("\n"))
    }
  }

  @SuppressLint("MissingPermission")
  suspend fun sendCommand(command: String, successMessage: String, onLog: (String) -> Unit): CommandResult {
    val lines = mutableListOf<String>()
    fun log(message: String) {
      lines += message
      onLog(message)
    }

    if (!BlePermissions.hasAll(context)) {
      return CommandResult.Failure("Concedi i permessi Bluetooth all'app", lines.joinToString("\n"))
    }

    val manager = context.getSystemService(BluetoothManager::class.java)
      ?: return CommandResult.Failure("BluetoothManager non disponibile", lines.joinToString("\n"))
    val adapter: BluetoothAdapter = manager.adapter
      ?: return CommandResult.Failure("Bluetooth non disponibile", lines.joinToString("\n"))

    logEnvironment(context, adapter, ::log)

    if (!adapter.isEnabled) {
      return CommandResult.Failure("Attiva il Bluetooth sul telefono", lines.joinToString("\n"))
    }

    return try {
      withBleSession(adapter, ::log, timeoutMs = 20_000) { session, discovery ->
        log("[command] write=$command")
        session.writeAscii(BleDoorConfig.commandUuid, command)
        CommandResult.Success(successMessage, lines.joinToString("\n"), discovery)
      }
    } catch (e: Exception) {
      log("[error] ${e.message ?: "Errore BLE"}")
      CommandResult.Failure(e.message ?: "Errore BLE", lines.joinToString("\n"))
    }
  }

  private suspend fun <T> withBleSession(
    adapter: BluetoothAdapter,
    log: (String) -> Unit,
    timeoutMs: Long,
    block: suspend (GattSession, DeviceMatch) -> T,
  ): T {
    return withContext(Dispatchers.IO) {
      try {
        withTimeout(timeoutMs) {
          log("[session] avvio ricerca ${BleDoorConfig.deviceName}")
          val discovery = scanForDevice(adapter, log)
            ?: throw IllegalStateException("Device ${BleDoorConfig.deviceName} non trovato")

          log("[session] trovato device name=${discovery.device.name ?: "<null>"} address=${discovery.device.address} rssi=${discovery.rssi}")
          val session = GattSession(context, discovery.device, log)
          try {
            log("[gatt] connect start")
            session.connect()
            log("[gatt] connect ok")
            block(session, discovery)
          } finally {
            log("[gatt] close")
            session.close()
          }
        }
      } catch (e: TimeoutCancellationException) {
        throw IllegalStateException("Timeout BLE", e)
      }
    }
  }
}

private fun logEnvironment(context: Context, adapter: BluetoothAdapter, log: (String) -> Unit) {
  log("[env] sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} manufacturer=${Build.MANUFACTURER} model=${Build.MODEL}")
  log("[env] target name=${BleDoorConfig.deviceName} service=${BleDoorConfig.serviceUuid}")
  log("[perm] required=${BlePermissions.required().joinToString()} missing=${BlePermissions.missing(context).joinToString().ifBlank { "<none>" }}")
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    log("[perm] scan=${permissionState(context, android.Manifest.permission.BLUETOOTH_SCAN)} connect=${permissionState(context, android.Manifest.permission.BLUETOOTH_CONNECT)} fine_location=${permissionState(context, android.Manifest.permission.ACCESS_FINE_LOCATION)}")
  } else {
    log("[perm] fine_location=${permissionState(context, android.Manifest.permission.ACCESS_FINE_LOCATION)}")
  }
  log("[bt] adapter enabled=${adapter.isEnabled} state=${adapterStateName(adapter.state)}")
  log("[bt] scanner available=${adapter.bluetoothLeScanner != null} offloaded_filtering=${adapter.isOffloadedFilteringSupported} offloaded_scan_batching=${adapter.isOffloadedScanBatchingSupported} multiple_advertisement=${adapter.isMultipleAdvertisementSupported}")
  log("[bt] location enabled=${isLocationEnabled(context)}")
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

    @Suppress("DEPRECATION")
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
  return bytesSummary(record.bytes)
}

private fun scanResultSummary(
  result: android.bluetooth.le.ScanResult,
  record: ScanRecord?,
  advName: String?,
  serviceUuids: List<ParcelUuid>,
): String {
  val device = result.device
  return "rssi=${result.rssi} name=${device?.name ?: "<null>"} adv=${advName ?: "<null>"} addr=${device?.address ?: "<null>"} uuids=${serviceUuids.joinToString { it.uuid.toString() }.ifBlank { "<none>" }} legacy=${result.isLegacy} connectable=${result.isConnectable} raw=${scanRecordSummary(record)}"
}

private fun scanSettingsSummary(settings: ScanSettings): String {
  return "mode=${settings.scanMode} callback_type=${settings.callbackType} report_delay_ms=${settings.reportDelayMillis}"
}

private fun bytesSummary(bytes: ByteArray?): String {
  if (bytes == null) return "<empty>"
  return bytes.take(24).joinToString(separator = "") { "%02X".format(it) }
}

private fun permissionState(context: Context, permission: String): String {
  return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
    "granted"
  } else {
    "missing"
  }
}

private fun parseSnapshotFields(payload: String): List<PeripheralField> {
  return payload
    .lineSequence()
    .mapNotNull { line ->
      val separator = line.indexOf('=')
      if (separator <= 0) return@mapNotNull null
      val key = line.substring(0, separator)
      val rawValue = line.substring(separator + 1)
      PeripheralField(label = snapshotLabelForKey(key), value = snapshotValueForKey(key, rawValue))
    }
    .toList()
}

private fun snapshotLabelForKey(key: String): String = when (key) {
  "auto_open" -> "Automazione apertura"
  "relay_lock" -> "Relay Lock"
  "relay_active" -> "Relay attivo"
  "wifi_status" -> "WiFi connesso"
  "bssid" -> "BSSID"
  "boot_time" -> "Boot time"
  "cpu_temp_c" -> "CPU temperature"
  "ip" -> "IP address"
  "mac" -> "MAC address"
  "ssid" -> "SSID"
  "uptime_s" -> "Uptime"
  "wifi_signal_dbm" -> "WiFi Signal"
  else -> key.replace('_', ' ')
}

private fun snapshotValueForKey(key: String, rawValue: String): String = when (key) {
  "auto_open", "relay_lock", "relay_active", "wifi_status" -> if (rawValue == "1") "On" else "Off"
  "cpu_temp_c" -> rawValue.ifBlank { "-" }.let { if (it == "-") it else "$it C" }
  "wifi_signal_dbm" -> rawValue.ifBlank { "-" }.let { if (it == "-") it else "$it dBm" }
  "uptime_s" -> rawValue.ifBlank { "-" }.let { if (it == "-") it else "$it s" }
  else -> rawValue.ifBlank { "-" }
}

private fun parseAdvName(scanRecord: ByteArray?): String? {
  if (scanRecord == null) return null
  var index = 0
  while (index < scanRecord.size) {
    val length = scanRecord[index].toInt() and 0xFF
    if (length == 0) break
    val typeIndex = index + 1
    val dataIndex = index + 2
    val endIndex = index + 1 + length
    if (endIndex > scanRecord.size) break
    val type = scanRecord[typeIndex].toInt() and 0xFF
    if (type == 0x08 || type == 0x09) {
      return scanRecord.copyOfRange(dataIndex, endIndex).toString(StandardCharsets.UTF_8)
    }
    index += length + 1
  }
  return null
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
