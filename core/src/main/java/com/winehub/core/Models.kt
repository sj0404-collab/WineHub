package com.winehub.core

data class DeviceProfile(
    val cpuAbi: String,
    val totalMemoryMb: Long,
    val gpuVendor: String,
    val vulkanSupported: Boolean,
    val vulkanApi: String = "1.0.0",
    val hasDescriptorIndexing: Boolean = false,
    val androidVersion: String = "",
    val cpuCores: Int = 0,
    val buildModel: String = ""
)

data class Profile(
    val game: String,
    val useEsync: Boolean,
    val dxvkVersion: String,
    val threads: Int,
    val targetFps: Int,
    val fsrEnabled: Boolean
)

data class WineConfig(
    val windowsVersion: String,
    val renderer: String,
    val environment: Map<String, String>
)

data class RuntimeResult(val exitCode: Int, val stdout: String, val stderr: String)

enum class GpuFamily {
    ADRENO,
    MALI,
    OTHER
}

data class VulkanRequirement(
    val minimumMajor: Int,
    val minimumMinor: Int,
    val supportsSoftwarePromotion: Boolean
)

enum class DxvkBranch(val versionTag: String) {
    DXVK_1("1.x"),
    DXVK_2("2.x")
}

data class DxvkBlendPlan(
    val interfaceBranch: DxvkBranch,
    val renderBranch: DxvkBranch,
    val allowSwap: Boolean,
    val reason: String
)
