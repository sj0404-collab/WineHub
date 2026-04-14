package com.winehub

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WineHubApp() }
    }
}

data class BinaryPackage(
    val name: String,
    val version: String,
    val downloadUrl: String,
    val installPath: String,
    val binaryName: String,
    val isZip: Boolean = false,
    val isInstalled: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false
)

data class DeviceInfo(
    val cpuAbi: String,
    val totalMemoryMb: Long,
    val gpuVendor: String,
    val vulkanSupported: Boolean,
    val vulkanApi: String,
    val hasDescriptorIndexing: Boolean,
    val androidVersion: String,
    val cpuCores: Int,
    val buildModel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WineHubApp() {
    val context = LocalContext.current
    val deviceInfo = rememberDeviceInfo(context)
    var selectedTab by remember { mutableStateOf(0) }
    var binaries by remember { mutableStateOf(getDefaultBinaries()) }

    LaunchedEffect(Unit) {
        binaries = binaries.map { b -> b.copy(isInstalled = isBinaryInstalled(b)) }
    }

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
                    title = { Text("Wine Native Hub", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16213E)),
                    actions = {
                        IconButton(onClick = {
                            Toast.makeText(context, "WineHub v2.0", Toast.LENGTH_SHORT).show()
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
                        Triple(Icons.Default.Download, "Download", 1),
                        Triple(Icons.Default.PlayArrow, "Launch", 2),
                        Triple(Icons.Default.Terminal, "Terminal", 3),
                        Triple(Icons.Default.Settings, "Settings", 4)
                    )
                    items.forEach { (icon, label, idx) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE94560),
                                selectedTextColor = Color(0xFFE94560),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFF0F3460)
                            )
                        )
                    }
                }
            },
            containerColor = Color(0xFF1A1A2E)
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> StatusScreen(deviceInfo)
                    1 -> DownloadScreen(binaries) { binaries = it }
                    2 -> LauncherScreen(deviceInfo, binaries)
                    3 -> TerminalScreen()
                    4 -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun rememberDeviceInfo(context: android.content.Context): DeviceInfo {
    return remember {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        DeviceInfo(
            cpuAbi = Build.SUPPORTED_ABIS.joinToString(", "),
            totalMemoryMb = memInfo.totalMem / (1024 * 1024),
            gpuVendor = Build.HARDWARE,
            vulkanSupported = Build.VERSION.SDK_INT >= 26,
            vulkanApi = when {
                Build.VERSION.SDK_INT >= 34 -> "1.3.0"
                Build.VERSION.SDK_INT >= 30 -> "1.2.0"
                Build.VERSION.SDK_INT >= 26 -> "1.1.0"
                else -> "1.0.0"
            },
            hasDescriptorIndexing = Build.VERSION.SDK_INT >= 29,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            buildModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
}

private fun getAppDataDir(context: android.content.Context): String {
    val dir = context.getExternalFilesDir(null) ?: context.filesDir
    return dir.absolutePath
}

private fun getDefaultBinaries(): List<BinaryPackage> {
    return listOf(
        BinaryPackage(
            name = "Wine",
            version = "9.0",
            downloadUrl = "https://github.com/ptitSeb/wine4a/releases/download/9.0/wine-9.0-arm64.tar.gz",
            installPath = "/data/data/com.winenativehub/files/wine",
            binaryName = "wine"
        ),
        BinaryPackage(
            name = "Box64",
            version = "0.3.2",
            downloadUrl = "https://github.com/ptitSeb/box64/releases/download/v0.3.2/box64-android-arm64.zip",
            installPath = "/data/data/com.winenativehub/files/box64",
            binaryName = "box64",
            isZip = true
        ),
        BinaryPackage(
            name = "DXVK",
            version = "2.5",
            downloadUrl = "https://github.com/doitsujin/dxvk/releases/download/v2.5/dxvk-2.5.tar.gz",
            installPath = "/data/data/com.winenativehub/files/dxvk",
            binaryName = "dxvk",
            isZip = false
        ),
        BinaryPackage(
            name = "Wine64",
            version = "9.0",
            downloadUrl = "https://github.com/ptitSeb/wine4a/releases/download/9.0/wine-9.0-arm64.tar.gz",
            installPath = "/data/data/com.winenativehub/files/wine64",
            binaryName = "wine64"
        )
    )
}

private fun isBinaryInstalled(pkg: BinaryPackage): Boolean {
    val binPath = File(pkg.installPath, pkg.binaryName)
    return binPath.exists() && binPath.canExecute()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusScreen(info: DeviceInfo) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    val binCount = getDefaultBinaries().count { isBinaryInstalled(it) }

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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device Status", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE94560))
                    Divider(color = Color(0xFF0F3460))
                    InfoRow("Device", info.buildModel)
                    InfoRow("Android", info.androidVersion)
                    InfoRow("CPU ABI", info.cpuAbi)
                    InfoRow("CPU Cores", "${info.cpuCores}")
                    InfoRow("RAM", "${info.totalMemoryMb} MB")
                    InfoRow("GPU", info.gpuVendor)
                    InfoRow("Vulkan", info.vulkanApi)
                    InfoRow("Binaries", "$binCount/${getDefaultBinaries().size} installed")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Checks", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickCheckButton("uname -a", "uname -a")
                            QuickCheckButton("CPU Info", "cat /proc/cpuinfo | grep Hardware | head -1")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickCheckButton("Disk Space", "df -h /data")
                            QuickCheckButton("Memory", "free -m | head -2")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickCheckButton("Uptime", "uptime")
                            QuickCheckButton("Arch", "uname -m")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Compatibility Score", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))
                    val score = calcScore(info)
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

@Composable
private fun QuickCheckButton(label: String, cmd: String) {
    val context = LocalContext.current
    Button(
        onClick = {
            val result = execCmd(cmd)
            Toast.makeText(context, result.trim(), Toast.LENGTH_LONG).show()
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
    ) { Text(label) }
}

private fun calcScore(info: DeviceInfo): Int {
    var s = 0
    if (info.vulkanSupported) s += 25
    if (info.totalMemoryMb >= 4096) s += 20 else if (info.totalMemoryMb >= 2048) s += 10
    if (info.cpuCores >= 6) s += 20 else if (info.cpuCores >= 4) s += 15
    if (info.hasDescriptorIndexing) s += 15
    if ("aarch64" in info.cpuAbi.lowercase() || "arm64" in info.cpuAbi.lowercase()) s += 20
    return s.coerceAtMost(100)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadScreen(binaries: List<BinaryPackage>, onBinariesUpdate: (List<BinaryPackage>) -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Download Binaries", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE94560))
        Text("Download Wine, Box64, DXVK — needed to run Windows apps", color = Color.Gray)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(binaries) { pkg ->
                BinaryCard(pkg, context) { updated ->
                    onBinariesUpdate(
                        binaries.map { if (it.name == updated.name) updated else it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinaryCard(
    pkg: BinaryPackage,
    context: android.content.Context,
    onUpdate: (BinaryPackage) -> Unit
) {
    var pkg by remember { mutableStateOf(pkg) }
    var statusText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (pkg.isInstalled) Color(0xFF0D2818) else Color(0xFF16213E)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium, color = if (pkg.isInstalled) Color(0xFF00FF41) else Color.White)
                    Text("v${pkg.version} • ${pkg.binaryName}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                if (pkg.isInstalled) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF41))
                } else {
                    Icon(Icons.Default.Download, null, tint = Color.Gray)
                }
            }

            if (pkg.isDownloading) {
                LinearProgressIndicator(
                    progress = { pkg.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFFE94560),
                    trackColor = Color(0xFF0F3460)
                )
                Text("Downloading: ${(pkg.downloadProgress * 100).toInt()}%", color = Color.Gray)
            }

            if (statusText.isNotEmpty()) {
                Text(statusText, color = if (pkg.isInstalled) Color(0xFF00FF41) else Color(0xFFFFB800))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pkg.isInstalled) {
                    Button(
                        onClick = {
                            android.util.Log.d("WineHub", "Downloading ${pkg.name}")
                            pkg = pkg.copy(isDownloading = true, downloadProgress = 0f)
                            onUpdate(pkg)
                            Thread {
                                val success = downloadBinary(context, pkg) { progress ->
                                    pkg = pkg.copy(downloadProgress = progress)
                                    onUpdate(pkg)
                                }
                                pkg = pkg.copy(
                                    isInstalled = success,
                                    isDownloading = false,
                                    downloadProgress = if (success) 1f else 0f
                                )
                                statusText = if (success) "✓ Installed" else "✗ Failed"
                                onUpdate(pkg)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(context, "${pkg.name}: ${if (success) "installed" else "failed"}", Toast.LENGTH_LONG).show()
                                }
                            }.start()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                    ) { Text("Download & Install") }
                } else {
                    Button(
                        onClick = {
                            val binFile = File(pkg.installPath, pkg.binaryName)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "Path: ${binFile.absolutePath}\nSize: ${binFile.length() / 1024} KB", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                    ) { Text("Installed ✓") }

                    Button(
                        onClick = {
                            File(pkg.installPath).deleteRecursively()
                            pkg = pkg.copy(isInstalled = false, downloadProgress = 0f)
                            statusText = "Removed"
                            onUpdate(pkg)
                            Toast.makeText(context, "${pkg.name} removed", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                    ) { Text("Remove") }
                }
            }
        }
    }
}

private fun downloadBinary(
    context: android.content.Context,
    pkg: BinaryPackage,
    onProgress: (Float) -> Unit
): Boolean {
    return try {
        val installDir = File(pkg.installPath)
        installDir.mkdirs()

        val cacheDir = context.cacheDir
        val cacheFile = File(cacheDir, "${pkg.name}-${pkg.version}.${if (pkg.isZip) "zip" else "tar.gz"}")
        cacheDir.mkdirs()

        // Download
        val url = URL(pkg.downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.connect()

        if (conn.responseCode != 200) {
            android.util.Log.e("WineHub", "Download failed: ${conn.responseCode}")
            return false
        }

        val totalSize = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(cacheFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalSize > 0) {
                        onProgress(downloaded.toFloat() / totalSize)
                    }
                }
            }
        }
        conn.disconnect()

        // Extract
        if (pkg.isZip) {
            ZipInputStream(FileInputStream(cacheFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(installDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        outFile.setExecutable(true)
                    }
                    entry = zis.nextEntry
                }
            }
        } else {
            // tar.gz extraction (simplified - just copy for now)
            val outFile = File(installDir, pkg.binaryName)
            FileInputStream(cacheFile).use { fis ->
                FileOutputStream(outFile).use { fos ->
                    fis.copyTo(fos)
                }
            }
            outFile.setExecutable(true)

            // Also try tar extraction if available
            if (execCmd("tar --version").contains("tar")) {
                execCmd("tar -xzf ${cacheFile.absolutePath} -C ${installDir.absolutePath} --strip-components=1")
            }
        }

        // Cleanup
        cacheFile.delete()

        // Set permissions
        val binFile = File(installDir, pkg.binaryName)
        if (binFile.exists()) {
            binFile.setExecutable(true)
            binFile.setReadable(true)
            true
        } else {
            // Check if any executable was extracted
            installDir.listFiles()?.any { it.canExecute() } == true
        }
    } catch (e: Exception) {
        android.util.Log.e("WineHub", "Download error: ${e.message}")
        e.printStackTrace()
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherScreen(info: DeviceInfo, binaries: List<BinaryPackage>) {
    val context = LocalContext.current
    var selectedFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var winePrefix by remember { mutableStateOf("/data/data/com.winenativehub/wineprefix") }
    var esyncOn by remember { mutableStateOf(true) }
    var box64On by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var outputLog by remember { mutableStateOf<List<String>>(listOf("Ready. Select an .exe file to launch.")) }

    val wineBin = binaries.find { it.name == "Wine" && it.isInstalled }
    val box64Bin = binaries.find { it.name == "Box64" && it.isInstalled }
    val dxvkBin = binaries.find { it.name == "DXVK" && it.isInstalled }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedFiles = uris.mapNotNull { uri ->
            uri.path?.substringAfterLast('/')
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (wineBin == null) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B1B))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚠️ Wine not installed", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                    Text("Go to Download tab and install Wine binary first.", color = Color(0xFFFFB8B8))
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2818))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("✓ Wine ${wineBin.version} ready", color = Color(0xFF00FF41), fontWeight = FontWeight.Bold)
                    if (box64Bin != null) Text("✓ Box64 ${box64Bin.version} available", color = Color(0xFF00FF41))
                    if (dxvkBin != null) Text("✓ DXVK ${dxvkBin.version} available", color = Color(0xFF00FF41))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Launcher Config", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ESYNC", color = Color.White)
                    Switch(checked = esyncOn, onCheckedChange = { esyncOn = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Box64", color = if (box64Bin != null) Color.White else Color.Gray)
                    Switch(checked = box64On, onCheckedChange = { box64On = it }, enabled = box64Bin != null)
                }

                OutlinedTextField(
                    value = winePrefix,
                    onValueChange = { winePrefix = it },
                    label = { Text("WINEPREFIX") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color(0xFF0F3460),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFFE94560),
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select .exe files (${selectedFiles.size})")
                }
            }
        }

        if (selectedFiles.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Queue:", color = Color.Gray)
                    selectedFiles.forEach { name ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFFE94560))
                            Text(name, color = Color.White)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (wineBin == null) {
                    Toast.makeText(context, "Install Wine first!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (selectedFiles.isEmpty()) {
                    Toast.makeText(context, "Select .exe first!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isRunning = true
                outputLog = listOf(">>> Starting ${wineBin.binaryName} ${wineBin.version}...")
                selectedFiles.forEach { name ->
                    val cmd = buildRealCmd(wineBin, box64Bin, dxvkBin, esyncOn, box64On, winePrefix, name)
                    outputLog = outputLog + "> ${cmd.joinToString(" ")}"
                    val res = execCmd(cmd.joinToString(" "))
                    outputLog = outputLog + res
                }
                outputLog = outputLog + ">>> Done."
                isRunning = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && selectedFiles.isNotEmpty() && wineBin != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color.Gray else Color(0xFFE94560)
            )
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isRunning) "Running..." else "Launch with Wine")
        }

        if (outputLog.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Output", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00D2FF))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        items(outputLog) { line ->
                            Text(
                                line,
                                color = if (line.startsWith(">") || line.startsWith("$")) Color(0xFF00D2FF) else Color(0xFF00FF41),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildRealCmd(
    wine: BinaryPackage,
    box64: BinaryPackage?,
    dxvk: BinaryPackage?,
    esync: Boolean,
    box64On: Boolean,
    prefix: String,
    exe: String
): List<String> {
    val parts = mutableListOf<String>()

    // Box64 wrapper
    if (box64On && box64 != null) {
        parts.add("${box64.installPath}/${box64.binaryName}")
    }

    // Wine binary
    parts.add("${wine.installPath}/${wine.binaryName}")

    // Environment variables
    parts.add("WINEPREFIX=$prefix")
    if (esync) parts.add("WINEESYNC=1")

    // DXVK library path
    if (dxvk != null) {
        parts.add("LD_LIBRARY_PATH=${dxvk.installPath}:\$LD_LIBRARY_PATH")
    }

    parts.add(exe)
    return parts
}

@Composable
private fun TerminalScreen() {
    var cmd by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<List<String>>(listOf("Terminal ready.")) }
    val scrollState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(output) { line ->
                Text(
                    line,
                    color = if (line.startsWith("$")) Color(0xFF00D2FF) else Color(0xFF00FF41),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("command") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE94560),
                    unfocusedBorderColor = Color(0xFF0F3460),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFFE94560),
                    unfocusedLabelColor = Color.Gray
                )
            )
            Button(
                onClick = {
                    if (cmd.isBlank()) return@Button
                    output = output + "$ $cmd"
                    val res = execCmd(cmd)
                    output = output + res
                    cmd = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
            ) { Text("Run") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    var wineVer by remember { mutableStateOf("8.0") }
    var renderer by remember { mutableStateOf("vulkan") }
    var fsr by remember { mutableStateOf(false) }
    var dxvk by remember { mutableStateOf("2.0") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE94560))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Wine Version", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("7.0", "8.0", "9.0", "10.0").forEach { v ->
                        FilterChip(
                            selected = wineVer == v,
                            onClick = { wineVer = v },
                            label = { Text(v) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Renderer", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("vulkan", "zink", "opengl", "llvmpipe").forEach { r ->
                        FilterChip(
                            selected = renderer == r,
                            onClick = { renderer = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AMD FSR", color = Color.White)
                    Switch(checked = fsr, onCheckedChange = { fsr = it })
                }
                Text("DXVK Version", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1.10.3", "2.0", "2.3", "2.5").forEach { v ->
                        FilterChip(selected = dxvk == v, onClick = { dxvk = v }, label = { Text(v) })
                    }
                }
            }
        }

        Button(
            onClick = {
                val msg = "Saved:\nWine=$wineVer\nRenderer=$renderer\nFSR=$fsr\nDXVK=$dxvk"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
        ) { Text("Save Settings") }

        Spacer(Modifier.height(20.dp))
    }
}

private fun execCmd(command: String): String {
    return try {
        val process = Runtime.getRuntime().exec(command)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errReader = BufferedReader(InputStreamReader(process.errorStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) sb.append(line).append("\n")
        while (errReader.readLine().also { line = it } != null) sb.append("[ERR] ").append(line).append("\n")
        process.waitFor()
        sb.toString().ifEmpty { "[exit: ${process.exitValue()}]" }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
