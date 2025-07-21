package io.github.octest.udtultra.utils

import kotlinx.coroutines.*
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

class SpeedMonitoringOutputStream(
    private val outputStream: OutputStream,
    private val callback: suspend (speed: Long) -> Unit,
    private val period: Long = 1000L,  // 每秒更新一次
    context: CoroutineContext = Dispatchers.IO
) : OutputStream() {
    private val bytesWritten = AtomicLong(0)
    private val job = Job()
    private val scope = CoroutineScope(context + job)
    private var lastReportedTime = System.currentTimeMillis()
    private var lastReportedBytes = 0L

    init {
        startSpeedMonitor()
    }

    private fun startSpeedMonitor() {
        scope.launch {
            try {
                while (true) {
                    delay(period)
                    val currentBytes = bytesWritten.get()
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = (currentTime - lastReportedTime).coerceAtLeast(1)

                    // 计算瞬时速度（字节/秒）
                    val speed = ((currentBytes - lastReportedBytes) * 1000L) / timeDiff
                    callback(speed)

                    // 更新记录值
                    lastReportedTime = currentTime
                    lastReportedBytes = currentBytes
                }
            } catch (e: Exception) {
                // 忽略异常以避免协程崩溃
            }
        }
    }

    override fun write(b: Int) {
        outputStream.write(b)
        bytesWritten.incrementAndGet()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        outputStream.write(b, off, len)
        bytesWritten.addAndGet(len.toLong())
    }

    override fun flush() {
        outputStream.flush()
    }

    override fun close() {
        // 取消协程并执行最后一次速度计算
        val finalBytes = bytesWritten.get()
        val finalSpeed = finalBytes - lastReportedBytes
        runBlocking {
            callback(finalSpeed)
        }

        job.cancel()
        outputStream.close()
    }

    companion object {
        /**
         * 创建带速度监控的OutputStream
         * @param outputStream 基础输出流
         * @param callback 每秒传输速度回调（单位：字节/秒）
         * @param period 更新间隔（毫秒）
         * @return 包装后的OutputStream
         */
        fun create(
            outputStream: OutputStream,
            callback: (speed: Long) -> Unit,
            period: Long = 1000L
        ): OutputStream {
            return SpeedMonitoringOutputStream(outputStream, callback, period)
        }
    }
}
