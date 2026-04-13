package com.winehub.winelayer

class WineLauncher {
    fun launchCommand(executable: String, args: List<String> = emptyList()): List<String> =
        listOf("wine", executable) + args

    fun environmentInjector(prefixPath: String): Map<String, String> = mapOf(
        "WINEPREFIX" to prefixPath,
        "WINEDLLOVERRIDES" to "dxgi=n,b"
    )

    fun memoryProfile(lowRam: Boolean): Map<String, String> {
        return if (lowRam) mapOf("MALLOC_ARENA_MAX" to "2") else mapOf("MALLOC_ARENA_MAX" to "8")
    }
}
