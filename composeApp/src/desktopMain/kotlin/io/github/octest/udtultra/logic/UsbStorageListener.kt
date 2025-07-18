package io.github.octest.udtultra.logic

import io.github.octestx.basic.multiplatform.common.utils.OS
import kotlinx.coroutines.delay
import java.io.File

suspend fun usbStorageListener(callback: (id: String, root: File) -> Unit) {
    val detectedDevices = mutableSetOf<String>()

    if (OS.currentOS == OS.OperatingSystem.WIN) {
        // Windows通过WMI监听USB存储设备插入
        while (true) {
            val result = "wmic path Win32_USBHub get DeviceID".executeCommand()
            val devices = result.lines().filter { it.contains("VID_") || it.contains("PID_") }

            devices.forEach { device ->
                if (device !in detectedDevices) {
                    detectedDevices.add(device)
                    // 获取卷序列号作为root路径
                    val volume = "wmic volume get DeviceID,Label".executeCommand()
                        .lines().firstOrNull { it.contains("Removable") }?.split(" ")?.firstOrNull()
                    volume?.let { callback(device, File(it)) }
                }
            }
            delay(1000)
        }
    } else if (OS.currentOS == OS.OperatingSystem.LINUX) {
        println("StartListenerService")
        // TODO Test
        callback("test", File("/media/octest/ESD-USB/"))
    }
}

// 扩展函数：执行命令并返回结果
private fun String.executeCommand(): String {
    val process = Runtime.getRuntime().exec(this.split(" ").toTypedArray())
    return process.inputStream.bufferedReader().readText()
}

// 扩展函数：读取文件第一行
private fun String.readFirstLine(): String? {
    val file = File(this)
    if (file.exists()) return file.bufferedReader().readLine()
    return null
}