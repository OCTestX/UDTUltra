package io.github.octest.udtultra

import io.github.octestx.basic.multiplatform.common.utils.mb
import kotlinx.coroutines.delay
import kotlin.random.Random

object Config {
    val copySpeed = 5.mb
    val appDir = "/home/octest/Desktop/UDTUltra"
    // 经过多少次可以尝试休息
    val randomSleepCount = 100
    // 次数的摆动数值
    val shake = 12
    // 休息成功的概率
    val sleepFactor = 0.5
    // 休息时间
    val sleep = 3L



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