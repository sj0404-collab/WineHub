package com.winehub.nativebridge

object NativeBridge {
    init {
        System.loadLibrary("winehub_native")
    }

    external fun execute(command: String): String
    external fun readText(path: String): String
}
