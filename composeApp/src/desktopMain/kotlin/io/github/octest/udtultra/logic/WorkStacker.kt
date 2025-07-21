package io.github.octest.udtultra.logic

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowUp
import compose.icons.tablericons.X
import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.DirRecord
import io.github.octest.udtultra.repository.database.DirTreeEntry
import io.klogging.noCoLogger
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

object WorkStacker {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val workers = mutableStateMapOf<String, Worker>()
    suspend fun putWork(worker: Worker): Job {
        withContext(Dispatchers.Main) {
            workers[worker.id] = worker
        }
        return scope.launch {
            worker.run()
        }
    }

    @Composable
    fun WorkerMiniComponent() {
        if (workers.isEmpty()) return

        var showPopup by remember { mutableStateOf(false) }

        Row {
            Box(modifier = Modifier.weight(1f)) {
                // 只显示第一个任务
                WorkerRow(workers.values.first(), Modifier.align(Alignment.CenterStart))
            }

            // 添加展开按钮
            IconButton(onClick = { showPopup = true }) {
                Icon(TablerIcons.ArrowUp, contentDescription = "展开任务列表")
            }

            // 弹出浮窗显示所有任务
            if (showPopup) {
                val density = LocalDensity.current
                Popup(
                    alignment = Alignment.BottomCenter,
                    offset = IntOffset(0, -density.run { 20.dp.roundToPx() }),
                    properties = PopupProperties(focusable = true),
                    onDismissRequest = { showPopup = false }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f) // 左右各留15dp空间
//                                .heightIn(max = LocalWindowSize.current.height.dp - 40.dp)
                            .padding(horizontal = 15.dp)
                            .clip(RoundedCornerShape(12.dp)) // 添加圆角
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    ) {
                        val scrollState = rememberLazyListState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Row {
                                Text(
                                    "后台任务",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    showPopup = false
                                }) {
                                    Icon(TablerIcons.X, contentDescription = null)
                                }
                            }
                            LazyColumn(state = scrollState) {
                                items(workers.values.toList(), key = { it.id }) { worker ->
                                    Card(modifier = Modifier.padding(3.dp).clip(MaterialTheme.shapes.extraSmall)) {
                                        WorkerRow(
                                            worker,
                                            modifier = Modifier.padding(6.dp).align(Alignment.CenterHorizontally)
                                        )
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WorkerRow(worker: Worker, modifier: Modifier = Modifier) {
        when (worker.progressType) {
            ProgressType.HasProgress ->
                Column(modifier) {
                    Text(
                        text = worker.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LinearProgressIndicator(
                        progress = worker.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = worker.message,
                        modifier = Modifier.align(Alignment.End)
                    )
                }

            ProgressType.Running ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = modifier
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp)
                        )
                        Text(
                            text = worker.title,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = worker.message,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
        }
    }

    suspend fun putWork(workInfo: WorkInfo, work: suspend WorkScope.() -> Unit) {
        putWork(Worker(workInfo, work))
    }

    suspend fun Worker.run() {
        try {
            coroutineScope {
                val workScope = WorkScopeImpl(this@run) {
                    throw CancellationException("任务取消", it)
                }
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

    class WorkScopeImpl(private val worker: Worker, private val cancel: (e: Throwable?) -> Unit) : WorkScope() {
        override suspend fun setTitle(title: String) {
            uiScope.launch {
                withContext(Dispatchers.Main) {
                    worker.title = title
                }
            }
        }

        override suspend fun setProgress(progress: Float) {
            uiScope.launch {
                withContext(Dispatchers.Main) {
                    worker.progress = progress
                }
            }
        }

        override suspend fun setMessage(message: String) {
            uiScope.launch {
                withContext(Dispatchers.Main) {
                    worker.message = message
                }
            }
        }

        override suspend fun setProgressType(type: ProgressType) {
            uiScope.launch {
                withContext(Dispatchers.Main) {
                    worker.progressType = type
                }
            }
        }

        override fun throwErrorAndCancel(error: Throwable) {
            worker.error = error
            cancel(error)
            throw error
        }
    }

    abstract class WorkScope {
        abstract suspend fun setTitle(title: String)
        abstract suspend fun setProgress(progress: Float)
        abstract suspend fun setMessage(message: String)
        abstract suspend fun setProgressType(type: ProgressType)
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
        var message by mutableStateOf("")
    }
}

object Workers {
    private val ologger = noCoLogger<Workers>()
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
                                //TODO
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

    fun copyDirWorker(
        entry: DirTreeEntry,
        from: DirRecord,
        to: File,
        append: Boolean = false,
        after: suspend (exception: Throwable?) -> Unit
    ): WorkStacker.Worker {
        return WorkStacker.Worker(
            workInfo = WorkStacker.WorkInfo(
                title = "正在复制文件夹从${from.relationDirPath}到${to.absolutePath}",
                type = WorkStacker.WorkType.CopyFromSource,
                progressType = WorkStacker.ProgressType.Running
            ),
            work = {
                try {
                    val fileRelations = mutableListOf<String>()
                    setTitle("正在复制文件夹从${from.relationDirPath}到${to.absolutePath}(正在统计中)")
                    ologger.info { "正在复制文件夹从${from.relationDirPath}到${to.absolutePath}(正在统计中)" }
                    var count = 0
                    var doneCount = 0
                    UDTDatabase.deepSeek(entry, from.relationDirPath, seekFile = {
                        fileRelations.add(it)
                        count++
                        setTitle("正在复制文件夹从${from.relationDirPath}到${to.absolutePath}(正在统计中($count): $it)")
                        ologger.info { "正在复制文件夹从${from.relationDirPath}到${to.absolutePath}(正在统计中($count): $it)" }
                    }, seekDir = {
                        setTitle("正在复制文件夹从${from.relationDirPath}到${to.absolutePath}(创建文件夹中: $it)")
                        ologger.info { "正在复制文件夹从${from.relationDirPath}到${to.absolutePath}(创建文件夹中: $it)" }
                    })
                    setProgressType(WorkStacker.ProgressType.HasProgress)
                    for (fileRelation in fileRelations) {
                        val source = FileTreeManager.getExitsFile(entry, fileRelation).getOrThrow()
                        val target = File(to, fileRelation.removePrefix(from.relationDirPath))
                        WorkStacker.putWork(copyFileWorker(source, target, append = false) {
                            if (it == null) {
                                doneCount++
                                setTitle("正在复制文件夹从${from.relationDirPath}到${to.absolutePath}($target)")
                                ologger.info { "正在复制文件夹从${from.relationDirPath}到${to.absolutePath}($target)" }
                                setProgress(doneCount.toFloat() / count)
                                if (count == doneCount) {
                                    after(null)
                                }
                            } else {
                                ologger.error(it) { "ERROR-正在复制文件夹从${from.relationDirPath}到${to.absolutePath}($target)" }
                            }
                        })
                    }
                } catch (e: Throwable) {
                    after(e)
                    throwErrorAndCancel(e)
                }
            }
        )
    }
}