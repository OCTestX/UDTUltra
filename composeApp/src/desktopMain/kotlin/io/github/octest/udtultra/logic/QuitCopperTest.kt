package io.github.octest.udtultra.logic

import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.klogging.Level
import io.klogging.config.ANSI_CONSOLE
import io.klogging.config.loggingConfiguration
import io.klogging.noCoLogger
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.SecureRandom
import kotlin.random.Random

/**
 * QuitCopper功能测试类
 * 用于验证从非KEY U盘复制符合规则的文件到KEY U盘的核心逻辑
 */
class QuitCopperTest {
    private val ologger = noCoLogger<QuitCopperTest>() // 添加logger实例
    private val fromDir = File("")
    private val toDir = File("/home/octest/Myself/DevProject/UDTUltra/build/TEST/QuitCopperTest1/to")
    private val fileTypes = listOf("ppt", "doc", "xls", "txt", "mp3", "mp4", "png", "exe", "zip")
    private val keyEntry = UDiskEntry(
        name = "KEY UDisk",
        target = toDir,
        id = "key-udisk",
        totalSpace = 1024L * 1024 * 1024, // 1GB
        freeSpace = 1024L * 1024 * 1024,
        type = UDiskEntry.Companion.Type.KEY.value
    )

    fun setup() {
        ologger.info { "开始清理测试环境" }
        // 清理测试环境
        toDir.deleteRecursively()

        ologger.info { "创建测试目录: toDir=${toDir.absolutePath}" }
        // 创建测试目录
        toDir.mkdirs()

//        ologger.info { "生成测试文件" }
//        // 生成测试文件
//        createTestFiles(fromDir, 3)

        ologger.info { "创建KEY U盘规则文件" }
        // 创建KEY U盘规则文件
        createKeyUdiskRules()

        ologger.info { "初始化数据库" }
        // 初始化数据库
        setupTestDatabase()
    }

    fun testQuitCopper() {
        runBlocking {
            ologger.info { "开始执行QuitCopper功能" }
            QuitCopper(keyEntry).start()

            ologger.info { "验证复制结果" }
            // 验证复制结果
            val copiedFiles = collectCopiedFiles()
            val expectedFiles = collectExpectedFiles()

            if (copiedFiles.toSet() != expectedFiles.toSet()) {
                ologger.warn { "测试失败: 预期 ${expectedFiles.size} 个文件，但找到 ${copiedFiles.size} 个文件" }
                throw AssertionError("Test failed: Expected ${expectedFiles.size} files, but found ${copiedFiles.size} files")
            }
            ologger.info { "测试通过! 已复制 ${copiedFiles.size} 个文件。" }
            println("Test passed! Copied ${copiedFiles.size} files.")
        }
    }

    /** 递归创建测试文件 */
    private fun createTestFiles(dir: File, maxDepth: Int, currentDepth: Int = 0) {
        ologger.info { "在目录 ${dir.absolutePath} 创建测试文件，当前深度 $currentDepth" }
        // 创建当前目录下的文件
        fileTypes.forEach { ext ->
            val fileName = "test_${Random.nextInt(1000)}.${ext}"
            ologger.info { "创建测试文件: $fileName" }
            File(dir, fileName).writeBytes(generateRandomBytes(512 * 1024))
        }

        // 递归创建子目录
        if (currentDepth < maxDepth) {
            repeat(Random.nextInt(1, 4)) {
                val subDir = File(dir, "subdir_${Random.nextInt(100)}")
                ologger.info { "创建子目录: ${subDir.absolutePath}" }
                subDir.mkdirs()
                createTestFiles(subDir, maxDepth, currentDepth + 1)
            }
        }
    }

    /** 生成随机字节数据 */
    private fun generateRandomBytes(size: Int): ByteArray {
        return ByteArray(size) { SecureRandom().nextInt(256).toByte() }
    }

    /** 创建KEY U盘规则文件 */
    private fun createKeyUdiskRules() {
        ologger.info { "创建KEY U盘规则文件到: ${toDir.absolutePath}" }
        val rulesDir = File(toDir, "KEY_UDT/quitCopy")
        rulesDir.mkdirs()
        File(rulesDir, "rules.txt").writeText(".*\\.(pptx?|docx?|xlsx?|ppt|doc|xls|txt|mp3|mp4|png|exe|zip)$\n")
        ologger.info { "KEY U盘规则文件创建完成" }
    }

    /** 初始化测试数据库 */
    private fun setupTestDatabase() {
        runBlocking {
            ologger.info { "开始初始化测试数据库" }
            // 添加KEY U盘
            UDTDatabase.writeNewEntry(
                keyEntry
            )

            ologger.info { "数据库初始化完成" }
        }
    }

    /** 收集所有应被复制的文件 */
    private fun collectExpectedFiles(): List<String> {
        ologger.info { "开始收集预期应被复制的文件" }
        val expected = mutableListOf<String>()
        collectAllFiles(fromDir, "").forEach { (path, _) ->
            if (path.matches(Regex(".*\\.(ppt|doc|xls|txt|mp3|mp4|png|exe|zip)$"))) {
                ologger.info { "添加预期文件: $path" }
                expected.add("source-udisk/$path")
            }
        }
        ologger.info { "共收集到 ${expected.size} 个预期文件" }
        return expected
    }

    /** 收集已复制的文件 */
    private fun collectCopiedFiles(): List<String> {
        ologger.info { "开始收集已复制的文件" }
        val copied = mutableListOf<String>()
        val targetDir = File(toDir, "KEY_UDT/quitCopy/Files")

        if (targetDir.exists()) {
            ologger.info { "目标目录存在: ${targetDir.absolutePath}" }
            targetDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(targetDir).path
                    ologger.info { "发现已复制文件: $relativePath" }
                    copied.add(relativePath)
                }
            }
        } else {
            ologger.warn { "目标目录不存在: ${targetDir.absolutePath}" }
        }
        ologger.info { "共收集到 ${copied.size} 个已复制文件" }
        return copied
    }

    /** 递归收集所有文件 */
    private fun collectAllFiles(dir: File, basePath: String): List<Pair<String, File>> {
        ologger.debug { "递归收集文件: ${dir.absolutePath}, basePath=$basePath" }
        val files = mutableListOf<Pair<String, File>>()
        dir.listFiles()?.forEach { file ->
            val currentPath = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
            if (file.isFile) {
                ologger.debug { "找到文件: $currentPath" }
                files.add(currentPath to file)
            } else if (file.isDirectory) {
                ologger.debug { "进入目录: $currentPath" }
                files.addAll(collectAllFiles(file, currentPath))
            }
        }
        return files
    }

    private suspend fun deepseekFiles(root: File, fetch: suspend (File) -> Unit) {
        for (item in root.listFiles()) {
            ologger.info { "DF: $item" }
            fetch(item)
            if (item.isDirectory) {
                deepseekFiles(item, fetch)
            }
        }
    }
}

private fun main() {
    loggingConfiguration {
        ANSI_CONSOLE()
        this.kloggingMinLogLevel(Level.DEBUG)
    }
    val test = QuitCopperTest()
    try {
        test.setup()
        test.testQuitCopper()
    } catch (e: Exception) {
        e.printStackTrace()
        println("测试失败: ${e.message}")
        System.exit(1)
    }
}