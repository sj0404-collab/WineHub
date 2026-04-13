package com.winehub.container

import java.io.File

class ContainerManager(private val root: File) {
    fun installDistro(name: String): File {
        val distro = File(root, name)
        if (!distro.exists()) {
            distro.mkdirs()
            File(distro, "installed.marker").writeText("installed")
        }
        return distro
    }

    fun startContainer(name: String): Boolean = File(root, name).exists()

    fun execInContainer(cmd: String): List<String> = listOf("proot", "-0", "-r", root.absolutePath, "sh", "-lc", cmd)
}
