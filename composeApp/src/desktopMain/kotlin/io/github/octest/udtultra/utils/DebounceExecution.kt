package io.github.octest.udtultra.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

object DebounceExecution {
    private const val DEBOUNCE_DELAY_MS = 3000L
    private val commandChannel = Channel<DebounceCommand>(Channel.BUFFERED)
    private val ioscope = CoroutineScope(Dispatchers.IO)

    private data class DebounceCommand(
        val id: String,
        val delayMillis: Long,
        val block: suspend () -> Unit
    )

    // 存储每个任务的最后执行时间
    private val lastTriggerMap = mutableMapOf<String, Long>()

    init {
        listenCommands()
    }

    private fun listenCommands() = ioscope.launch {
        val activeTasks = mutableMapOf<String, Job>()

        for (cmd in commandChannel) {
            val now = System.currentTimeMillis()
            lastTriggerMap[cmd.id] = now

            activeTasks[cmd.id]?.cancel()

            val job = launch {
                delay(cmd.delayMillis)

                // 只有当当前时间戳与最后触发时间一致时才执行
                if (lastTriggerMap[cmd.id] == now) {
                    cmd.block()
                }
            }

            activeTasks[cmd.id] = job
        }
    }

    /**
     * 防抖执行（线程安全）
     * @param id 任务唯一标识
     * @param delayMillis 自定义延迟时间（毫秒）
     * @param block 待执行任务
     */
    suspend fun debounceExecution(
        id: String,
        delayMillis: Long = DEBOUNCE_DELAY_MS,
        block: suspend () -> Unit
    ) {
        commandChannel.send(DebounceCommand(id, delayMillis, block))
    }

}