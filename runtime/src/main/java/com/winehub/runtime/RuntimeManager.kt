package com.winehub.runtime

import com.winehub.core.RuntimeResult

class RuntimeManager {
    fun buildCommand(useBox64: Boolean, exePath: String): List<String> {
        return if (useBox64) listOf("box64", "wine", exePath) else listOf("wine", exePath)
    }

    fun optimizeEnvironment(base: Map<String, String>): Map<String, String> {
        return base + mapOf(
            "WINEESYNC" to "1",
            "MESA_NO_ERROR" to "1",
            "WINEDEBUG" to "-all"
        )
    }

    fun parseResult(exitCode: Int, stdout: String, stderr: String): RuntimeResult =
        RuntimeResult(exitCode, stdout, stderr)
}
