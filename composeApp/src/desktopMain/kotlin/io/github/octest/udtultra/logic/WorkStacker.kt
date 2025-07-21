package io.github.octest.udtultra.logic

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

object WorkStacker {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val workers = mutableStateMapOf<String, Worker>()
    fun putWork(worker: Worker) {
        workers[worker.id] = worker
        scope.launch {
            worker.run()
        }
    }

    @Composable
    fun WorkerMiniComponent() {
        Column {
            workers.values.forEach { worker ->
                WorkerRow(worker)
            }
        }
    }

    @Composable
    private fun WorkerRow(worker: Worker) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = worker.title,
                modifier = Modifier.weight(1f)
            )
            when (worker.progressType) {
                ProgressType.HasProgress ->
                    LinearProgressIndicator(
                        progress = worker.progress,
                        modifier = Modifier.width(100.dp)
                    )

                ProgressType.Running ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
            }
        }
    }

    fun putWork(workInfo: WorkInfo, work: suspend WorkScope.() -> Unit) {
        putWork(Worker(workInfo, work))
    }

    suspend fun Worker.run() {
        try {
            coroutineScope {
                val workScope = WorkScopeImpl(this@run)
                workScope.apply {
                    work()
                }
            }
        } finally {
            // 任务完成后移除worker
            withContext(Dispatchers.Main) {
                workers.remove(id)
            }
        }
    }

    class WorkScopeImpl(private val worker: Worker) : WorkScope() {
        override fun setTitle(title: String) {
            worker.title = title
        }

        override fun setProgress(progress: Float) {
            worker.progress = progress
        }

        override fun setProgressType(type: ProgressType) {
            worker.progressType = type
        }

        override fun throwErrorAndCancel(error: Throwable) {
            worker.error = error
            throw error
        }
    }

    abstract class WorkScope {
        abstract fun setTitle(title: String)
        abstract fun setProgress(progress: Float)
        abstract fun setProgressType(type: ProgressType)
        abstract fun throwErrorAndCancel(error: Throwable)
    }

    data class WorkInfo(
        val title: String,
        val type: WorkType,
        val progressType: ProgressType
    )

    enum class WorkType {
        CopyFromSource, // 从U盘复制
        Delete,
        Send, // 发送到指定地点
        CopyToMasterUDisk, // 复制到主U盘
    }

    enum class ProgressType {
        HasProgress, //显示实时进度
        Running, //不显示实时进度
    }

    class Worker(
        val workInfo: WorkInfo,
        val work: suspend WorkScope.() -> Unit
    ) {
        val id = UUID.randomUUID().toString()
        var title by mutableStateOf(workInfo.title)
        var progress by mutableStateOf(0f)
        var progressType by mutableStateOf(workInfo.progressType)
        var error by mutableStateOf<Throwable?>(null)
    }
}

fun copyFileWorker(
    from: File,
    to: File,
    append: Boolean = false,
    after: suspend (exception: Throwable?) -> Unit
): WorkStacker.Worker {
    return WorkStacker.Worker(
        workInfo = WorkStacker.WorkInfo(
            title = "正在复制从${from.absolutePath}到${to.absolutePath}",
            type = WorkStacker.WorkType.CopyFromSource,
            progressType = WorkStacker.ProgressType.HasProgress
        ),
        work = {
            try {
                from.inputStream().use { inputStream ->
                    to.parentFile.apply {
                        if (exists().not()) {
                            mkdirs()
                        }
                    }
                    val totalSize = from.length()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead: Long = 0

                    if (append) {
                        inputStream.skipNBytes(to.length())
                        totalRead = to.length()
                    }

                    setProgress(0f)

                    var lastUpdateBytes = 0L
                    val updateInterval = totalSize / 100

                    FileOutputStream(to, append).use { outputStream ->
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            if (totalRead - lastUpdateBytes >= updateInterval) {
                                val progress = totalRead.toFloat() / totalSize.toFloat()
                                setProgress(progress)
                                lastUpdateBytes = totalRead
                            }
                        }
                    }

                    setProgress(1f)
                }
                after(null)
            } catch (e: Throwable) {
                after(e)
                throwErrorAndCancel(e)
            }
        }
    )
}