package com.example.voice

import java.io.File

/**
 * Nullable audio helpers for the provider response path.
 * The call site falls back before attempting playback when no bytes are returned.
 */
internal fun ByteArray?.isNullOrEmpty(): Boolean = this == null || isEmpty()

internal fun File.writeBytes(bytes: ByteArray?): File {
    val safeBytes = bytes ?: return this
    outputStream().use { it.write(safeBytes) }
    return this
}
