package com.zapret.droid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zapret.droid.config.ProfileConfig
import com.zapret.droid.service.ZapretVpnService
import com.zapret.droid.strategies.Service
import com.zapret.droid.strategies.Strategy
import com.zapret.droid.strategies.StrategyPresets
import java.io.IOException

class MainActivity : ComponentActivity() {
    private var refreshStatus: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZapretApp(onRefreshStatus = { refreshStatus = it }) }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus?.invoke()
    }

    override fun onDestroy() {
        refreshStatus = null
        super.onDestroy()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ZapretApp(onRefreshStatus: ((() -> Unit) -> Unit)) {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = remember {
        context.getSharedPreferences("zapret_prefs", Context.MODE_PRIVATE)
    }
    val isTv = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    var selectedId by remember {
        mutableStateOf(prefs.getString("strategy_id", "general") ?: "general")
    }
    val selectedStrategy = remember(selectedId) {
        StrategyPresets.ALL.firstOrNull { it.id == selectedId } ?: StrategyPresets.GENERAL
    }
    var isRunning by remember { mutableStateOf(ZapretVpnService.isRunning) }
    var telegramEnabled by remember {
        mutableStateOf(prefs.getBoolean("telegram_enabled", true))
    }
    var autostartEnabled by remember {
        mutableStateOf(prefs.getBoolean("autostart", false))
    }
    var notice by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileCheckMessage by remember { mutableStateOf("") }
    val logs = remember {
        mutableStateListOf<String>().apply {
            addAll(ZapretVpnService.logBuffer.takeLast(40))
        }
    }
    val startFocusRequester = remember { FocusRequester() }

    val startService = {
        val intent = Intent(context, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_START
            putExtra(ZapretVpnService.EXTRA_STRATEGY_ID, selectedStrategy.id)
            putExtra(ZapretVpnService.EXTRA_TELEGRAM_ENABLED, telegramEnabled)
        }
        ContextCompat.startForegroundService(context, intent)
        isRunning = true
    }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService()
        } else {
            notice = "Разрешение на VPN не выдано."
        }
    }
    val toggleVpn = {
        if (isRunning) {
            context.startService(
                Intent(context, ZapretVpnService::class.java).apply {
                    action = ZapretVpnService.ACTION_STOP
                }
            )
            isRunning = false
        } else {
            val permissionIntent = VpnService.prepare(context)
            if (permissionIntent == null) {
                startService()
            } else {
                vpnPermissionLauncher.launch(permissionIntent)
            }
        }
    }
    val saveConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Не удалось открыть файл для записи.")
                output.use {
                    it.write(ProfileConfig.generate(selectedStrategy).toByteArray(Charsets.UTF_8))
                }
                notice = "Профиль сохранён."
            } catch (error: IOException) {
                notice = "Ошибка сохранения профиля: ${error.message ?: "ошибка ввода-вывода"}"
            }
        }
    }
    val openConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть файл.")
                val contents = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val result = ProfileConfig.validate(contents)
                val imported = result.strategy
                if (result.isValid && imported != null) {
                    selectedId = imported.id
                    prefs.edit().putString("strategy_id", imported.id).apply()
                    notice = "Профиль проверен и выбран: ${imported.name}."
                } else {
                    notice = "Профиль отклонён: ${result.errors.joinToString("; ")}"
                }
            } catch (error: IOException) {
                notice = "Ошибка чтения профиля: ${error.message ?: "ошибка ввода-вывода"}"
            }
        }
    }
    val importConfig = {
        if (isRunning) {
            notice = "Останови VPN перед импортом другого профиля."
        } else {
            openConfigLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }

    DisposableEffect(activity) {
        onRefreshStatus { isRunning = ZapretVpnService.isRunning }
        ZapretVpnService.onLogUpdate = { line ->
            activity.runOnUiThread {
                if (logs.size == 40) logs.removeAt(0)
                logs.add(line)
            }
        }
        onDispose {
            ZapretVpnService.onLogUpdate = null
            onRefreshStatus {}
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) startFocusRequester.requestFocus()
    }

    ZapretTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("ZapretDroid")
                            Text(
                                text = if (isTv) "Android TV · управление пультом" else "Android · профили DPI",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val wideLayout = maxWidth >= 760.dp
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(max = 1180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(if (isTv) 28.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (notice.isNotBlank()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = notice,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (wideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(0.9f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                StatusCard(
                                    isRunning = isRunning,
                                    strategyName = selectedStrategy.name,
                                    isTv = isTv,
                                    startFocusRequester = startFocusRequester,
                                    onToggle = toggleVpn
                                )
                                SettingsCard(
                                    isTv = isTv,
                                    telegramEnabled = telegramEnabled,
                                    autostartEnabled = autostartEnabled,
                                    onTelegramChange = {
                                        telegramEnabled = it
                                        prefs.edit().putBoolean("telegram_enabled", it).apply()
                                    },
                                    onAutostartChange = {
                                        autostartEnabled = it
                                        prefs.edit().putBoolean("autostart", it).apply()
                                    }
                                )
                                ProfileActions(
                                    isTv = isTv,
                                    onPreview = {
                                        profileCheckMessage = ""
                                        showProfileDialog = true
                                    },
                                    onImport = importConfig,
                                    onExport = {
                                        saveConfigLauncher.launch("${selectedStrategy.id}.json")
                                    }
                                )
                                LogCard(logs = logs, isTv = isTv)
                            }
                            Column(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StrategyList(
                                    selectedStrategy = selectedStrategy,
                                    enabled = !isRunning,
                                    isTv = isTv,
                                    onSelect = { strategy ->
                                        selectedId = strategy.id
                                        prefs.edit().putString("strategy_id", strategy.id).apply()
                                    }
                                )
                            }
                        }
                    } else {
                        StatusCard(
                            isRunning = isRunning,
                            strategyName = selectedStrategy.name,
                            isTv = isTv,
                            startFocusRequester = startFocusRequester,
                            onToggle = toggleVpn
                        )
                        SettingsCard(
                            isTv = isTv,
                            telegramEnabled = telegramEnabled,
                            autostartEnabled = autostartEnabled,
                            onTelegramChange = {
                                telegramEnabled = it
                                prefs.edit().putBoolean("telegram_enabled", it).apply()
                            },
                            onAutostartChange = {
                                autostartEnabled = it
                                prefs.edit().putBoolean("autostart", it).apply()
                            }
                        )
                        ProfileActions(
                            isTv = isTv,
                            onPreview = {
                                profileCheckMessage = ""
                                showProfileDialog = true
                            },
                            onImport = importConfig,
                            onExport = {
                                saveConfigLauncher.launch("${selectedStrategy.id}.json")
                            }
                        )
                        StrategyList(
                            selectedStrategy = selectedStrategy,
                            enabled = !isRunning,
                            isTv = isTv,
                            onSelect = { strategy ->
                                selectedId = strategy.id
                                prefs.edit().putString("strategy_id", strategy.id).apply()
                            }
                        )
                        LogCard(logs = logs, isTv = isTv)
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        val generatedConfig = ProfileConfig.generate(selectedStrategy)
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Профиль ${selectedStrategy.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Это профиль для Android-приложения, а не исполняемый .bat для Windows.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = generatedConfig,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    if (profileCheckMessage.isNotBlank()) {
                        Spacer(Modifier.size(12.dp))
                        Text(profileCheckMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val result = ProfileConfig.validate(generatedConfig)
                        profileCheckMessage = if (result.isValid) {
                            "Проверка пройдена: профиль соответствует встроенной стратегии."
                        } else {
                            "Проверка не пройдена: ${result.errors.joinToString("; ")}"
                        }
                    },
                    modifier = Modifier.tvFocusRing(isTv)
                ) {
                    Text("Проверить")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { saveConfigLauncher.launch("${selectedStrategy.id}.json") },
                        modifier = Modifier.tvFocusRing(isTv)
                    ) {
                        Text("Сохранить JSON")
                    }
                    TextButton(
                        onClick = { showProfileDialog = false },
                        modifier = Modifier.tvFocusRing(isTv)
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    isRunning: Boolean,
    strategyName: String,
    isTv: Boolean,
    startFocusRequester: FocusRequester,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(if (isTv) 24.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isRunning) "VPN активен" else "VPN остановлен",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Профиль: $strategyName",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTv) 64.dp else 56.dp)
                    .focusRequester(startFocusRequester)
                    .tvFocusRing(isTv)
            ) {
                Text(
                    text = if (isRunning) "Остановить" else "Запустить",
                    fontSize = if (isTv) 20.sp else 16.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    isTv: Boolean,
    telegramEnabled: Boolean,
    autostartEnabled: Boolean,
    onTelegramChange: (Boolean) -> Unit,
    onAutostartChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
            SettingRow(
                title = "Telegram",
                subtitle = "Включать локальный WebSocket-прокси",
                checked = telegramEnabled,
                isTv = isTv,
                onCheckedChange = onTelegramChange
            )
            SettingRow(
                title = "Автозапуск",
                subtitle = "Запускать VPN после включения устройства",
                checked = autostartEnabled,
                isTv = isTv,
                onCheckedChange = onAutostartChange
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isTv: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.tvFocusRing(isTv)
        )
    }
}

@Composable
private fun ProfileActions(
    isTv: Boolean,
    onPreview: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Профили конфигурации", style = MaterialTheme.typography.titleLarge)
            Text(
                "Экспортируй профиль, импортируй его на другом устройстве или проверь поля перед запуском.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .tvFocusRing(isTv)
            ) {
                Text("Создать и проверить")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).tvFocusRing(isTv)
                ) {
                    Text("Импорт")
                }
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).tvFocusRing(isTv)
                ) {
                    Text("Экспорт")
                }
            }
        }
    }
}

@Composable
private fun StrategyList(
    selectedStrategy: Strategy,
    enabled: Boolean,
    isTv: Boolean,
    onSelect: (Strategy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Стратегии", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (enabled) {
                "Выбери профиль и проверь его в своей сети."
            } else {
                "Останови VPN, чтобы сменить профиль."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        StrategyPresets.ALL.forEach { strategy ->
            StrategyOption(
                strategy = strategy,
                selected = strategy.id == selectedStrategy.id,
                enabled = enabled,
                isTv = isTv,
                onClick = { onSelect(strategy) }
            )
        }
    }
}

@Composable
private fun StrategyOption(
    strategy: Strategy,
    selected: Boolean,
    enabled: Boolean,
    isTv: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(isTv),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isTv) 18.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strategy.name,
                    style = if (isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = strategy.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strategy.services.joinToString(" · ") { it.displayName() },
                    style = MaterialTheme.typography.labelMedium
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun LogCard(logs: List<String>, isTv: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Журнал", style = MaterialTheme.typography.titleLarge)
            if (logs.isEmpty()) {
                Text("Записи появятся после запуска.", style = MaterialTheme.typography.bodyMedium)
            } else {
                logs.takeLast(8).asReversed().forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = if (isTv) 14.sp else 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ZapretTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkMode = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (darkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkMode) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun Modifier.tvFocusRing(enabled: Boolean): Modifier {
    if (!enabled) return this
    val focused = remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return onFocusChanged { focused.value = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused.value) {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
}

private fun Service.displayName(): String = when (this) {
    Service.YOUTUBE -> "YouTube"
    Service.DISCORD -> "Discord"
    Service.TELEGRAM -> "Telegram"
    Service.GOOGLE -> "Google"
    Service.GAMING -> "Игры"
}
