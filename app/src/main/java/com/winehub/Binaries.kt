package com.winehub

import android.content.Context
import android.os.Build
import android.provider.OpenableColumns
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
        Text("Install binaries you downloaded or auto-download", color = Color.Gray)

        Divider(color = Color(0xFF0F3460))

        val installed = binaries.count { it.isInstalled }
        Text("$installed/${binaries.size} installed", color = if (installed > 0) Color(0xFF00FF41) else Color.Gray)

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
    var pkgState by remember { mutableStateOf(pkg) }
    var statusText by remember { mutableStateOf("") }
    var showFileInfo by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            statusText = "Cancelled — no file selected"
            return@rememberLauncherForActivityResult
        }

        try {
            val cr = context.contentResolver

            // Get file name
            var fileName = "unknown"
            cr.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIdx)
                }
            }

            // Get file size
            var fileSize = 0L
            cr.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && cursor.moveToFirst()) {
                    fileSize = cursor.getLong(sizeIdx)
                }
            }

            // If we can't get size from cursor, read from stream
            if (fileSize <= 0) {
                cr.openInputStream(uri)?.use { fileSize = it.available().toLong() }
            }

            showFileInfo = "Selected: $fileName ($fileSize bytes)"

            // Copy to cache
            val cacheFile = File(context.cacheDir, "install_${System.currentTimeMillis()}_${fileName}")
            var copiedBytes = 0L
            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val buf = ByteArray(32768)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        copiedBytes += read
                    }
                }
            }

            if (copiedBytes <= 0) {
                statusText = "✗ Could not read file (0 bytes copied)"
                showFileInfo = "File: $fileName, Size: $fileSize, Copied: $copiedBytes"
                return@rememberLauncherForActivityResult
            }

            showFileInfo = "Copied: $copiedBytes bytes from $fileName"

            // Check compatibility
            val compatible = isCompatible(pkg.binaryName, cacheFile)

            if (!compatible) {
                statusText = "✗ NOT compatible with ${pkg.name}"
                showFileInfo = "Name: $fileName, Size: $copiedBytes, Type: ${cacheFile.extension}"
                cacheFile.delete()
                return@rememberLauncherForActivityResult
            }

            // Install
            val instDir = File(pkg.installPath)
            instDir.mkdirs()

            when {
                cacheFile.extension == "tar.gz" || cacheFile.extension == "tgz" -> {
                    val result = runCmd("tar -xzf ${cacheFile.absolutePath} -C ${instDir.absolutePath} 2>&1")
                    val found = findBin(instDir, pkg.binaryName)
                    if (found != null) {
                        found.setExecutable(true)
                        pkgState = pkgState.copy(isInstalled = true)
                        statusText = "✓ Installed from tar.gz"
                        showFileInfo = "Found: ${found.name} (${found.length() / 1024} KB)"
                    } else {
                        // DXVK might extract to subdirectories
                        val dlls = instDir.walkTopDown().filter { it.extension == "dll" }.toList()
                        if (dlls.isNotEmpty()) {
                            pkgState = pkgState.copy(isInstalled = true)
                            statusText = "✓ Installed DXVK (${dlls.size} DLLs)"
                            showFileInfo = "${dlls.size} DLL files extracted"
                        } else {
                            statusText = "✗ Extracted but binary not found"
                            showFileInfo = "Looking for: ${pkg.binaryName}. Files: ${instDir.listFiles()?.size ?: 0}"
                        }
                    }
                }
                cacheFile.extension == "zip" -> {
                    var foundTarget = false
                    java.util.zip.ZipInputStream(FileInputStream(cacheFile)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val n = entry.name.substringAfterLast('/')
                            if (n.isNotEmpty() && !entry.isDirectory) {
                                val outFile = File(instDir, n)
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                outFile.setExecutable(true)
                                if (n == pkg.binaryName || n.contains(pkg.binaryName, ignoreCase = true)) {
                                    foundTarget = true
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                    if (foundTarget || instDir.listFiles()?.any { it.canExecute() } == true) {
                        pkgState = pkgState.copy(isInstalled = true)
                        statusText = "✓ Installed from ZIP"
                        showFileInfo = "Extracted from: $fileName"
                    } else {
                        statusText = "✗ ZIP extracted but no executable found"
                    }
                }
                cacheFile.extension == "deb" -> {
                    val dpkgResult = runCmd("dpkg-deb -x ${cacheFile.absolutePath} ${instDir.absolutePath} 2>&1")
                    val found = findBin(instDir, pkg.binaryName)
                    if (found != null) {
                        found.setExecutable(true)
                        pkgState = pkgState.copy(isInstalled = true)
                        statusText = "✓ Installed from DEB"
                        showFileInfo = "Binary: ${found.absolutePath}"
                    } else {
                        // Try ar extraction
                        val tmpDir = File(instDir.parentFile, "ar_tmp_${System.currentTimeMillis()}")
                        tmpDir.mkdirs()
                        runCmd("cd ${tmpDir.absolutePath} && ar x ${cacheFile.absolutePath} 2>&1")
                        val dataTar = tmpDir.listFiles()?.find { it.name.startsWith("data.tar") }
                        if (dataTar != null) {
                            runCmd("tar -xf ${dataTar.absolutePath} -C ${instDir.absolutePath} 2>&1")
                            val found2 = findBin(instDir, pkg.binaryName)
                            if (found2 != null) {
                                found2.setExecutable(true)
                                pkgState = pkgState.copy(isInstalled = true)
                                statusText = "✓ Installed from DEB (ar)"
                            }
                        }
                        tmpDir.deleteRecursively()
                        if (!pkgState.isInstalled) {
                            statusText = "✗ DEB extracted but binary not found"
                            showFileInfo = "Looking for: ${pkg.binaryName}"
                        }
                    }
                }
                else -> {
                    // Direct binary
                    val destFile = File(instDir, pkg.binaryName)
                    cacheFile.copyTo(destFile, overwrite = true)
                    destFile.setExecutable(true)
                    destFile.setReadable(true)
                    if (destFile.length() > 0) {
                        pkgState = pkgState.copy(isInstalled = true)
                        statusText = "✓ Installed"
                        showFileInfo = "Size: ${destFile.length() / 1024} KB at ${destFile.absolutePath}"
                    } else {
                        statusText = "✗ Binary is 0 bytes"
                        destFile.delete()
                    }
                }
            }

            onUpdate(pkgState)
            Toast.makeText(context, "${pkg.name}: $statusText", Toast.LENGTH_LONG).show()
            cacheFile.delete()

        } catch (e: Exception) {
            statusText = "✗ Error: ${e.message}"
            showFileInfo = "Type: ${e.javaClass.simpleName}"
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (pkgState.isInstalled) Color(0xFF0D2818) else Color(0xFF16213E)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium, color = if (pkgState.isInstalled) Color(0xFF00FF41) else Color.White)
                    Text("v${pkg.version} • ${pkg.binaryName}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                if (pkgState.isInstalled) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF41))
                else Icon(Icons.Default.Download, null, tint = Color.Gray)
            }

            if (pkgState.isDownloading) {
                LinearProgressIndicator(
                    progress = { pkgState.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFFE94560),
                    trackColor = Color(0xFF0F3460)
                )
                Text("Downloading: ${(pkgState.downloadProgress * 100).toInt()}%", color = Color.Gray)
            }

            if (statusText.isNotEmpty()) {
                Text(statusText, color = if (pkgState.isInstalled) Color(0xFF00FF41) else Color(0xFFFFB800))
            }

            if (showFileInfo != null) {
                Text(
                    showFileInfo!!,
                    color = Color(0xFF00D2FF),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pkgState.isInstalled) {
                    Button(
                        onClick = {
                            pkgState = pkgState.copy(isDownloading = true, downloadProgress = 0f)
                            onUpdate(pkgState)
                            Thread {
                                val ok = downloadBinary(context, pkgState) { p ->
                                    pkgState = pkgState.copy(downloadProgress = p)
                                    onUpdate(pkgState)
                                }
                                pkgState = pkgState.copy(
                                    isInstalled = ok,
                                    isDownloading = false,
                                    downloadProgress = if (ok) 1f else 0f
                                )
                                statusText = if (ok) "✓ Installed" else "✗ Auto-download failed — use file below"
                                onUpdate(pkgState)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(context, "${pkg.name}: ${if (ok) "ok" else "failed"}", Toast.LENGTH_LONG).show()
                                }
                            }.start()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-download")
                    }

                    Button(
                        onClick = { filePicker.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("📁 Install from file")
                    }
                } else {
                    Button(
                        onClick = {
                            val f = File(pkg.installPath, pkg.binaryName)
                            if (f.exists()) {
                                Toast.makeText(context, "${f.absolutePath}\nSize: ${f.length() / 1024} KB", Toast.LENGTH_LONG).show()
                            } else {
                                val dir = File(pkg.installPath)
                                val count = dir.listFiles()?.size ?: 0
                                Toast.makeText(context, "${dir.absolutePath}\nFiles: $count", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                    ) { Text("Installed ✓") }

                    Button(
                        onClick = {
                            File(pkg.installPath).deleteRecursively()
                            pkgState = pkgState.copy(isInstalled = false, downloadProgress = 0f)
                            statusText = "Removed"
                            showFileInfo = null
                            onUpdate(pkgState)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                    ) { Text("Remove") }
                }
            }
        }
    }
}

private fun isCompatible(binaryName: String, file: File): Boolean {
    val lower = binaryName.lowercase()
    return when {
        lower == "winetricks" -> {
            // Shell script - should be text and large enough
            try {
                val firstLine = file.bufferedReader().useLines { it.firstOrNull() ?: "" }
                (firstLine.contains("#!/") || firstLine.isEmpty()) && file.length() > 5000
            } catch (e: Exception) { false }
        }
        lower == "dxvk" -> {
            // Can be tar.gz, zip, or DLL
            file.extension in listOf("tar.gz", "tgz", "zip", "dll", "gz") ||
                file.name.contains("dxvk", ignoreCase = true) ||
                file.name.contains("d3d", ignoreCase = true)
        }
        lower == "box64" || lower == "box86" -> {
            // ELF binary
            isElf(file) || file.name.contains(lower, ignoreCase = true)
        }
        else -> isElf(file) || file.length() > 1000
    }
}

private fun isElf(file: File): Boolean {
    return try {
        val h = ByteArray(4)
        FileInputStream(file).use { it.read(h) }
        h[0] == 0x7f.toByte() && h[1] == 0x45.toByte() && h[2] == 0x4C.toByte() && h[3] == 0x46.toByte()
    } catch (e: Exception) {
        false
    }
}

fun getDefaultBinaries(): List<BinaryPackage> {
    val a64 = Build.SUPPORTED_ABIS.any { it.contains("arm64", true) }
    val arch = if (a64) "aarch64" else "x86_64"
    return listOf(
        BinaryPackage(
            "Box64", "0.3.2",
            "https://packages-cf.termux.dev/apt/termux-main/pool/main/b/box64/box64_0.3.2-$arch.deb",
            "/data/data/com.winenativehub/files/box64", "box64", isDeb = true
        ),
        BinaryPackage(
            "DXVK", "2.5",
            "https://github.com/doitsujin/dxvk/releases/download/v2.5/dxvk-2.5.tar.gz",
            "/data/data/com.winenativehub/files/dxvk", "dxvk"
        ),
        BinaryPackage(
            "Winetricks", "2024",
            "https://raw.githubusercontent.com/Winetricks/winetricks/master/src/winetricks",
            "/data/data/com.winenativehub/files/winetricks", "winetricks"
        ),
        BinaryPackage(
            "Box86", "0.3.2",
            "https://packages-cf.termux.dev/apt/termux-main/pool/main/b/box86/box86_0.3.2-$arch.deb",
            "/data/data/com.winenativehub/files/box86", "box86", isDeb = true
        )
    )
}

fun downloadBinary(ctx: Context, pkg: BinaryPackage, onProgress: (Float) -> Unit): Boolean {
    return try {
        val instDir = File(pkg.installPath).apply { mkdirs() }
        val cacheDir = ctx.cacheDir.apply { mkdirs() }
        val ext = when {
            pkg.isDeb -> "deb"
            pkg.isZip -> "zip"
            pkg.downloadUrl.endsWith(".tar.gz") -> "tar.gz"
            else -> "bin"
        }
        val cf = File(cacheDir, "${pkg.name}-${pkg.version}.$ext")
        val url = URL(pkg.downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.connect()
        if (conn.responseCode !in listOf(200, 302)) {
            android.util.Log.e("WH", "HTTP ${conn.responseCode}")
            return false
        }
        val total = conn.contentLengthLong
        var dl = 0L
        conn.inputStream.use { inp ->
            FileOutputStream(cf).use { out ->
                val buf = ByteArray(16384)
                var r: Int
                while (inp.read(buf).also { r = it } != -1) {
                    out.write(buf, 0, r)
                    dl += r
                    if (total > 0) onProgress((dl.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        conn.disconnect()
        val ok = when {
            pkg.isDeb -> {
                runCmd("dpkg-deb -x ${cf.absolutePath} ${instDir.absolutePath}")
                findBin(instDir, pkg.binaryName)?.setExecutable(true)
                findBin(instDir, pkg.binaryName) != null
            }
            pkg.isZip -> {
                java.util.zip.ZipInputStream(FileInputStream(cf)).use { zis ->
                    var e = zis.nextEntry
                    var f = false
                    while (e != null) {
                        val n = e.name.substringAfterLast('/')
                        if (n.isNotEmpty()) {
                            val o = File(instDir, n)
                            o.parentFile?.mkdirs()
                            FileOutputStream(o).use { fos -> zis.copyTo(fos) }
                            o.setExecutable(true)
                            if (n.contains(pkg.binaryName, true)) f = true
                        }
                        e = zis.nextEntry
                    }
                    f
                }
            }
            ext == "tar.gz" -> {
                runCmd("tar -xzf ${cf.absolutePath} -C ${instDir.absolutePath}")
                findBin(instDir, pkg.binaryName)?.setExecutable(true)
                findBin(instDir, pkg.binaryName) != null ||
                    instDir.walkTopDown().any { it.extension == "dll" }
            }
            else -> {
                val d = File(instDir, pkg.binaryName)
                cf.copyTo(d, true)
                d.setExecutable(true)
                true
            }
        }
        cf.delete()
        ok
    } catch (e: Exception) {
        android.util.Log.e("WH", "Err: ${e.message}")
        false
    }
}

fun findBin(dir: File, name: String): File? = dir.walkTopDown().find { it.name == name && it.isFile }

fun runCmd(cmd: String): String = try {
    val p = Runtime.getRuntime().exec(cmd)
    p.waitFor()
    val out = p.inputStream.bufferedReader().readText()
    val err = p.errorStream.bufferedReader().readText()
    (out + err).trim().ifEmpty { "[exit:${p.exitValue()}]" }
} catch (e: Exception) {
    "err:${e.message}"
}
