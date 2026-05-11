package com.example.doorapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val AppBackground = Color(0xFF050505)
private val SheetBackground = Color(0xFF1A1A1A)
private val AccentYellow = Color(0xFFF3C544)
private val SoftText = Color(0xFFB7B7B7)
private val RingColor = Color(0xFFF5F2EB)
private val RingTrack = Color(0x33F5F2EB)
private val CardBackground = Color(0xFF171717)

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.navigationBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

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
  val activity = context as FragmentActivity
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

  fun withAppSecurity(action: () -> Unit) {
    if (!uiState.biometricProtectionEnabled) {
      action()
    } else {
      authenticateWithDevice(activity, action)
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
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 16.dp),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          HeaderRow(onOpenMenu = { scope.launch { drawerState.open() } })
          Spacer(modifier = Modifier.height(32.dp))
          when (uiState.currentScreen) {
            AppScreen.HOME -> HomeScreen(
              uiState = uiState,
              onOpenDoor = { withPermissions { withAppSecurity { viewModel.openDoor() } } },
            )

            AppScreen.ACTIONS -> ActionsScreen(
              uiState = uiState,
              onRefresh = { withPermissions { viewModel.refreshPeripheralData() } },
              onSetAutoOpen = { enabled -> withPermissions { withAppSecurity { viewModel.setAutoOpen(enabled) } } },
              onRestart = { withPermissions { withAppSecurity { viewModel.restartDevice() } } },
            )

            AppScreen.SETTINGS -> SettingsScreen(
              uiState = uiState,
              onSetBiometricProtection = { enabled -> viewModel.setBiometricProtection(enabled) },
              onSaveBleSharedSecret = { secret -> viewModel.setBleSharedSecret(secret) },
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
        text = "ESPnuki",
        color = Color.White,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
      )
      Text(text = stringResource(R.string.subtitle_ble_local_access), color = SoftText, style = MaterialTheme.typography.labelSmall)
    }
    Spacer(modifier = Modifier.size(10.dp))
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
      text = "ESPnuki",
      color = Color.White,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
    Spacer(modifier = Modifier.height(24.dp))
    DrawerItem(stringResource(R.string.nav_home), currentScreen == AppScreen.HOME) { onNavigate(AppScreen.HOME) }
    DrawerItem(stringResource(R.string.nav_actions), currentScreen == AppScreen.ACTIONS) { onNavigate(AppScreen.ACTIONS) }
    DrawerItem(stringResource(R.string.nav_settings), currentScreen == AppScreen.SETTINGS) { onNavigate(AppScreen.SETTINGS) }
    DrawerItem(stringResource(R.string.nav_info), currentScreen == AppScreen.INFO) { onNavigate(AppScreen.INFO) }
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = stringResource(R.string.quick_actions),
      color = SoftText,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
    DrawerItem(stringResource(R.string.refresh_ble_data), false, onRefresh)
    DrawerItem(stringResource(R.string.ble_scan), false, onScan)
    if (hasLog) {
      DrawerItem(stringResource(R.string.copy_log), false, onCopyLog)
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
  val centerLabel = when {
    relayActive -> stringResource(R.string.status_unlocked)
    uiState.status == "connessione in corso" -> stringResource(R.string.status_unlocked)
    uiState.status == "sblocco in corso" -> stringResource(R.string.status_unlocked)
    else -> stringResource(R.string.status_locked)
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier
        .size(272.dp)
        .clickable(enabled = !uiState.isBusy, onClick = onOpenDoor),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
          color = RingTrack,
          startAngle = 320f,
          sweepAngle = 260f,
          useCenter = false,
          style = stroke,
        )
        drawArc(
          color = RingColor,
          startAngle = 320f,
          sweepAngle = 260f,
          useCenter = false,
          style = stroke,
        )
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    Text(text = stringResource(R.string.nav_actions), color = Color.White, style = MaterialTheme.typography.headlineMedium)
    Text(text = stringResource(R.string.actions_subtitle), color = SoftText)
    ActionCard(title = stringResource(R.string.auto_open)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = if (autoOpenEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled), color = AccentYellow, style = MaterialTheme.typography.titleMedium)
          Text(text = stringResource(R.string.auto_open_description), color = SoftText)
        }
        Switch(
          checked = autoOpenEnabled,
          onCheckedChange = { onSetAutoOpen(it) },
          enabled = !uiState.isBusy,
          colors = nukiSwitchColors(),
        )
      }
    }
    ActionCard(title = stringResource(R.string.peripheral)) {
      OutlinedButton(onClick = onRefresh, enabled = !uiState.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.refresh_ble_data), color = AccentYellow)
      }
      Spacer(modifier = Modifier.height(12.dp))
      OutlinedButton(onClick = onRestart, enabled = !uiState.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.restart_esp32), color = AccentYellow)
      }
    }
  }
}

@Composable
private fun SettingsScreen(
  uiState: DoorUiState,
  onSetBiometricProtection: (Boolean) -> Unit,
  onSaveBleSharedSecret: (String) -> Unit,
) {
  var secretInput by rememberSaveable(uiState.bleSharedSecret) { mutableStateOf(uiState.bleSharedSecret) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(text = stringResource(R.string.nav_settings), color = Color.White, style = MaterialTheme.typography.headlineMedium)
    Text(text = stringResource(R.string.settings_subtitle), color = SoftText)
    ActionCard(title = stringResource(R.string.ble_access)) {
      Text(
        text = if (uiState.bleSharedSecretConfigured) stringResource(R.string.ble_shared_key) else stringResource(R.string.ble_secret_hint),
        color = if (uiState.bleSharedSecretConfigured) AccentYellow else SoftText,
      )
      Spacer(modifier = Modifier.height(12.dp))
      OutlinedTextField(
        value = secretInput,
        onValueChange = { secretInput = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.ble_shared_secret), color = SoftText) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = Color.White,
          unfocusedTextColor = Color.White,
          focusedBorderColor = AccentYellow,
          unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
          focusedLabelColor = AccentYellow,
          unfocusedLabelColor = SoftText,
          cursorColor = AccentYellow,
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
        ),
      )
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          onClick = { onSaveBleSharedSecret(secretInput) },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(
            containerColor = AccentYellow,
            contentColor = AppBackground,
          ),
        ) {
          Text(if (uiState.bleSharedSecretConfigured) stringResource(R.string.update) else stringResource(R.string.save))
        }
        OutlinedButton(
          onClick = {
            secretInput = ""
            onSaveBleSharedSecret("")
          },
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.remove), color = AccentYellow)
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.ble_secret_note),
        color = SoftText,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    ActionCard(title = stringResource(R.string.security)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = stringResource(R.string.fingerprint_or_pin), color = AccentYellow, style = MaterialTheme.typography.titleMedium)
          Text(
            text = if (uiState.biometricAvailable) stringResource(R.string.biometric_enabled_description) else stringResource(R.string.biometric_unavailable_description),
            color = SoftText,
          )
        }
        Switch(
          checked = uiState.biometricProtectionEnabled,
          onCheckedChange = onSetBiometricProtection,
          enabled = uiState.biometricAvailable,
          colors = nukiSwitchColors(),
        )
      }
    }
  }
}

@Composable
private fun ActionCard(title: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(CardBackground, RoundedCornerShape(24.dp))
      .border(1.dp, AccentYellow.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
      .padding(20.dp),
  ) {
    Text(text = title, color = AccentYellow, style = MaterialTheme.typography.titleLarge)
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
    Text(text = stringResource(R.string.nav_info), color = Color.White, style = MaterialTheme.typography.headlineMedium)
    EspNukiLogoCard()
    InfoRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
    InfoRow("Build", BuildConfig.BUILD_TIMESTAMP)
    InfoRow("Device BLE", BleDoorConfig.deviceName)
    InfoRow(stringResource(R.string.github_repository), BuildConfig.REPOSITORY_URL)
    if (uiState.log.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = stringResource(R.string.last_log), color = Color.White, style = MaterialTheme.typography.titleMedium)
      Text(text = uiState.log, color = SoftText, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun EspNukiLogoCard() {
  Card(
    colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
    shape = RoundedCornerShape(28.dp),
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 420.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      EspNukiLogoMark(
        modifier = Modifier
          .fillMaxWidth(0.72f)
          .aspectRatio(1f),
      )
      Text(
        text = buildAnnotatedString {
          withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color.White)) { append("ESP") }
          withStyle(
            style = androidx.compose.ui.text.SpanStyle(
              color = AccentYellow,
              baselineShift = BaselineShift(0.02f),
            ),
          ) { append("nuki") }
        },
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
      )
    }
  }
}

@Composable
private fun EspNukiLogoMark(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val ringColor = Color(0xFFF7F4EF)
    val centerColor = Color(0xFFFFD400)
    val ringStroke = size.minDimension * 0.07f
    val ringDiameter = size.minDimension * 0.5f
    val ringTop = size.height * 0.14f
    val ringLeft = (size.width - ringDiameter) / 2f
    val ringCenterX = size.width / 2f
    val ringBottom = ringTop + ringDiameter
    val branchTop = ringBottom - ringStroke * 0.15f
    val branchBottom = size.height * 0.86f
    val leftX = ringCenterX - ringDiameter * 0.21f
    val rightX = ringCenterX + ringDiameter * 0.21f
    val branchOffset = ringDiameter * 0.18f
    val nodeRadius = size.minDimension * 0.045f

    drawCircle(
      color = ringColor,
      radius = ringDiameter / 2f,
      center = Offset(ringCenterX, ringTop + ringDiameter / 2f),
      style = Stroke(width = ringStroke),
    )
    drawCircle(
      color = centerColor,
      radius = ringDiameter * 0.085f,
      center = Offset(ringCenterX, ringTop + ringDiameter / 2f),
    )

    fun drawBranch(startX: Float, endX: Float) {
      drawLine(ringColor, Offset(startX, branchTop), Offset(startX, branchTop + size.height * 0.11f), strokeWidth = ringStroke, cap = StrokeCap.Round)
      drawLine(ringColor, Offset(startX, branchTop + size.height * 0.11f), Offset(endX, branchTop + size.height * 0.19f), strokeWidth = ringStroke, cap = StrokeCap.Round)
      drawLine(ringColor, Offset(endX, branchTop + size.height * 0.19f), Offset(endX, branchBottom - nodeRadius * 1.45f), strokeWidth = ringStroke, cap = StrokeCap.Round)
    }

    drawBranch(leftX, leftX - branchOffset)
    drawLine(ringColor, Offset(ringCenterX, branchTop), Offset(ringCenterX, branchBottom - nodeRadius * 1.45f), strokeWidth = ringStroke, cap = StrokeCap.Round)
    drawBranch(rightX, rightX + branchOffset)

    val nodeCenters = listOf(
      Offset(leftX - branchOffset, branchBottom),
      Offset(ringCenterX, branchBottom),
      Offset(rightX + branchOffset, branchBottom),
    )
    nodeCenters.forEach { center ->
      drawCircle(color = ringColor, radius = nodeRadius, center = center)
      drawCircle(color = Color(0xFF080808), radius = nodeRadius * 0.34f, center = center)
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
  val networkFields = uiState.peripheralFields.filter { it.label in setOf("IP address", "SSID", "BSSID", "MAC address", "WiFi Signal") }
  val systemFields = uiState.peripheralFields.filter { it.label in setOf("Boot time", "Uptime", "CPU temperature") }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(420.dp)
      .navigationBarsPadding()
      .padding(horizontal = 20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(text = stringResource(R.string.entrance), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(text = subtitle, color = SoftText, style = MaterialTheme.typography.bodyLarge)
      }
      Text(text = peripheralValue(uiState.peripheralFields, "WiFi Signal") ?: "--", color = AccentYellow, style = MaterialTheme.typography.titleLarge)
    }
    Spacer(modifier = Modifier.height(16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (networkFields.isNotEmpty()) {
        item { SheetSectionTitle(stringResource(R.string.network)) }
        items(networkFields) { field -> PeripheralFieldRow(field) }
      }
      if (systemFields.isNotEmpty()) {
        item { SheetSectionTitle(stringResource(R.string.diagnostics)) }
        items(systemFields) { field -> PeripheralFieldRow(field) }
      }
      if (uiState.scanResults.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = stringResource(R.string.seen_ble_peripherals), color = Color.White, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun statusSubtitle(uiState: DoorUiState): String {
  if (uiState.status == "connessione in corso") return stringResource(R.string.status_connecting)
  if (uiState.status == "sblocco in corso") return stringResource(R.string.status_unlocking)
  if (uiState.status == "blocco in corso") return stringResource(R.string.status_locking)

  val rssi = uiState.lastSeenRssi
  if (rssi == null) return uiState.status

  return when {
    rssi >= -60 -> stringResource(R.string.status_peripheral_in_range)
    rssi >= -72 -> stringResource(R.string.status_peripheral_nearby)
    rssi >= -84 -> stringResource(R.string.status_peripheral_weak)
    else -> stringResource(R.string.status_peripheral_out_of_range)
  }
}

@Composable
private fun nukiSwitchColors() = SwitchDefaults.colors(
  checkedThumbColor = AppBackground,
  checkedTrackColor = AccentYellow,
  checkedBorderColor = AccentYellow,
  uncheckedThumbColor = Color.White,
  uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
  uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
)

private fun copyLogToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText("ESPnuki BLE Log", text))
  Toast.makeText(context, context.getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
}

private fun authenticateWithDevice(context: FragmentActivity, onSuccess: () -> Unit) {
  val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
  if (BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
    onSuccess()
    return
  }

  val executor = ContextCompat.getMainExecutor(context)
  val prompt = BiometricPrompt(
    context,
    executor,
    object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        onSuccess()
      }
    },
  )
  val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle(context.getString(R.string.confirm_action))
    .setSubtitle(context.getString(R.string.auth_subtitle))
    .setAllowedAuthenticators(authenticators)
    .build()
  prompt.authenticate(promptInfo)
}
