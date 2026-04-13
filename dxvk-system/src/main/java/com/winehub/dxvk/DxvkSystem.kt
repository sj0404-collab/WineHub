package com.winehub.dxvk

import com.winehub.core.DeviceProfile
import com.winehub.core.DxvkBlendPlan
import com.winehub.core.DxvkBranch
import com.winehub.core.GpuFamily

class DxvkSystem {
    fun selectDxvk(versionMajor: Int): String = if (versionMajor >= 2) "dxvk-2.x" else "dxvk-1.x"

    fun createBlendPlan(
        gpuFamily: GpuFamily,
        profile: DeviceProfile,
        prioritizeFps: Boolean,
        forceSwap: Boolean
    ): DxvkBlendPlan {
        val lowVulkan = isVulkanBelow12(profile.vulkanApi)
        val defaultPlan = when {
            gpuFamily == GpuFamily.ADRENO && prioritizeFps -> DxvkBlendPlan(
                interfaceBranch = DxvkBranch.DXVK_1,
                renderBranch = DxvkBranch.DXVK_2,
                allowSwap = true,
                reason = "Adreno: interface stability from 1.x with render path from 2.x"
            )

            gpuFamily == GpuFamily.MALI || lowVulkan -> DxvkBlendPlan(
                interfaceBranch = DxvkBranch.DXVK_2,
                renderBranch = DxvkBranch.DXVK_1,
                allowSwap = true,
                reason = "Mali/low Vulkan: modern HUD path with legacy renderer compatibility"
            )

            else -> DxvkBlendPlan(
                interfaceBranch = DxvkBranch.DXVK_2,
                renderBranch = DxvkBranch.DXVK_2,
                allowSwap = false,
                reason = "Full DXVK 2.x path"
            )
        }

        return if (forceSwap && defaultPlan.allowSwap) {
            defaultPlan.copy(
                interfaceBranch = defaultPlan.renderBranch,
                renderBranch = defaultPlan.interfaceBranch,
                reason = "${defaultPlan.reason}. Manual swap enabled"
            )
        } else {
            defaultPlan
        }
    }

    fun runtimeEnv(plan: DxvkBlendPlan, emulatedVulkan: Boolean): Map<String, String> {
        val base = mutableMapOf(
            "WINEHUB_DXVK_INTERFACE" to plan.interfaceBranch.versionTag,
            "WINEHUB_DXVK_RENDER" to plan.renderBranch.versionTag,
            "DXVK_HUD" to "compiler",
            "DXVK_ASYNC" to "1"
        )
        if (emulatedVulkan) {
            base["DXVK_NVAPIHACK"] = "0"
            base["DXVK_ENABLE_DXGI_CUSTOM_VENDOR_ID"] = "1"
        }
        return base
    }

    fun vkd3dConfig(enableAsync: Boolean, lowMemoryMode: Boolean): Map<String, String> {
        val config = if (enableAsync) "force_static_cbv" else ""
        return mapOf(
            "VKD3D_CONFIG" to config,
            "VKD3D_SHADER_CACHE_PATH" to "/sdcard/WineNativeHub/cache",
            "VKD3D_DEBUG" to if (lowMemoryMode) "warn" else "none"
        )
    }

    private fun isVulkanBelow12(api: String): Boolean {
        val parts = api.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major < 1 || (major == 1 && minor < 2)
    }
}
