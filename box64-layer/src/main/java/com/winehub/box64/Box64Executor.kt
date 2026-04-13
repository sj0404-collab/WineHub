package com.winehub.box64

class Box64Executor {
    fun wrappedWineCommand(executable: String): List<String> = listOf("box64", "wine", executable)

    fun cpuAffinityMask(bigCoresOnly: Boolean): String = if (bigCoresOnly) "f0" else "ff"

    fun envForTranslation(): Map<String, String> = mapOf(
        "BOX64_DYNAREC" to "1",
        "BOX64_NOBANNER" to "1"
    )
}
