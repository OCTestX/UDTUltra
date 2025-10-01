package io.github.octest.udtultra.logic

import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.klogging.Level
import io.klogging.config.ANSI_CONSOLE
import io.klogging.config.loggingConfiguration
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.SecureRandom
import kotlin.random.Random

/**
 * QuitCopper功能测试类
 * 用于验证从非KEY U盘复制符合规则的文件到KEY U盘的核心逻辑
 */
class QuitCopperTest {
    private val fromDir = File("/home/octest/Myself/DevProject/UDTUltra/build/TEST/QuitCopperTest1/from")
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
        // 清理测试环境
        fromDir.deleteRecursively()
        toDir.deleteRecursively()

        // 创建测试目录
        fromDir.mkdirs()
        toDir.mkdirs()

        // 生成测试文件
        createTestFiles(fromDir, 3)

        // 创建KEY U盘规则文件
        createKeyUdiskRules()

        // 初始化数据库
        setupTestDatabase()
    }

    fun testQuitCopper() {
        runBlocking {

            QuitCopper(keyEntry).start()

            // 验证复制结果
            val copiedFiles = collectCopiedFiles()
            val expectedFiles = collectExpectedFiles()

            if (copiedFiles.toSet() != expectedFiles.toSet()) {
                throw AssertionError("Test failed: Expected ${expectedFiles.size} files, but found ${copiedFiles.size} files")
            }
            println("Test passed! Copied ${copiedFiles.size} files.")
        }
    }

    /** 递归创建测试文件 */
    private fun createTestFiles(dir: File, maxDepth: Int, currentDepth: Int = 0) {
        // 创建当前目录下的文件
        fileTypes.forEach { ext ->
            val fileName = "test_${Random.nextInt(1000)}.${ext}"
            File(dir, fileName).writeBytes(generateRandomBytes(512 * 1024))
        }

        // 递归创建子目录
        if (currentDepth < maxDepth) {
            repeat(Random.nextInt(1, 4)) {
                val subDir = File(dir, "subdir_${Random.nextInt(100)}")
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
        val rulesDir = File(toDir, "KEY_UDT/quitCopy")
        rulesDir.mkdirs()
        File(rulesDir, "rules.txt").writeText(".*\\.(pptx?|docx?|xlsx?|ppt|doc|xls|txt|mp3|mp4|png|exe|zip)$\n")
    }

    /** 初始化测试数据库 */
    private fun setupTestDatabase() {
        runBlocking {
            // 添加KEY U盘
            UDTDatabase.writeNewEntry(
                keyEntry
            )

            // 添加源U盘
            UDTDatabase.writeNewEntry(
                UDiskEntry(
                    name = "Source UDisk",
                    target = fromDir,
                    id = "source-udisk",
                    totalSpace = 1024L * 1024 * 1024,
                    freeSpace = 1024L * 1024 * 1024,
                    type = UDiskEntry.Companion.Type.COMMON.value
                )
            )

            // 收集所有文件并注册到数据库
            collectAllFiles(fromDir, "").forEach { (_, file) ->
                UDTDatabase.runInEntry(
                    UDTDatabase.getEntry("source-udisk")!!
                ) {
                    saveFile(file)
                }
            }
        }
    }

    /** 收集所有应被复制的文件 */
    private fun collectExpectedFiles(): List<String> {
        val expected = mutableListOf<String>()
        collectAllFiles(fromDir, "").forEach { (path, _) ->
            if (path.matches(Regex(".*\\.(ppt|doc|xls|txt|mp3|mp4|png|exe|zip)$"))) {
                expected.add("source-udisk/$path")
            }
        }
        return expected
    }

    /** 收集已复制的文件 */
    private fun collectCopiedFiles(): List<String> {
        val copied = mutableListOf<String>()
        val targetDir = File(toDir, "KEY_UDT/quitCopy/Files")

        if (targetDir.exists()) {
            targetDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(targetDir).path
                    copied.add(relativePath)
                }
            }
        }
        return copied
    }

    /** 递归收集所有文件 */
    private fun collectAllFiles(dir: File, basePath: String): List<Pair<String, File>> {
        val files = mutableListOf<Pair<String, File>>()
        dir.listFiles()?.forEach { file ->
            val currentPath = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
            if (file.isFile) {
                files.add(currentPath to file)
            } else if (file.isDirectory) {
                files.addAll(collectAllFiles(file, currentPath))
            }
        }
        return files
    }
}

private fun main() {
    loggingConfiguration {
        ANSI_CONSOLE()
        this.kloggingMinLogLevel(Level.DEBUG)
    }
    val test = QuitCopperTest()
    try {
//        test.setup()
        test.testQuitCopper()
    } catch (e: Exception) {
        e.printStackTrace()
        println("测试失败: ${e.message}")
        System.exit(1)
    }
}
