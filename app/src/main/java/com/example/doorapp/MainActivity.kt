package com.example.doorapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val AppBackground = Color(0xFF050505)
private val SheetBackground = Color(0xFF1A1A1A)
private val AccentYellow = Color(0xFFF3C544)
private val SoftText = Color(0xFFB7B7B7)
private val RingColor = Color(0xFFF5F2EB)
private val RingTrack = Color(0x33F5F2EB)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
          DoorApp()
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoorApp(viewModel: MainViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var missingPermissions by remember { mutableStateOf(BlePermissions.missing(context)) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    missingPermissions = BlePermissions.missing(context)
  }
  val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
  val sheetState = rememberBottomSheetScaffoldState()
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    if (BlePermissions.missing(context).isEmpty()) {
      viewModel.refreshPeripheralData()
    }
  }

  fun withPermissions(action: () -> Unit) {
    val missing = BlePermissions.missing(context)
    missingPermissions = missing
    if (missing.isNotEmpty()) {
      launcher.launch(missing)
    } else {
      action()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      DrawerContent(
        currentScreen = uiState.currentScreen,
        hasLog = uiState.log.isNotEmpty(),
        onNavigate = { screen ->
          viewModel.navigateTo(screen)
          scope.launch { drawerState.close() }
        },
        onRefresh = {
          withPermissions { viewModel.refreshPeripheralData() }
          scope.launch { drawerState.close() }
        },
        onScan = {
          withPermissions { viewModel.scanBle() }
          scope.launch { drawerState.close() }
        },
        onCopyLog = {
          copyLogToClipboard(context, uiState.log)
          scope.launch { drawerState.close() }
        },
      )
    },
  ) {
    BottomSheetScaffold(
      scaffoldState = sheetState,
      sheetPeekHeight = 120.dp,
      sheetContainerColor = SheetBackground,
      sheetDragHandle = { BottomSheetDefaults.DragHandle(color = SoftText) },
      containerColor = AppBackground,
      sheetContent = {
        DiagnosticsSheet(uiState = uiState)
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(AppBackground)
          .padding(innerPadding)
          .padding(horizontal = 24.dp, vertical = 16.dp),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          HeaderRow(onOpenMenu = { scope.launch { drawerState.open() } })
          Spacer(modifier = Modifier.height(32.dp))
          when (uiState.currentScreen) {
            AppScreen.HOME -> HomeScreen(
              uiState = uiState,
              onOpenDoor = { withPermissions { viewModel.openDoor() } },
            )

            AppScreen.ACTIONS -> ActionsScreen(
              uiState = uiState,
              onRefresh = { withPermissions { viewModel.refreshPeripheralData() } },
              onSetAutoOpen = { enabled -> withPermissions { viewModel.setAutoOpen(enabled) } },
              onRestart = { withPermissions { viewModel.restartDevice() } },
            )

            AppScreen.INFO -> InfoScreen(uiState = uiState)
          }
        }
      }
    }
  }
}

@Composable
private fun HeaderRow(onOpenMenu: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "≡",
      color = AccentYellow,
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.clickable(onClick = onOpenMenu),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "DOOR",
        color = Color.White,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 6.sp,
      )
      Text(text = "secondary BLE access", color = SoftText, style = MaterialTheme.typography.labelSmall)
    }
    Box(
      modifier = Modifier
        .size(10.dp)
        .background(AccentYellow, CircleShape),
    )
  }
}

@Composable
private fun DrawerContent(
  currentScreen: AppScreen,
  hasLog: Boolean,
  onNavigate: (AppScreen) -> Unit,
  onRefresh: () -> Unit,
  onScan: () -> Unit,
  onCopyLog: () -> Unit,
) {
  ModalDrawerSheet(drawerContainerColor = SheetBackground, drawerContentColor = Color.White) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = "Door App",
      color = Color.White,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
    Spacer(modifier = Modifier.height(24.dp))
    DrawerItem("Home", currentScreen == AppScreen.HOME) { onNavigate(AppScreen.HOME) }
    DrawerItem("Azioni", currentScreen == AppScreen.ACTIONS) { onNavigate(AppScreen.ACTIONS) }
    DrawerItem("Info", currentScreen == AppScreen.INFO) { onNavigate(AppScreen.INFO) }
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = "Azioni rapide",
      color = SoftText,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
    DrawerItem("Aggiorna dati BLE", false, onRefresh)
    DrawerItem("Scansione BLE", false, onScan)
    if (hasLog) {
      DrawerItem("Copia log", false, onCopyLog)
    }
  }
}

@Composable
private fun DrawerItem(title: String, selected: Boolean, onClick: () -> Unit) {
  val background = if (selected) AccentYellow.copy(alpha = 0.14f) else Color.Transparent
  val textColor = if (selected) AccentYellow else Color.White
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(background)
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 16.dp),
  ) {
    Text(text = title, color = textColor, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun HomeScreen(uiState: DoorUiState, onOpenDoor: () -> Unit) {
  val relayActive = peripheralValue(uiState.peripheralFields, "Relay attivo") == "On"
  val isUnlocked = relayActive
  val centerLabel = if (isUnlocked) "sbloccato" else "bloccato"

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(28.dp))
    Box(
      modifier = Modifier
        .size(320.dp)
        .clickable(enabled = !uiState.isBusy, onClick = onOpenDoor),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
          color = RingTrack,
          startAngle = 140f,
          sweepAngle = 260f,
          useCenter = false,
          style = stroke,
        )
        drawArc(
          color = RingColor,
          startAngle = 140f,
          sweepAngle = 260f,
          useCenter = false,
          style = stroke,
        )
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (uiState.isBusy) {
          CircularProgressIndicator(color = AccentYellow)
          Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
          text = centerLabel,
          color = Color.White,
          style = MaterialTheme.typography.headlineLarge,
        )
      }
    }
  }
}

@Composable
private fun ActionsScreen(
  uiState: DoorUiState,
  onRefresh: () -> Unit,
  onSetAutoOpen: (Boolean) -> Unit,
  onRestart: () -> Unit,
) {
  val autoOpenEnabled = peripheralValue(uiState.peripheralFields, "Automazione apertura") == "On"

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(text = "Azioni", color = Color.White, style = MaterialTheme.typography.headlineMedium)
    Text(text = "Canale di controllo secondario via BLE", color = SoftText)
    ActionCard(title = "Automazione apertura") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = if (autoOpenEnabled) "Abilitata" else "Disabilitata", color = Color.White)
          Text(text = "Attiva o disattiva l'automazione dal dispositivo", color = SoftText)
        }
        Switch(checked = autoOpenEnabled, onCheckedChange = { onSetAutoOpen(it) }, enabled = !uiState.isBusy)
      }
    }
    ActionCard(title = "Periferica") {
      Button(onClick = onRefresh, enabled = !uiState.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text("Aggiorna dati BLE")
      }
      Spacer(modifier = Modifier.height(12.dp))
      Button(onClick = onRestart, enabled = !uiState.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text("Riavvia ESP32")
      }
    }
  }
}

@Composable
private fun ActionCard(title: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(SheetBackground, RoundedCornerShape(24.dp))
      .padding(20.dp),
  ) {
    Text(text = title, color = Color.White, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(16.dp))
    content()
  }
}

@Composable
private fun InfoScreen(uiState: DoorUiState) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(text = "Info", color = Color.White, style = MaterialTheme.typography.headlineMedium)
    InfoRow("Versione", BuildConfig.VERSION_NAME)
    InfoRow("Build", BuildConfig.BUILD_TIMESTAMP)
    InfoRow("Device BLE", BleDoorConfig.deviceName)
    InfoRow("Repository GitHub", "da definire")
    if (uiState.log.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = "Ultimo log", color = Color.White, style = MaterialTheme.typography.titleMedium)
      Text(text = uiState.log, color = SoftText, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(SheetBackground, RoundedCornerShape(20.dp))
      .padding(16.dp),
  ) {
    Text(text = label, color = SoftText, style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = value, color = Color.White, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun DiagnosticsSheet(
  uiState: DoorUiState,
) {
  val subtitle = statusSubtitle(uiState)
  val controlFields = uiState.peripheralFields.filter { it.label in setOf("Automazione apertura", "Relay Lock", "Relay attivo", "WiFi connesso") }
  val networkFields = uiState.peripheralFields.filter { it.label in setOf("IP address", "SSID", "BSSID", "MAC address", "WiFi Signal") }
  val systemFields = uiState.peripheralFields.filter { it.label in setOf("Boot time", "Uptime", "CPU temperature") }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(420.dp)
      .padding(horizontal = 20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(text = "Ingresso", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(text = subtitle, color = SoftText, style = MaterialTheme.typography.bodyLarge)
      }
      Text(text = peripheralValue(uiState.peripheralFields, "WiFi Signal") ?: "--", color = AccentYellow, style = MaterialTheme.typography.titleLarge)
    }
    Spacer(modifier = Modifier.height(16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (controlFields.isNotEmpty()) {
        item { SheetSectionTitle("Controllo") }
        items(controlFields) { field -> PeripheralFieldRow(field) }
      }
      if (networkFields.isNotEmpty()) {
        item { SheetSectionTitle("Rete") }
        items(networkFields) { field -> PeripheralFieldRow(field) }
      }
      if (systemFields.isNotEmpty()) {
        item { SheetSectionTitle("Diagnostica") }
        items(systemFields) { field -> PeripheralFieldRow(field) }
      }
      if (uiState.scanResults.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = "Periferiche BLE viste", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.scanResults) { item ->
          Text(text = item, color = SoftText, style = MaterialTheme.typography.bodySmall)
          HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }
      }
    }
  }
}

@Composable
private fun SheetSectionTitle(title: String) {
  Text(
    text = title,
    color = SoftText,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
  )
}

@Composable
private fun PeripheralFieldRow(field: PeripheralField) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
      .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = field.label, color = Color.White, modifier = Modifier.weight(1f))
    Spacer(modifier = Modifier.width(12.dp))
    Text(text = field.value, color = AccentYellow, textAlign = TextAlign.End)
  }
}

private fun peripheralValue(fields: List<PeripheralField>, label: String): String? {
  return fields.firstOrNull { it.label == label }?.value
}

private fun statusSubtitle(uiState: DoorUiState): String {
  if (uiState.isBusy) return "connessione in corso"

  val rssi = uiState.lastSeenRssi
  val connectable = uiState.bleConnectable
  if (rssi == null) return uiState.status

  val proximity = when {
    rssi >= -65 -> "molto vicina"
    rssi >= -78 -> "in zona"
    else -> "piu' distante"
  }

  return if (connectable == false) {
    "periferica $proximity ma non connettibile"
  } else {
    "periferica $proximity e connettibile"
  }
}

private fun copyLogToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText("Door App BLE Log", text))
  Toast.makeText(context, "Log copiato", Toast.LENGTH_SHORT).show()
}
