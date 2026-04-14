package com.winehub

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winehub.core.DeviceProfile
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WineHubApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, Material3ExpressiveTokens::class)
@Composable
fun WineHubApp() {
    val context = LocalContext.current
    val deviceInfo = rememberDeviceInfo(context)
    var selectedTab by remember { mutableStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE94560),
            secondary = Color(0xFF0F3460),
            surface = Color(0xFF16213E),
            background = Color(0xFF1A1A2E),
            onPrimary = Color.White,
            onSurface = Color.White,
            onBackground = Color(0xFFE8E8E8)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Wine Native Hub", fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF16213E)
                    ),
                    actions = {
                        IconButton(onClick = {
                            Toast.makeText(context, "WineHub v1.0", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Info, "About", tint = Color.White)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF16213E)) {
                    val items = listOf(
                        Triple(Icons.Default.Dashboard, "Status", 0),
                        Triple(Icons.Default.PlayArrow, "Launch", 1),
                        Triple(Icons.Default.Terminal, "Terminal", 2),
                        Triple(Icons.Default.Settings, "Settings", 3)
                    )
                    items.forEach { (icon, label, idx) ->
                        NavigationItem(
                            icon = icon,
                            label = label,
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx }
                        )
                    }
                }
            },
            containerColor = Color(0xFF1A1A2E)
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> StatusScreen(deviceInfo)
                    1 -> LauncherScreen(deviceInfo)
                    2 -> TerminalScreen(deviceInfo)
                    3 -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun rememberDeviceInfo(context: android.content.Context): DeviceProfile {
    return remember {
        DeviceProfile(
            cpuAbi = Build.SUPPORTED_ABIS.joinToString(", "),
            totalMemoryMb = getTotalRamMb(),
            gpuVendor = Build.HARDWARE,
            vulkanSupported = Build.VERSION.SDK_INT >= 26,
            vulkanApi = getVulkanApi(),
            hasDescriptorIndexing = Build.VERSION.SDK_INT >= 29,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            buildModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
}

private fun getTotalRamMb(): Long {
    val memInfo = android.app.ActivityManager.MemoryInfo()
    val am = android.app.ActivityManager()
    try {
        am.getMemoryInfo(memInfo)
    } catch (_: Exception) {}
    return memInfo.totalMem / (1024 * 1024)
}

private fun getVulkanApi(): String {
    return when {
        Build.VERSION.SDK_INT >= 34 -> "1.3.0"
        Build.VERSION.SDK_INT >= 30 -> "1.2.0"
        Build.VERSION.SDK_INT >= 26 -> "1.1.0"
        else -> "1.0.0"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusScreen(deviceInfo: DeviceProfile) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Device Status", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE94560))
                    Divider(color = Color(0xFF0F3460))
                    InfoRow("Device", deviceInfo.buildModel)
                    InfoRow("Android", deviceInfo.androidVersion)
                    InfoRow("CPU ABI", deviceInfo.cpuAbi)
                    InfoRow("CPU Cores", "${deviceInfo.cpuCores}")
                    InfoRow("RAM", "${deviceInfo.totalMemoryMb} MB")
                    InfoRow("GPU", deviceInfo.gpuVendor)
                    InfoRow("Vulkan", deviceInfo.vulkanApi)
                    InfoRow("Vulkan Supported", if (deviceInfo.vulkanSupported) "Yes" else "No")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Checks", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val result = executeCommand("uname -a")
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                        ) { Text("uname -a") }
                        Button(
                            onClick = {
                                val result = executeCommand("cat /proc/cpuinfo | grep Hardware | head -1")
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                        ) { Text("CPU Info") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val result = executeCommand("df -h /data")
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                        ) { Text("Disk Space") }
                        Button(
                            onClick = {
                                val result = executeCommand("free -m | head -2")
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                        ) { Text("Memory") }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Compatibility Score", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))
                    val score = calculateCompatibility(deviceInfo)
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = if (score >= 70) Color(0xFF00D2FF) else if (score >= 40) Color(0xFFFFB800) else Color(0xFFE94560),
                        trackColor = Color(0xFF0F3460)
                    )
                    Text("${score}/100 - ${if (score >= 70) "Good" else if (score >= 40) "Medium" else "Low"}",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

private fun calculateCompatibility(info: DeviceProfile): Int {
    var score = 0
    if (info.vulkanSupported) score += 25
    if (info.totalMemoryMb >= 4096) score += 20
    else if (info.totalMemoryMb >= 2048) score += 10
    if (info.cpuCores >= 6) score += 20
    else if (info.cpuCores >= 4) score += 15
    if (info.hasDescriptorIndexing) score += 15
    if ("aarch64" in info.cpuAbi.lowercase() || "arm64" in info.cpuAbi.lowercase()) score += 20
    return score.coerceAtMost(100)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherScreen(deviceInfo: DeviceProfile) {
    val context = LocalContext.current
    var selectedFiles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedPrefix by remember { mutableStateOf("/data/data/com.winenativehub/wineprefix") }
    var esyncEnabled by remember { mutableStateOf(true) }
    var box64Enabled by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var outputLog by remember { mutableStateOf<List<String>>(emptyList()) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val files = uris.map { uri ->
            val name = uri.path?.substringAfterLast('/') ?: "unknown"
            name to uri.toString()
        }
        selectedFiles = files
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Launch Settings", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ESYNC", color = Color.White)
                    Switch(checked = esyncEnabled, onCheckedChange = { esyncEnabled = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Box64 (ARM64 only)", color = Color.White)
                    Switch(checked = box64Enabled, onCheckedChange = { box64Enabled = it })
                }

                OutlinedTextField(
                    value = selectedPrefix,
                    onValueChange = { selectedPrefix = it },
                    label = { Text("Wine Prefix", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color(0xFF0F3460),
                        focusedLabelColor = Color(0xFFE94560),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Button(
                    onClick = { filePicker.launch(arrayOf("application/x-ms-dos-executable", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select .exe files")
                }
            }
        }

        if (selectedFiles.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Selected Files (${selectedFiles.size})", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE94560))
                    selectedFiles.forEach { (name, _) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, null, tint = Color.Gray)
                            Text(name, color = Color.White)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (selectedFiles.isEmpty()) {
                    Toast.makeText(context, "Select an .exe file first!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isRunning = true
                outputLog = listOf("Starting Wine...")
                selectedFiles.forEach { (name, uri) ->
                    val wineCmd = buildWineCommand(deviceInfo, esyncEnabled, box64Enabled, selectedPrefix, name)
                    outputLog = outputLog + "> ${wineCmd.joinToString(" ")}"
                    val result = executeCommand(wineCmd.joinToString(" "))
                    outputLog = outputLog + result
                }
                outputLog = outputLog + "Done."
                isRunning = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && selectedFiles.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Gray else Color(0xFFE94560))
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isRunning) "Running..." else "Launch in Wine")
        }

        if (outputLog.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Output Log", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00D2FF))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        items(outputLog) { line ->
                            Text(line, color = Color(0xFF00FF41), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalScreen(deviceInfo: DeviceProfile) {
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<List<String>>(listOf("Terminal ready. Enter command.")) }
    val scrollState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(output) { line ->
                Text(line, color = if (line.startsWith("$")) Color(0xFF00D2FF) else Color(0xFF00FF41),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("command", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE94560),
                    unfocusedBorderColor = Color(0xFF0F3460),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Button(
                onClick = {
                    if (command.isBlank()) return@Button
                    output = output + "$ $command"
                    val result = executeCommand(command)
                    output = output + result
                    command = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
            ) { Text("Run") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    var wineVersion by remember { mutableStateOf("8.0") }
    var renderer by remember { mutableStateOf("vulkan") }
    var fsrEnabled by remember { mutableStateOf(false) }
    var dxvkVersion by remember { mutableStateOf("2.0") }

    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Wine Settings", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE94560))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Wine Version", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("7.0", "8.0", "9.0", "10.0").forEach { ver ->
                        FilterChip(
                            selected = wineVersion == ver,
                            onClick = { wineVersion = ver },
                            label = { Text(ver) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE94560),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Renderer", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("vulkan", "zink", "opengl", "llvmpipe").forEach { r ->
                        FilterChip(
                            selected = renderer == r,
                            onClick = { renderer = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE94560),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Advanced", color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AMD FSR", color = Color.White)
                    Switch(checked = fsrEnabled, onCheckedChange = { fsrEnabled = it })
                }

                Text("DXVK Version", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1.10.3", "2.0", "2.3", "2.5").forEach { v ->
                        FilterChip(
                            selected = dxvkVersion == v,
                            onClick = { dxvkVersion = v },
                            label = { Text(v) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE94560),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val msg = "Settings saved:\nWine=$wineVersion\nRenderer=$renderer\nFSR=$fsrEnabled\nDXVK=$dxvkVersion"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
        ) {
            Text("Save Settings")
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NavigationItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFFE94560),
            selectedTextColor = Color(0xFFE94560),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color(0xFF0F3460)
        )
    )
}

private fun executeCommand(command: String): String {
    return try {
        val process = Runtime.getRuntime().exec(command)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        while (errorReader.readLine().also { line = it } != null) {
            output.append("[ERR] ").append(line).append("\n")
        }
        process.waitFor()
        output.toString().ifEmpty { "[exit code: ${process.exitValue()}]" }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

private fun buildWineCommand(
    deviceInfo: DeviceProfile,
    esyncEnabled: Boolean,
    box64Enabled: Boolean,
    prefix: String,
    exeName: String
): List<String> {
    val cmd = mutableListOf<String>()
    if (box64Enabled) cmd.add("box64")
    cmd.add("wine")

    val env = mutableMapOf<String, String>()
    env["WINEPREFIX"] = prefix
    if (esyncEnabled) env["WINEESYNC"] = "1"
    env["DXVK_HUD"] = "fps"

    cmd.addAll(listOf("env", *env.flatMap { (k, v) -> listOf("$k=$v") }.toTypedArray()))
    cmd.add(exeName)

    return cmd
}
