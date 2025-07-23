package io.github.octest.udtultra

import io.github.octestx.basic.multiplatform.common.utils.OS
import kotlinx.coroutines.delay
import kotlin.random.Random

object Const {
    // TODO
    val appDir by lazy {
        if (OS.currentOS == OS.OperatingSystem.LINUX) "/home/octest/Desktop/UDTUltra"
        else "D:/UDTUltra"
    }
    val desktop by lazy {
        when (OS.currentOS) {
            OS.OperatingSystem.LINUX -> "/home/octest/Desktop"
            OS.OperatingSystem.WIN -> {
                val userHome = System.getProperty("user.home")
                "$userHome\\Desktop"
            }

            else -> throw Exception("Not supported OS")
        }
    }
    // 经过多少次可以尝试休息
    const val randomSleepCount = 100
    // 次数的摆动数值
    const val shake = 12
    // 休息成功的概率
    const val sleepFactor = 0.99
    // 休息时间
    const val sleep = 100L



    // 内部状态
    private var ioCount = 0
    private val random = Random.Default
    suspend fun seekPoint() {
        ioCount++
        // 计算当前休眠阈值（在 randomSleepCount ± shake 范围内）
        val currentThreshold = maxOf(1, randomSleepCount + random.nextInt(2 * shake + 1) - shake)

        if (ioCount >= currentThreshold) {
            // 根据概率决定是否休眠
            if (random.nextDouble() < sleepFactor) {
                println("DELAY!")
                delay(sleep)
            }
            ioCount = 0 // 重置计数器
        }
    }
}