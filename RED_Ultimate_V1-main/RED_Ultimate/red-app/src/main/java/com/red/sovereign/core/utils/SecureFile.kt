package com.red.sovereign.core.utils

import java.io.File
import java.io.RandomAccessFile

/**
 * Best-effort secure deletion for temporary files that ever held plaintext.
 *
 * Flash wear-leveling means a wipe is not a guarantee against forensic recovery,
 * but overwriting with zeros before delete() removes the obvious disk-level
 * recovery path. Always call this before deleting a temp file that contained
 * unencrypted audio/image data.
 */
object SecureFile {

    fun wipe(file: File?) {
        if (file == null || !file.exists()) return
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                val block = ByteArray(64 * 1024)
                val size = file.length()
                var written = 0L
                while (written < size) {
                    val count = minOf(block.size.toLong(), size - written).toInt()
                    raf.write(block, 0, count)
                    written += count
                }
                raf.fd.sync()
            }
        } catch (_: Exception) {
            // Best effort only.
        }
        file.delete()
    }
}
