package com.winehub.downloader

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class Downloader {
    fun downloadWithResume(url: String, destination: File): File {
        destination.parentFile?.mkdirs()
        val existing = if (destination.exists()) destination.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        connection.inputStream.use { input ->
            RandomAccessFile(destination, "rw").use { raf ->
                raf.seek(existing)
                val buf = ByteArray(8192)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    raf.write(buf, 0, read)
                }
            }
        }
        connection.disconnect()
        return destination
    }

    fun multiThreadDownload(urls: List<String>, outputDir: File): List<File> {
        val executor = Executors.newFixedThreadPool(urls.size.coerceAtMost(4))
        return try {
            val futures = urls.mapIndexed { index, u ->
                executor.submit<File> { downloadWithResume(u, File(outputDir, "file-$index.bin")) }
            }
            futures.map { it.get() }
        } finally {
            executor.shutdown()
        }
    }
}
