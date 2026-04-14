package com.winehub

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class BinaryPackage(
    val name: String,
    val version: String,
    val downloadUrl: String,
    val installPath: String,
    val binaryName: String,
    val isZip: Boolean = false,
    val isDeb: Boolean = false,
    val isInstalled: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(binaries: List<BinaryPackage>, onBinariesUpdate: (List<BinaryPackage>) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Binary Manager", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE94560))
        Text("Auto-download OR install from files you downloaded in browser", color = Color.Gray)
        Divider(color = Color(0xFF0F3460))
        Text("Components", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE94560))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(binaries) { pkg ->
                BinaryCard(pkg, context) { updated ->
                    onBinariesUpdate(binaries.map { if (it.name == updated.name) updated else it })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinaryCard(pkg: BinaryPackage, context: Context, onUpdate: (BinaryPackage) -> Unit) {
    var pkg by remember { mutableStateOf(pkg) }
    var statusText by remember { mutableStateOf("") }
    var showFileInfo by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { fileUri ->
            val cr = context.contentResolver
            val fileName = cr.query(fileUri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst()) c.getString(ni) else null
            } ?: "unknown"
            val cacheFile = File(context.cacheDir, "install_${System.currentTimeMillis()}_${fileName}")
            try {
                cr.openInputStream(fileUri)?.use { inp ->
                    FileOutputStream(cacheFile).use { out -> inp.copyTo(out) }
                }
                val compatible = when (pkg.binaryName.lowercase()) {
                    "winetricks" -> cacheFile.length() > 10000
                    "dxvk" -> cacheFile.extension == "tar.gz" || cacheFile.extension == "zip" || cacheFile.extension == "dll"
                    else -> isElf(cacheFile) || cacheFile.length() > 1000
                }
                if (compatible) {
                    val instDir = File(pkg.installPath).apply { mkdirs() }
                    val destFile = File(instDir, pkg.binaryName)
                    if (cacheFile.extension == "tar.gz" || cacheFile.extension == "tgz") {
                        exec("tar -xzf ${cacheFile.absolutePath} -C ${instDir.absolutePath}")
                        pkg = pkg.copy(isInstalled = true); statusText = "✓ Installed (tar)"
                        showFileInfo = "Extracted ${cacheFile.length() / 1024} KB"
                    } else if (cacheFile.extension == "zip") {
                        java.util.zip.ZipInputStream(FileInputStream(cacheFile)).use { zis ->
                            var e = zis.nextEntry
                            while (e != null) {
                                val n = e.name.substringAfterLast('/')
                                if (n.isNotEmpty()) {
                                    val f = File(instDir, n); f.parentFile?.mkdirs()
                                    FileOutputStream(f).use { fos -> zis.copyTo(fos) }; f.setExecutable(true)
                                }
                                e = zis.nextEntry
                            }
                        }
                        pkg = pkg.copy(isInstalled = true); statusText = "✓ Installed (zip)"
                        showFileInfo = "Extracted: $fileName"
                    } else {
                        cacheFile.copyTo(destFile, overwrite = true); destFile.setExecutable(true)
                        pkg = pkg.copy(isInstalled = true); statusText = "✓ Installed"
                        showFileInfo = "Size: ${destFile.length() / 1024} KB"
                    }
                    onUpdate(pkg); Toast.makeText(context, "${pkg.name} installed!", Toast.LENGTH_LONG).show()
                } else {
                    showFileInfo = "NOT compatible: $fileName (${cacheFile.length() / 1024} KB)"
                    Toast.makeText(context, "Not compatible with ${pkg.name}", Toast.LENGTH_LONG).show()
                }
                cacheFile.delete()
            } catch (e: Exception) {
                showFileInfo = "Error: ${e.message}"
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (pkg.isInstalled) Color(0xFF0D2818) else Color(0xFF16213E))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium, color = if (pkg.isInstalled) Color(0xFF00FF41) else Color.White)
                    Text("v${pkg.version} • ${pkg.binaryName}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                if (pkg.isInstalled) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF41)) else Icon(Icons.Default.Download, null, tint = Color.Gray)
            }
            if (pkg.isDownloading) {
                LinearProgressIndicator(progress = { pkg.downloadProgress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Color(0xFFE94560), trackColor = Color(0xFF0F3460))
                Text("Downloading: ${(pkg.downloadProgress * 100).toInt()}%", color = Color.Gray)
            }
            if (statusText.isNotEmpty()) Text(statusText, color = if (pkg.isInstalled) Color(0xFF00FF41) else Color(0xFFFFB800))
            if (showFileInfo != null) Text(showFileInfo!!, color = Color(0xFF00D2FF), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pkg.isInstalled) {
                    Button(onClick = {
                        pkg = pkg.copy(isDownloading = true, downloadProgress = 0f); onUpdate(pkg)
                        Thread {
                            val ok = downloadBinary(context, pkg) { p -> pkg = pkg.copy(downloadProgress = p); onUpdate(pkg) }
                            pkg = pkg.copy(isInstalled = ok, isDownloading = false, downloadProgress = if (ok) 1f else 0f)
                            statusText = if (ok) "✓ Installed" else "✗ Failed — use 'Install from file'"
                            onUpdate(pkg)
                            android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, "${pkg.name}: ${if (ok) "ok" else "failed"}", Toast.LENGTH_LONG).show() }
                        }.start()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))) { Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Auto-download") }
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))) { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("📁 Install from file") }
                } else {
                    Button(onClick = { Toast.makeText(context, "Path: ${File(pkg.installPath, pkg.binaryName).absolutePath}", Toast.LENGTH_LONG).show() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))) { Text("Installed ✓") }
                    Button(onClick = { File(pkg.installPath).deleteRecursively(); pkg = pkg.copy(isInstalled = false, downloadProgress = 0f); statusText = "Removed"; showFileInfo = null; onUpdate(pkg) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))) { Text("Remove") }
                }
            }
        }
    }
}

fun isElf(file: File): Boolean = try {
    val h = ByteArray(4); FileInputStream(file).use { it.read(h) }
    h[0] == 0x7f.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte()
} catch (e: Exception) { false }

fun getDefaultBinaries(): List<BinaryPackage> {
    val a64 = Build.SUPPORTED_ABIS.any { it.contains("arm64", true) }
    val arch = if (a64) "aarch64" else "x86_64"
    return listOf(
        BinaryPackage("Box64", "0.3.2", "https://packages-cf.termux.dev/apt/termux-main/pool/main/b/box64/box64_0.3.2-$arch.deb", "/data/data/com.winenativehub/files/box64", "box64", isDeb = true),
        BinaryPackage("DXVK", "2.5", "https://github.com/doitsujin/dxvk/releases/download/v2.5/dxvk-2.5.tar.gz", "/data/data/com.winenativehub/files/dxvk", "dxvk"),
        BinaryPackage("Winetricks", "2024", "https://raw.githubusercontent.com/Winetricks/winetricks/master/src/winetricks", "/data/data/com.winenativehub/files/winetricks", "winetricks"),
        BinaryPackage("Box86", "0.3.2", "https://packages-cf.termux.dev/apt/termux-main/pool/main/b/box86/box86_0.3.2-$arch.deb", "/data/data/com.winenativehub/files/box86", "box86", isDeb = true)
    )
}

fun downloadBinary(ctx: Context, pkg: BinaryPackage, onProgress: (Float) -> Unit): Boolean = try {
    val instDir = File(pkg.installPath).apply { mkdirs() }
    val cacheDir = ctx.cacheDir.apply { mkdirs() }
    val ext = when { pkg.isDeb -> "deb"; pkg.isZip -> "zip"; pkg.downloadUrl.endsWith(".tar.gz") -> "tar.gz"; else -> "bin" }
    val cf = File(cacheDir, "${pkg.name}-${pkg.version}.$ext")
    val url = URL(pkg.downloadUrl); val conn = url.openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = true; conn.connectTimeout = 30000; conn.readTimeout = 120000; conn.connect()
    if (conn.responseCode !in listOf(200, 302)) { android.util.Log.e("WH", "HTTP ${conn.responseCode}"); return false }
    val total = conn.contentLengthLong; var dl = 0L
    conn.inputStream.use { inp -> FileOutputStream(cf).use { out ->
        val buf = ByteArray(16384); var r: Int
        while (inp.read(buf).also { r = it } != -1) { out.write(buf, 0, r); dl += r; if (total > 0) onProgress((dl.toFloat() / total).coerceAtMost(1f)) }
    }}; conn.disconnect()
    val ok = when {
        pkg.isDeb -> { exec("dpkg-deb -x ${cf.absolutePath} ${instDir.absolutePath}"); findBin(instDir, pkg.binaryName)?.setExecutable(true); findBin(instDir, pkg.binaryName) != null }
        pkg.isZip -> { java.util.zip.ZipInputStream(FileInputStream(cf)).use { zis -> var e = zis.nextEntry; var f = false; while (e != null) { val n = e.name.substringAfterLast('/'); if (n.isNotEmpty()) { val o = File(instDir, n); o.parentFile?.mkdirs(); FileOutputStream(o).use { fos -> zis.copyTo(fos) }; o.setExecutable(true); if (n.contains(pkg.binaryName, true)) f = true }; e = zis.nextEntry }; f } }
        ext == "tar.gz" -> { exec("tar -xzf ${cf.absolutePath} -C ${instDir.absolutePath}"); findBin(instDir, pkg.binaryName)?.setExecutable(true); findBin(instDir, pkg.binaryName) != null || instDir.walkTopDown().any { it.extension == "dll" } }
        else -> { val d = File(instDir, pkg.binaryName); cf.copyTo(d, true); d.setExecutable(true); true }
    }; cf.delete(); ok
} catch (e: Exception) { android.util.Log.e("WH", "Err: ${e.message}"); false }

fun findBin(dir: File, name: String): File? = dir.walkTopDown().find { it.name == name && it.isFile }
fun exec(cmd: String): String = try { val p = Runtime.getRuntime().exec(cmd); p.waitFor(); "[exit:${p.exitValue()}]" } catch (e: Exception) { "err:${e.message}" }
