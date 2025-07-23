package io.github.octest.udtultra.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import io.github.octest.udtultra.Const
import io.github.octest.udtultra.logic.WorkStacker
import io.github.octest.udtultra.logic.Workers.copyFileWorker
import io.github.octest.udtultra.logic.Workers.copyUDiskDirWorker
import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.SettingRepository
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.DirRecord
import io.github.octest.udtultra.repository.database.FileRecord
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.github.octest.udtultra.ui.DirInfoDialog
import io.github.octest.udtultra.ui.DirItemUI
import io.github.octest.udtultra.ui.FileInfoDialog
import io.github.octest.udtultra.ui.FileItemUI
import io.github.octest.udtultra.ui.animation.DelayShowAnimationFromTopLeft
import io.github.octest.udtultra.ui.pages.FileBrowserPageMVIBackend.FileBrowserPageEvent.SwitchPath
import io.github.octestx.basic.multiplatform.common.utils.gb
import io.github.octestx.basic.multiplatform.common.utils.kb
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.utils.MVIBackend
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.klogging.noCoLogger
import kotlinx.coroutines.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File

/**
 * 主页面对象，继承自AbsUIPage
 * 管理文件浏览相关状态和UI展示
 */
/**
 * 文件浏览器UI组件
 * 展示目录树和文件列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun FileBrowserUI(
    backend: FileBrowserPageMVIBackend,
    state: FileBrowserPageMVIBackend.FileBrowserPageIntentState,
) {
    // 添加文件详情弹窗状态
    var selectedFile by remember { mutableStateOf<FileRecord?>(null) }
    var selectedDir by remember { mutableStateOf<DirRecord?>(null) }
    var showSelectedFile by remember { mutableStateOf(false) }

    // 添加控制侧滑栏的状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // 添加列表加载状态
    val isLoading by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(240.dp)
            ) {
                Row(Modifier.padding(6.dp)) {
                    Text("UDTServiceDaemon", modifier = Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = SettingRepository.daemonSwitch.value, onCheckedChange = {
                        SettingRepository.switchDaemonStatus(it)
                    })
                }
                SpeedSlider()
                // 顶部添加圆角Row布局
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .padding(8.dp)
                ) {
                    Row {
                        Text(
                            "数据源",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).weight(1f)
                        )
                    }
                }

                // 数据源列表 - 手动创建滚动条
                val drawerListState = rememberLazyListState()
                Box(Modifier.padding(4.dp)) {
                    LazyColumn(
                        state = drawerListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.entrys, key = { it.id }) { entry ->
                            val isSelected = state.currentEntry?.id == entry.id
                            Card(
                                onClick = {
                                    backend.emitIntent(
                                        FileBrowserPageMVIBackend.FileBrowserPageEvent.SelectedEntry(
                                            entry
                                        )
                                    )
                                },
                                Modifier.padding(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
                            ) {
                                Row {
                                    Column(Modifier.padding(6.dp).weight(1f)) {
                                        Text(
                                            entry.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "总共${storage(entry.totalSpace)}, 剩余${storage(entry.freeSpace)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    IconButton(onClick = {
                                        backend.emitIntent(
                                            FileBrowserPageMVIBackend.FileBrowserPageEvent.JumpToUDiskEditor(
                                                entry
                                            )
                                        )
                                    }) {
                                        Icon(TablerIcons.Edit, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(drawerListState)
                    )
                }
            }
        }
    ) {
        val pickExportTargetDirectory =
            rememberDirectoryPickerLauncher(title = "选择导出的文件的存放位置") { directory ->
                val selectedDirectory = selectedDir
                val absPath = directory?.absolutePath()
                if (absPath != null && selectedDirectory != null) {
                    backend.emitIntent(
                        FileBrowserPageMVIBackend.FileBrowserPageEvent.SendDirTo(
                            selectedDirectory,
                            File(absPath)
                        )
                    )
                }
            }
        val saveSingleFile = rememberFileSaverLauncher { directory ->
            val selectedFile = selectedFile
            val absPath = directory?.absolutePath()
            if (absPath != null && selectedFile != null) {
                backend.emitIntent(
                    FileBrowserPageMVIBackend.FileBrowserPageEvent.SendFileTo(
                        selectedFile,
                        File(absPath)
                    )
                )
            }
        }
        selectedFile?.let {
            if (showSelectedFile) {
                FileInfoDialog(
                    it,
                    cleanSelectedFile = { showSelectedFile = false },
                    sendFileTo = { file ->
                        val tmpFile = File(file.relationFilePath)
                        saveSingleFile.launch(tmpFile.nameWithoutExtension, tmpFile.extension)
                    },
                    sendFileToDesktop = { file ->
                        backend.emitIntent(
                            FileBrowserPageMVIBackend.FileBrowserPageEvent.SendFileToDesktop(
                                file
                            )
                        )
                    },
                    deleteAndBanFile = { file ->
                        backend.emitIntent(
                            FileBrowserPageMVIBackend.FileBrowserPageEvent.DeleteAndBanFile(
                                file
                            )
                        )
                    }
                )
            }
        }
        selectedDir?.let {
            if (showSelectedFile) {
                DirInfoDialog(
                    it,
                    cleanSelectedDir = { showSelectedFile = false },
                    sendDirTo = { file -> pickExportTargetDirectory.launch() },
                    sendDirToDesktop = { file ->
                        backend.emitIntent(
                            FileBrowserPageMVIBackend.FileBrowserPageEvent.SendDirToDesktop(
                                file
                            )
                        )
                    },
                    deleteAndBanDir = { file ->
                        backend.emitIntent(
                            FileBrowserPageMVIBackend.FileBrowserPageEvent.DeleteAndBanDir(
                                file
                            )
                        )
                    }
                )
            }
        }

        Scaffold(
            topBar = {
                val scope = rememberCoroutineScope()
                TopAppBar(
                    title = {
                        Text(
                            text = state.currentPath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        // 添加菜单按钮控制侧滑栏
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(TablerIcons.Menu2, contentDescription = "打开菜单")
                        }
                    },
                    actions = {
                        Row {
                            AnimatedVisibility(state.canBack) {
                                IconButton(onClick = {
                                    backend.emitIntent(FileBrowserPageMVIBackend.FileBrowserPageEvent.BackDirectory)
                                }) {
                                    Icon(TablerIcons.ArrowBack, contentDescription = "返回上一级")
                                }
                            }
                            IconButton(onClick = {
                                backend.emitIntent(FileBrowserPageMVIBackend.FileBrowserPageEvent.ReloadData)
                            }) {
                                Icon(TablerIcons.Loader, contentDescription = "Reload")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .padding(4.dp)
            ) {
                // 文件/目录列表优化 - 添加动画效果
                if (isLoading) {
                    // 加载时的动画提示
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                } else {
                    // 文件/目录列表 - 手动创建滚动条
                    val mainListState = rememberLazyListState()
                    Box(Modifier.weight(1f)) {
                        if ((state.currentFiles.size + state.currentDirs.size) > 0) {
                            LazyColumn(
                                state = mainListState,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize()
                            ) {
                                items(
                                    items = state.currentFiles,
                                    key = { it.relationFilePath }
                                ) { file ->
                                    DelayShowAnimationFromTopLeft(
                                        modifier = Modifier.animateItem()
                                    ) {
                                        // 修改文件点击事件：显示详情弹窗
                                        FileItemUI(file = file) {
                                            selectedDir = null
                                            selectedFile = file
                                            showSelectedFile = true
                                        }
                                    }
                                }
                                items(
                                    items = state.currentDirs,
                                    key = { it.relationDirPath }
                                ) { dir ->
                                    DelayShowAnimationFromTopLeft(modifier = Modifier.animateItem()) {
                                        DirItemUI(
                                            dir = dir,
                                            click = {
                                                backend.emitIntent(
                                                    FileBrowserPageMVIBackend.FileBrowserPageEvent.IntoDirectory(
                                                        dir.dirName
                                                    )
                                                )
                                            },
                                            clickInfo = {
                                                selectedFile = null
                                                selectedDir = dir
                                                showSelectedFile = true
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.align(Alignment.Center)) {
                                    Text(
                                        text = "没有文件",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        TablerIcons.FileOff,
                                        contentDescription = "没有文件",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(mainListState)
                        )
                    }
                }
                WorkStacker.WorkerMiniComponent()
            }
        }
    }
}


class FileBrowserPageMVIBackend(private val jumpToUDiskEditor: (entry: UDiskEntry) -> Unit) :
    MVIBackend<FileBrowserPageMVIBackend.FileBrowserPageIntentState, FileBrowserPageMVIBackend.FileBrowserPageEvent>() {
    private val ologger = noCoLogger<FileBrowserPageMVIBackend>()
    private var reloadNotify by mutableIntStateOf(0)
    private val entrys = mutableStateListOf<UDiskEntry>()
    private var currentEntry: UDiskEntry? by mutableStateOf(entrys.firstOrNull())
    private var currentPath: String by mutableStateOf("")
    private val currentFiles = mutableStateListOf<FileRecord>()
    private val currentDirs = mutableStateListOf<DirRecord>()

    /**
     * 事件处理器
     * 处理路径切换、进入目录、返回目录等操作
     */
    override suspend fun processIntent(event: FileBrowserPageEvent) {
        when (event) {
            FileBrowserPageEvent.ReloadData -> {
                reloadNotify++
            }

            is SwitchPath -> {
                // 处理路径切换，标准化路径格式
                val t1 = if (event.path.startsWith(File.separator))
                    event.path.removePrefix(File.separator) else event.path
                currentPath = if (t1.endsWith(File.separator)) {
                    t1.removeSuffix(File.separator)
                } else t1
            }

            is FileBrowserPageEvent.IntoDirectory -> {
                // 进入子目录：拼接新路径
                processIntent(SwitchPath(currentPath + File.separator + event.dirName))
            }

            is FileBrowserPageEvent.BackDirectory -> {
                // 返回上一级目录：移除当前路径最后一段
                processIntent(
                    SwitchPath(currentPath.removeSuffix(currentPath.split(File.separator).last()))
                )
            }

            is FileBrowserPageEvent.SelectedEntry -> {
                // 选择根目录：切换当前目录
                currentEntry = event.entry
                currentPath = ""
            }

            is FileBrowserPageEvent.DeleteAndBanFile -> {
                val entry = currentEntry
                if (entry != null) {
                    val source = FileTreeManager.getExitsFile(entry, event.file.relationFilePath)
                    source.onSuccess { source ->
                        val ioscope = CoroutineScope(Dispatchers.IO)
                        ioscope.launch {
                            WorkStacker.putWork(
                                WorkStacker.Worker(
                                    WorkStacker.WorkInfo(
                                        title = "",
                                        type = WorkStacker.WorkType.Delete,
                                        progressType = WorkStacker.ProgressType.Running
                                    ), work = {
                                        source.delete()
                                        UDTDatabase.changeBanedFileStatus(entry, event.file.relationFilePath, true)
                                        ologger.info { "文件已封禁： $source" }
                                        emitIntent(FileBrowserPageEvent.ReloadData)
                                    }
                                )
                            )
                        }
                    }
                    source.onFailure {
//                            toast.applyShow(ToastModel("复制失败",  type = ToastModel.Type.Error))
                        ologger.error { "文件已封禁(${source.exceptionOrNull()})" }
                    }
                }
            }

            is FileBrowserPageEvent.SendFileTo -> {
                val target = event.targetFile
                ologger.info { "sendFileTo: ${target.absolutePath}" }
                val entry = currentEntry
                if (entry != null) {
                    if (target.exists()) {
                        target.delete()
                    }
                    val source = FileTreeManager.getExitsFile(entry, event.file.relationFilePath)
                    source.onSuccess { source ->
                        val ioscope = CoroutineScope(Dispatchers.IO)
                        ioscope.launch {
                            WorkStacker.putWork(copyFileWorker(source, target, append = true) {
                                ologger.info { "复制完成" }
                            })
                        }
                    }
                    source.onFailure {
//                            toast.applyShow(ToastModel("复制失败",  type = ToastModel.Type.Error))
                        ologger.error(it) { "复制失败" }
                    }
                }
            }
            is FileBrowserPageEvent.SendFileToDesktop -> {
                val entry = currentEntry
                if (entry != null) {
                    val target = File(Const.desktop, event.file.fileName)
                    if (target.exists()) {
                        target.delete()
                    }
                    val source = FileTreeManager.getExitsFile(entry, event.file.relationFilePath)
                    source.onSuccess { source ->
                        val ioscope = CoroutineScope(Dispatchers.IO)
                        ioscope.launch {
                            WorkStacker.putWork(copyFileWorker(source, target, append = true) {
                                ologger.info { "复制完成" }
                            })
                        }
                    }
                    source.onFailure {
//                            toast.applyShow(ToastModel("复制失败",  type = ToastModel.Type.Error))
                        ologger.error(it) { "复制失败" }
                    }
                }
            }

            is FileBrowserPageEvent.SendDirTo -> {
                val entry = currentEntry
                if (entry != null) {
                    val target = File(event.targetDirectory, event.dir.dirName)
                    if (target.exists()) {
                        target.delete()
                    }
                    val ioscope = CoroutineScope(Dispatchers.IO)
                    ioscope.launch {
                        WorkStacker.putWork(copyUDiskDirWorker(entry, event.dir, target) {
                            ologger.info { "文件夹复制完成" }
                        })
                    }
                }
            }
            is FileBrowserPageEvent.SendDirToDesktop -> {
                val entry = currentEntry
                if (entry != null) {
                    val target = File(Const.desktop, event.dir.dirName)
                    if (target.exists()) {
                        target.delete()
                    }
                    val ioscope = CoroutineScope(Dispatchers.IO)
                    ioscope.launch {
                        WorkStacker.putWork(copyUDiskDirWorker(entry, event.dir, target) {
                            ologger.info { "文件夹复制完成" }
                        })
                    }
                }
            }

            is FileBrowserPageEvent.DeleteAndBanDir -> {
                val entry = currentEntry
                if (entry != null) {
                    UDTDatabase.deepSeek(entry, event.dir.relationDirPath, seekDir = { dirRecord ->
                        val ioscope = CoroutineScope(Dispatchers.IO)
                        ioscope.launch {
                            WorkStacker.putWork(
                                WorkStacker.Worker(
                                    WorkStacker.WorkInfo(
                                        title = "文件夹封禁...: ${dirRecord.relationDirPath}",
                                        type = WorkStacker.WorkType.Delete,
                                        progressType = WorkStacker.ProgressType.Running
                                    ), work = {
                                        UDTDatabase.changeBanedDirStatus(entry, dirRecord.relationDirPath, true)
                                    }
                                )
                            )
                        }
                    }, seekFile = { fileRecord ->
                        val source = FileTreeManager.getExitsFile(entry, fileRecord.relationFilePath)
                        val ioscope = CoroutineScope(Dispatchers.IO)
                        ioscope.launch {
                            WorkStacker.putWork(
                                WorkStacker.Worker(
                                    WorkStacker.WorkInfo(
                                        title = "文件封禁...: ${fileRecord.relationFilePath}",
                                        type = WorkStacker.WorkType.Delete,
                                        progressType = WorkStacker.ProgressType.Running
                                    ), work = {
                                        source.onSuccess { source ->
                                            source.delete()
                                        }
                                        source.onFailure {
                                            //                            toast.applyShow(ToastModel("复制失败",  type = ToastModel.Type.Error))
                                            // 要删除的文件未找到，可能已经被删除
                                        }
                                        UDTDatabase.changeBanedFileStatus(entry, fileRecord.relationFilePath, true)
                                        ologger.info { "文件封禁： ${fileRecord.relationFilePath}" }
                                    }
                                )
                            )
                        }
                    })
                    UDTDatabase.changeBanedDirStatus(entry, event.dir.relationDirPath, true)
                    emitIntent(FileBrowserPageEvent.ReloadData)
                }
            }
            is FileBrowserPageEvent.JumpToUDiskEditor -> jumpToUDiskEditor(event.entry)
        }

    }


    /**
     * 创建页面状态
     * 包含LaunchedEffect监听路径变化
     */
    @Composable
    override fun CalculateState(): FileBrowserPageIntentState {
        LaunchedEffect(currentEntry, currentPath, reloadNotify) {
            entrys.clear()
            entrys.addAll(UDTDatabase.getEntrys())
            val entry = currentEntry
            if ((entry?.id in entrys.map { it.id }).not()) {
                currentEntry = if (entrys.isEmpty()) null
                else entrys.first()
            }
            ologger.info { "ReloadData: $currentPath" }
            if (entry != null) {
                currentFiles.clear()
                currentDirs.clear()
                // 加载当前目录下的文件和子目录
                currentFiles.addAll(UDTDatabase.getFiles(entry, currentPath))
                currentDirs.addAll(UDTDatabase.getDirs(entry, currentPath))
            }
        }
        return FileBrowserPageIntentState(
            entrys, currentEntry, currentPath, currentFiles, currentDirs, canBack = currentPath.isNotEmpty(),
        )

    }

    /**
     * 页面状态类
     * @param entrys 根目录条目列表
     * @param currentEntry 当前选中的根目录
     * @param currentPath 当前浏览路径
     * @param currentFiles 当前目录下的文件列表
     * @param currentDirs 当前目录下的子目录列表
     * @param action 事件回调
     */
    data class FileBrowserPageIntentState(
        val entrys: List<UDiskEntry>,
        val currentEntry: UDiskEntry?,
        val currentPath: String,
        val currentFiles: List<FileRecord>,
        val currentDirs: List<DirRecord>,
        val canBack: Boolean,
    ) : IntentState()

    /**
     * 页面动作密封类
     * 包含路径切换、进入目录、返回目录等操作
     */
    sealed class FileBrowserPageEvent : IntentEvent() {
        data object ReloadData : FileBrowserPageEvent()
        data class SwitchPath(val path: String) : FileBrowserPageEvent()
        data class IntoDirectory(val dirName: String) : FileBrowserPageEvent()
        data object BackDirectory : FileBrowserPageEvent()
        data class SelectedEntry(val entry: UDiskEntry) : FileBrowserPageEvent()
        data class SendFileTo(val file: FileRecord, val targetFile: File) : FileBrowserPageEvent()
        data class SendFileToDesktop(val file: FileRecord) : FileBrowserPageEvent()
        data class DeleteAndBanFile(val file: FileRecord) : FileBrowserPageEvent()
        data class SendDirTo(val dir: DirRecord, val targetDirectory: File) : FileBrowserPageEvent()
        data class SendDirToDesktop(val dir: DirRecord) : FileBrowserPageEvent()
        data class DeleteAndBanDir(val dir: DirRecord) : FileBrowserPageEvent()

        data class JumpToUDiskEditor(val entry: UDiskEntry) : FileBrowserPageEvent()
    }
}

// 替换原有Row组件
@Composable
private fun SpeedSlider() {
    val ioscope = remember { CoroutineScope(Dispatchers.IO) }

    Column(
        Modifier
            .padding(6.dp)
            .fillMaxWidth()
    ) {
        Text(
            "复制速度: ${storage(SettingRepository.copySpeed.value)}",
        )
        Spacer(Modifier.width(6.dp))

        Slider(
            value = (SettingRepository.copySpeed.value).toFloat(),
            onValueChange = { newValue ->
                val speedInMB = newValue.toLong()
                // 启动协程更新速度
                ioscope.launch {
                    SettingRepository.changeCopySpeed(speedInMB)
                }
            },
            valueRange = 512.kb.toFloat()..1.gb.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}