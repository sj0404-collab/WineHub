package com.winehub.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.winehub.core.DeviceProfile

@Composable
fun WineHubLibraryScreen(deviceInfo: DeviceProfile) {
    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("GPU Info", style = MaterialTheme.typography.titleMedium) }
        item { Text("Vendor: ${deviceInfo.gpuVendor}") }
        item { Text("Vulkan API: ${deviceInfo.vulkanApi}") }
        item { Text("Vulkan Supported: ${deviceInfo.vulkanSupported}") }
        item { Text("Descriptor Indexing: ${deviceInfo.hasDescriptorIndexing}") }
        item { Text("CPU ABI: ${deviceInfo.cpuAbi}") }
        item { Text("RAM: ${deviceInfo.totalMemoryMb} MB") }
    }
}
