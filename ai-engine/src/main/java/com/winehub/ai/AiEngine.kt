package com.winehub.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.winehub.core.DeviceProfile
import com.winehub.core.Profile
import com.winehub.core.WineConfig
import java.io.File
import kotlin.math.roundToInt

class AiEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("winehub_ai", Context.MODE_PRIVATE)

    fun loadGgufModel(path: File): Boolean = path.exists() && path.extension.equals("gguf", true)

    fun loadOnnxModel(path: File): Boolean = path.exists() && path.extension.equals("onnx", true)

    fun analyzeDevice(): DeviceProfile {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memInfo)
        return DeviceProfile(
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            totalMemoryMb = memInfo.totalMem / (1024 * 1024),
            gpuVendor = Build.HARDWARE,
            vulkanSupported = Build.VERSION.SDK_INT >= 26,
            vulkanApi = detectVulkanApi(),
            hasDescriptorIndexing = Build.VERSION.SDK_INT >= 29
        )
    }

    fun optimizeGameProfile(game: String): Profile {
        val lower = game.lowercase()
        val heavy = setOf("cyberpunk", "starfield", "alan wake", "hogwarts")
        val wins = prefs.getInt("telemetry_wins", 0)
        val losses = prefs.getInt("telemetry_losses", 0)
        val reliability = if (wins + losses == 0) 0.5 else wins.toDouble() / (wins + losses)

        val aggressive = heavy.any { it in lower } || reliability < 0.45
        val threadBoost = if (aggressive) 0 else 2
        val threads = (Runtime.getRuntime().availableProcessors() - threadBoost).coerceAtLeast(4)

        return Profile(
            game = game,
            useEsync = true,
            dxvkVersion = if (aggressive) "1.x" else "2.x",
            threads = threads,
            targetFps = if (aggressive) 45 else 60,
            fsrEnabled = aggressive
        )
    }

    fun suggestWineConfig(): WineConfig {
        val profile = analyzeDevice()
        val renderer = when {
            "adreno" in profile.gpuVendor.lowercase() -> "vulkan"
            "mali" in profile.gpuVendor.lowercase() -> "zink"
            else -> "opengl"
        }
        return WineConfig(
            windowsVersion = "win10",
            renderer = renderer,
            environment = mapOf(
                "WINEESYNC" to "1",
                "DXVK_ASYNC" to "1",
                "WINE_CPU_TOPOLOGY" to profile.cpuAbi,
                "WINEHUB_AI_SCORE" to modelScore(profile).toString()
            )
        )
    }

    fun recordSessionOutcome(stable: Boolean, avgFps: Double) {
        val wins = prefs.getInt("telemetry_wins", 0)
        val losses = prefs.getInt("telemetry_losses", 0)
        val fpsBucket = avgFps.roundToInt().coerceAtLeast(1)
        prefs.edit()
            .putInt("telemetry_wins", if (stable) wins + 1 else wins)
            .putInt("telemetry_losses", if (!stable) losses + 1 else losses)
            .putInt("telemetry_fps_bucket", fpsBucket)
            .apply()
    }

    private fun detectVulkanApi(): String {
        val glRenderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: ""
        return when {
            Build.VERSION.SDK_INT >= 34 -> "1.3.0"
            Build.VERSION.SDK_INT >= 30 -> "1.2.0"
            Build.VERSION.SDK_INT >= 26 -> "1.1.0"
            else -> "1.0.0"
        }
    }

    private fun modelScore(profile: DeviceProfile): Int {
        val memScore = (profile.totalMemoryMb / 1024).coerceAtMost(16)
        val vkScore = if (profile.vulkanSupported) 4 else 0
        val descriptor = if (profile.hasDescriptorIndexing) 2 else 0
        return (memScore + vkScore + descriptor).toInt()
    }
}
