package com.winehub.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.winehub.core.DeviceProfile
import com.winehub.core.GpuFamily
import com.winehub.core.VulkanRequirement
import com.winehub.dxvk.DxvkSystem
import com.winehub.graphics.GpuPipeline

@Composable
fun WineHubLibraryScreen(deviceInfo: DeviceProfile) {
    var tab by remember { mutableStateOf(0) }
    val logs = remember { mutableStateListOf("Wine Native Hub initialized") }
    val tabs = listOf("GPU", "DXVK", "Log")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = index == tab, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> GpuInfoTab(deviceInfo)
            1 -> DxvkInfoTab()
            else -> LogTab(logs)
        }
    }
}

@Composable
private fun GpuInfoTab(deviceInfo: DeviceProfile) {
    val gpu = remember { GpuPipeline() }
    val family = remember(deviceInfo.gpuVendor) { gpu.gpuFamily(deviceInfo.gpuVendor) }
    val caps = remember { gpu.detectCapabilities(0x401000, setOf("VK_KHR_dynamic_rendering", "VK_EXT_descriptor_indexing")) }

    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("GPU: ${deviceInfo.gpuVendor}", style = MaterialTheme.typography.titleMedium) }
        item { Text("Family: ${family.name}") }
        item { Text("Vulkan API: ${caps.apiVersion}") }
        item { Text("Descriptor Indexing: ${caps.hasDescriptorIndexing}") }
        item { Text("Dynamic Rendering: ${caps.supportsDynamicRendering}") }
        item { Text("Timeline Semaphore: ${caps.supportsTimelineSemaphore}") }
    }
}

@Composable
private fun DxvkInfoTab() {
    val dxvk = remember { DxvkSystem() }
    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("DXVK System Info", style = MaterialTheme.typography.titleMedium) }
        item { Text("DXVK 1.x: ${dxvk.selectDxvk(1)}") }
        item { Text("DXVK 2.x: ${dxvk.selectDxvk(2)}") }
        item {
            val env = dxvk.vkd3dConfig(enableAsync = true, lowMemoryMode = false)
            Text("VKD3D Config: $env")
        }
    }
}

@Composable
private fun LogTab(logs: List<String>) {
    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(logs.takeLast(20)) { entry -> Text(entry, style = MaterialTheme.typography.bodySmall) }
    }
}
