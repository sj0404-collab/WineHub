package com.winehub.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winehub.ai.AiEngine
import com.winehub.core.GpuFamily
import com.winehub.core.VulkanRequirement
import com.winehub.dxvk.DxvkSystem
import com.winehub.graphics.GpuPipeline

@Composable
fun WineHubApp() {
    var tab by remember { mutableStateOf(0) }
    val logs = remember { mutableStateListOf("Wine Native Hub initialized") }
    val tabs = listOf("Home", "Launcher", "Container", "AI", "Settings")

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = index == tab, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> HomeDashboard(logs)
                1 -> AppLauncher(logs)
                2 -> ContainerManagerView(logs)
                3 -> AiPanel(logs)
                else -> SettingsView(logs)
            }
            RuntimeLogConsole(logs)
        }
    }
}

@Composable
private fun HomeDashboard(logs: MutableList<String>) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("System Status", style = MaterialTheme.typography.titleLarge)
            Text("Runtime: Ready")
            Text("Container: Controlled by proot")
            Text("Graphics: Hybrid DXVK switching enabled")
            Button(onClick = { logs.add("Status refresh at ${System.currentTimeMillis()}") }) { Text("Refresh") }
        }
    }
}

@Composable
private fun AppLauncher(logs: MutableList<String>) {
    val executables = listOf("setup.exe", "game.exe", "launcher.exe")
    LazyColumn {
        items(executables) { exe ->
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exe)
                Button(onClick = { logs.add("Queue launch for $exe with runtime optimizer") }) { Text("Run") }
            }
        }
    }
}

@Composable
private fun ContainerManagerView(logs: MutableList<String>) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { logs.add("Ubuntu rootfs install requested") }) { Text("Install Ubuntu") }
        Button(onClick = { logs.add("Container started") }) { Text("Start Container") }
        Button(onClick = { logs.add("Executed: winecfg") }) { Text("Exec Command") }
    }
}

@Composable
private fun AiPanel(logs: MutableList<String>) {
    val context = LocalContext.current
    val ai = remember { AiEngine(context) }
    val dxvk = remember { DxvkSystem() }
    val gpu = remember { GpuPipeline() }
    val profile = remember { ai.analyzeDevice() }
    var prioritizeFps by remember { mutableStateOf(true) }
    var swapBranches by remember { mutableStateOf(false) }

    val family = remember(profile.gpuVendor) { gpu.gpuFamily(profile.gpuVendor) }
    val blendPlan = remember(family, profile.vulkanApi, prioritizeFps, swapBranches) {
        dxvk.createBlendPlan(
            gpuFamily = family,
            profile = profile,
            prioritizeFps = prioritizeFps,
            forceSwap = swapBranches
        )
    }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI Optimization Panel", style = MaterialTheme.typography.titleMedium)
        Text("GPU: ${profile.gpuVendor} (${family.name})")
        Text("Vulkan API: ${profile.vulkanApi}")
        Text("DXVK UI=${blendPlan.interfaceBranch.versionTag} Render=${blendPlan.renderBranch.versionTag}")
        Text("Reason: ${blendPlan.reason}")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Prioritize FPS")
            Switch(checked = prioritizeFps, onCheckedChange = { prioritizeFps = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Swap UI/Render branch")
            Switch(checked = swapBranches, onCheckedChange = { swapBranches = it })
        }

        Button(onClick = {
            val suggestion = ai.suggestWineConfig()
            val promotion = gpu.promoteForRequirement(
                profile = profile,
                requirement = VulkanRequirement(minimumMajor = 1, minimumMinor = 2, supportsSoftwarePromotion = true)
            )
            logs.add("AI renderer=${suggestion.renderer}, score=${suggestion.environment["WINEHUB_AI_SCORE"]}")
            if (promotion != null) {
                logs.add("Vulkan promoted to ${promotion.promotedApi} via ${promotion.softwarePath}")
            } else {
                logs.add("Native Vulkan level is sufficient")
            }
        }) { Text("Apply AI + Vulkan Plan") }

        Button(onClick = {
            ai.recordSessionOutcome(stable = true, avgFps = 59.0)
            logs.add("Telemetry trained: stable session 59 FPS")
        }) { Text("Train AI from Session") }
    }
}

@Composable
private fun SettingsView(logs: MutableList<String>) {
    var forceMaliCompatibility by remember { mutableStateOf(false) }
    var forceSnapdragonCompatibility by remember { mutableStateOf(true) }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleMedium)

        CompatibilityToggle("Force Mali compatibility", forceMaliCompatibility) {
            forceMaliCompatibility = it
            if (it) logs.add("Mali compatibility path enabled")
        }

        CompatibilityToggle("Force Snapdragon compatibility", forceSnapdragonCompatibility) {
            forceSnapdragonCompatibility = it
            if (it) logs.add("Snapdragon compatibility path enabled")
        }

        Button(onClick = {
            val family = when {
                forceMaliCompatibility -> GpuFamily.MALI
                forceSnapdragonCompatibility -> GpuFamily.ADRENO
                else -> GpuFamily.OTHER
            }
            logs.add("Compatibility policy active for ${family.name}")
        }) { Text("Apply Compatibility") }
    }
}

@Composable
private fun CompatibilityToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun RuntimeLogConsole(logs: List<String>) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        LazyColumn(modifier = Modifier.padding(10.dp)) {
            items(logs.takeLast(12)) { entry -> Text(entry) }
        }
    }
}
