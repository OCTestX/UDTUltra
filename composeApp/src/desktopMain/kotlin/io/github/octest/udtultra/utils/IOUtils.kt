package io.github.octest.udtultra.utils

import java.io.InputStream
import java.io.OutputStream

suspend fun InputStream.transferToWithProgress(
    output: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    progress: suspend (currentStepRead: Int) -> Unit
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        output.write(buffer, 0, bytes)
        bytesCopied += bytes
        progress(bytes)
        bytes = read(buffer)
    }
    return bytesCopied
}