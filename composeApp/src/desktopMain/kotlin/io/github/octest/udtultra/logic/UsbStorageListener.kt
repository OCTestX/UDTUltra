package io.github.octest.udtultra.logic

import io.github.octestx.basic.multiplatform.common.utils.OS
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchService

// 新增：USB事件类型枚举
enum class UsbEvent {
    INSERT, REMOVE
}

suspend fun usbStorageListener(
    initDriversId: Set<String> = setOf(),
    callback: (event: UsbEvent, id: String, root: File) -> Unit
) {
    if (OS.currentOS == OS.OperatingSystem.WIN) {
        val detectedDevices = mutableMapOf<String, File>()

        // 寻找已经连接的指定id的u盘, 这样可以不用拔出来再插回去来识别
        val initCurrentDevices = mutableMapOf<String, File>()
        updateWindowsUDrives(initCurrentDevices)
        // 检测设备
        initCurrentDevices.forEach { (id, root) ->
            if (initCurrentDevices.contains(id)) {
                detectedDevices[id] = root
                callback(UsbEvent.INSERT, id, root)
            }
        }

        while (true) {
            val currentDevices = mutableMapOf<String, File>()
            updateWindowsUDrives(currentDevices)

            // 检测新增设备
            currentDevices.forEach { (id, root) ->
                if (!detectedDevices.containsKey(id)) {
                    detectedDevices[id] = root
                    callback(UsbEvent.INSERT, id, root)
                }
            }

            // 检测移除设备
            detectedDevices.keys.filterNot { currentDevices.containsKey(it) }.forEach { id ->
                callback(UsbEvent.REMOVE, id, detectedDevices[id]!!)
                detectedDevices.remove(id)
            }

            delay(1000)
        }
    } else if (OS.currentOS == OS.OperatingSystem.LINUX) {
        // 维护已检测到的设备映射
        val detectedDevices = mutableMapOf<String, File>()

        val mediaPath = Paths.get("/media/${System.getProperty("user.name")}")
        // 确保用户媒体目录存在
        Files.createDirectories(mediaPath)

        // 新增：扫描现有挂载点（支持指定ID过滤）
        listInLinuxUDrivers().forEach { id, file ->
            // 如果initDriversId为空 或 ID在指定集合中，则触发事件
            if (id in initDriversId) {
                detectedDevices[id] = file
                callback(UsbEvent.INSERT, id, file)
            }
        }

        val watchService: WatchService = mediaPath.fileSystem.newWatchService()
        mediaPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE)

        while (true) {
            val key = watchService.take()
            key.pollEvents().forEach { event ->
                // 修复：显式将 event.context() 转换为 Path 类型
                val context = event.context() as? Path
                if (context != null) {
                    // 修复：使用正确的 resolve 重载方法
                    val devicePath = mediaPath.resolve(context)
                    val id = context.toString()
                    when (event.kind()) {
                        ENTRY_CREATE -> {
                            if (Files.isDirectory(devicePath)) {
                                // 避免重复触发已有设备
                                if (!detectedDevices.containsKey(id)) {
                                    detectedDevices[id] = devicePath.toFile()
                                    callback(UsbEvent.INSERT, id, devicePath.toFile())
                                }
                            }
                        }

                        ENTRY_DELETE -> {
                            if (detectedDevices.containsKey(id)) {
                                callback(UsbEvent.REMOVE, id, detectedDevices[id]!!)
                                detectedDevices.remove(id)
                            }
                        }

                        else -> {}
                    }
                }
            }
            key.reset()
        }
    }
}

private fun updateWindowsUDrives(detectedDevices: MutableMap<String, File>) {
    val command = "wmic path Win32_Volume where DriveType=2 get DeviceID,DriveLetter /format:csv"
    val result = command.executeCommand()

    result.trim().lines().drop(1) // 跳过CSV标题行
        .filter { it.isNotBlank() } // 过滤空行
        .forEach { line ->
            val parts = line.split(',', limit = 3)
            if (parts.size >= 3) {
                val rawId = parts[1].trim().removeSurrounding("\"")
                // 提取GUID部分
                val guid = extractVolumeGUID(rawId)!!
                val drive = parts[2].trim().removeSurrounding("\"")

                // 验证盘符格式（如"D:"）
                if (drive.isNotBlank() && drive.matches(Regex("[A-Z]:"))) {
                    detectedDevices[guid] = File("$drive\\")
                }
            }
        }
}

// 从原始设备ID中提取GUID
private fun extractVolumeGUID(rawId: String): String? {
    val pattern = """Volume\{([0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})\}""".toRegex()
    val matchResult = pattern.find(rawId)
    return matchResult?.groupValues?.get(1)
}

fun listInWindowsUDrivers(): Map<String, File> {
    val detectedDevices = mutableMapOf<String, File>()
    updateWindowsUDrives(detectedDevices)
    return detectedDevices
}

fun listInLinuxUDrivers(): Map<String, File> {
    // 新增：扫描现有挂载点（支持指定ID过滤）
    val mediaPath = Paths.get("/media/${System.getProperty("user.name")}")
    val map = mutableMapOf<String, File>()
    if (Files.exists(mediaPath)) {
        Files.list(mediaPath).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .forEach { path ->
                    val id = path.fileName.toString()
                    map[id] = path.toFile()
                }
        }
    }
    return map
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