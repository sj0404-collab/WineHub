package com.winehub.graphics

import com.winehub.core.DeviceProfile
import com.winehub.core.GpuFamily
import com.winehub.core.VulkanRequirement

enum class GpuPath { TURNIP, ZINK, LLVMPIPE }

data class VulkanCaps(
    val apiVersion: String,
    val hasDescriptorIndexing: Boolean,
    val supportsTimelineSemaphore: Boolean,
    val supportsDynamicRendering: Boolean
)

data class VulkanPromotion(
    val promotedApi: String,
    val softwarePath: String,
    val additionalEnv: Map<String, String>
)

class GpuPipeline {
    fun gpuFamily(renderer: String): GpuFamily {
        val lower = renderer.lowercase()
        return when {
            "adreno" in lower || "snapdragon" in lower -> GpuFamily.ADRENO
            "mali" in lower -> GpuFamily.MALI
            else -> GpuFamily.OTHER
        }
    }

    fun detectPath(renderer: String): GpuPath {
        return when (gpuFamily(renderer)) {
            GpuFamily.ADRENO -> GpuPath.TURNIP
            GpuFamily.MALI -> GpuPath.ZINK
            GpuFamily.OTHER -> GpuPath.LLVMPIPE
        }
    }

    fun detectCapabilities(version: Int, extensions: Set<String>): VulkanCaps {
        val major = version shr 22
        val minor = (version shr 12) and 0x3ff
        val patch = version and 0xfff
        return VulkanCaps(
            apiVersion = "$major.$minor.$patch",
            hasDescriptorIndexing = "VK_EXT_descriptor_indexing" in extensions,
            supportsTimelineSemaphore = "VK_KHR_timeline_semaphore" in extensions,
            supportsDynamicRendering = "VK_KHR_dynamic_rendering" in extensions
        )
    }

    fun promoteForRequirement(profile: DeviceProfile, requirement: VulkanRequirement): VulkanPromotion? {
        if (!requirement.supportsSoftwarePromotion) return null

        val (major, minor) = parseApi(profile.vulkanApi)
        if (major > requirement.minimumMajor || (major == requirement.minimumMajor && minor >= requirement.minimumMinor)) {
            return null
        }

        val path = when (gpuFamily(profile.gpuVendor)) {
            GpuFamily.ADRENO -> "turnip+vulkan-loader-wrap"
            GpuFamily.MALI -> "zink+lavapipe"
            GpuFamily.OTHER -> "lavapipe"
        }

        return VulkanPromotion(
            promotedApi = "${requirement.minimumMajor}.${requirement.minimumMinor}.0",
            softwarePath = path,
            additionalEnv = mapOf(
                "WINEHUB_VK_PROMOTE" to "1",
                "WINEHUB_VK_SOFTWARE_PATH" to path,
                "MESA_VK_VERSION_OVERRIDE" to "${requirement.minimumMajor}.${requirement.minimumMinor}"
            )
        )
    }

    private fun parseApi(api: String): Pair<Int, Int> {
        val parts = api.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major to minor
    }
}
