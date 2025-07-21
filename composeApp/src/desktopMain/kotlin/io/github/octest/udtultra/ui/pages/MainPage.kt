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
import compose.icons.tablericons.ArrowBack
import compose.icons.tablericons.Loader
import compose.icons.tablericons.Menu2
import compose.icons.tablericons.X
import io.github.octest.udtultra.logic.WorkStacker
import io.github.octest.udtultra.logic.Workers.copyDirWorker
import io.github.octest.udtultra.logic.Workers.copyFileWorker
import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.SettingRepository
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.DirRecord
import io.github.octest.udtultra.repository.database.DirTreeEntry
import io.github.octest.udtultra.repository.database.FileRecord
import io.github.octest.udtultra.ui.DirInfoDialog
import io.github.octest.udtultra.ui.DirItemUI
import io.github.octest.udtultra.ui.FileInfoDialog
import io.github.octest.udtultra.ui.FileItemUI
import io.github.octest.udtultra.ui.animation.DelayShowAnimationFromTopLeft
import io.github.octest.udtultra.ui.pages.MainPage.MainPageAction.SwitchPath
import io.github.octestx.basic.multiplatform.common.utils.gb
import io.github.octestx.basic.multiplatform.common.utils.kb
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.core.AbsUIPage
import io.klogging.noCoLogger
import kotlinx.coroutines.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File

/**
 * 主页面对象，继承自AbsUIPage
 * 管理文件浏览相关状态和UI展示
 */
object MainPage : AbsUIPage<Unit, MainPage.MainPageState, MainPage.MainPageAction>(MainPageModel()) {
    private val ologger = noCoLogger<MainPage>()

    @Composable
    override fun UI(state: MainPageState) {
        FileBrowserUI(
            state.entrys,
            state.currentEntry,
            state.currentPath,
            state.currentFiles,
            state.currentDirs,
            canBack = state.canBack,
            reload = {
                state.action(MainPageAction.ReloadData)
            },
            selectedEntry = {
                state.action(MainPageAction.SelectedEntry(it))
            },
            intoDirectory = {
                state.action(MainPageAction.IntoDirectory(it))
            },
            backDirectory = {
                state.action(MainPageAction.BackDirectory)
            },
            sendFileTo = {
                state.action(MainPageAction.SendFileTo(it))
            },
            sendFileToDesktop = {
                state.action(MainPageAction.SendFileToDesktop(it))
            },
            deleteAndBanFile = {
                state.action(MainPageAction.DeleteAndBanFile(it))
            },
            sendDirTo = {
                state.action(MainPageAction.SendDirTo(it))
            },
            sendDirToDesktop = {
                state.action(MainPageAction.SendDirToDesktop(it))
            },
            deleteAndBanDir = {
                state.action(MainPageAction.DeleteAndBanDir(it))
            }
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
    data class MainPageState(
        val entrys: List<DirTreeEntry>,
        val currentEntry: DirTreeEntry?,
        val currentPath: String,
        val currentFiles: List<FileRecord>,
        val currentDirs: List<DirRecord>,
        val canBack: Boolean,
        val action: (MainPageAction) -> Unit,
    ) : AbsUIState<MainPageAction>()

    class MainPageModel() : AbsUIModel<Unit, MainPageState, MainPageAction>() {
        private var reloadNotify by mutableIntStateOf(0)
        private val entrys = mutableStateListOf<DirTreeEntry>()
        private var currentEntry: DirTreeEntry? by mutableStateOf(entrys.firstOrNull())
        private var currentPath: String by mutableStateOf("")
        private val currentFiles = mutableStateListOf<FileRecord>()
        private val currentDirs = mutableStateListOf<DirRecord>()

        /**
         * 创建页面状态
         * 包含LaunchedEffect监听路径变化
         */
        @Composable
        override fun CreateState(params: Unit): MainPageState {
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
            return MainPageState(
                entrys, currentEntry, currentPath, currentFiles, currentDirs, canBack = currentPath.isNotEmpty(),
                action = {
                    actionExecute(params, it)
                }
            )
        }

        /**
         * 事件处理器
         * 处理路径切换、进入目录、返回目录等操作
         */
        override fun actionExecute(params: Unit, action: MainPageAction) {
            when (action) {
                MainPageAction.ReloadData -> {
                    reloadNotify++
                }
                is SwitchPath -> {
                    // 处理路径切换，标准化路径格式
                    val t1 = if (action.path.startsWith(File.separator))
                        action.path.removePrefix(File.separator) else action.path
                    currentPath = if (t1.endsWith(File.separator)) {
                        t1.removeSuffix(File.separator)
                    } else t1
                }

                is MainPageAction.IntoDirectory -> {
                    // 进入子目录：拼接新路径
                    actionExecute(params, SwitchPath(currentPath + File.separator + action.dirName))
                }

                is MainPageAction.BackDirectory -> {
                    // 返回上一级目录：移除当前路径最后一段
                    actionExecute(
                        params,
                        SwitchPath(currentPath.removeSuffix(currentPath.split(File.separator).last()))
                    )
                }
                is MainPageAction.SelectedEntry -> {
                    // 选择根目录：切换当前目录
                    currentEntry = action.entry
                    currentPath = ""
                }
                is MainPageAction.DeleteAndBanFile -> TODO()
                is MainPageAction.SendFileTo -> TODO()
                is MainPageAction.SendFileToDesktop -> {
                    val entry = currentEntry
                    if (entry != null) {
                        val target = File("/home/octest/Desktop", action.file.fileName)
                        if (target.exists()) {
                            target.delete()
                        }
                        val source = FileTreeManager.getExitsFile(entry, action.file.relationFilePath)
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

                is MainPageAction.SendDirTo -> TODO()
                is MainPageAction.SendDirToDesktop -> {
                    val entry = currentEntry
                    if (entry != null) {
                        val target = File("/home/octest/Desktop/TEST1", action.dir.dirName)
                        if (target.exists()) {
                            target.delete()
                        }
                        val ioscope = CoroutineScope(Dispatchers.IO)
                        ioscope.launch {
                            WorkStacker.putWork(copyDirWorker(entry, action.dir, target) {
                                ologger.info { "文件夹复制完成" }
                            })
                        }
                    }
                }

                is MainPageAction.DeleteAndBanDir -> TODO()
            }
        }
    }

    /**
     * 页面动作密封类
     * 包含路径切换、进入目录、返回目录等操作
     */
    sealed class MainPageAction : AbsUIAction() {
        data object ReloadData : MainPageAction()
        data class SwitchPath(val path: String) : MainPageAction()
        data class IntoDirectory(val dirName: String) : MainPageAction()
        data object BackDirectory : MainPageAction()
        data class SelectedEntry(val entry: DirTreeEntry) : MainPageAction()
        data class SendFileTo(val file: FileRecord) : MainPageAction()
        data class SendFileToDesktop(val file: FileRecord) : MainPageAction()
        data class DeleteAndBanFile(val file: FileRecord) : MainPageAction()
        data class SendDirTo(val dir: DirRecord) : MainPageAction()
        data class SendDirToDesktop(val dir: DirRecord) : MainPageAction()
        data class DeleteAndBanDir(val dir: DirRecord) : MainPageAction()
    }
}

/**
 * 文件浏览器UI组件
 * 展示目录树和文件列表
 * @param entrys 根目录条目
 * @param currentEntry 当前根目录
 * @param currentPath 当前路径
 * @param currentFiles 当前文件列表
 * @param currentDirs 当前目录列表
 * @param intoDirectory 进入目录回调
 * @param backDirectory 返回目录回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun FileBrowserUI(
    entrys: List<DirTreeEntry>,
    currentEntry: DirTreeEntry?,
    currentPath: String,
    currentFiles: List<FileRecord>,
    currentDirs: List<DirRecord>,
    canBack: Boolean,
    reload: () -> Unit,
    selectedEntry: (DirTreeEntry) -> Unit,
    intoDirectory: (String) -> Unit,
    backDirectory: () -> Unit,
    sendFileTo: (FileRecord) -> Unit,
    sendFileToDesktop: (FileRecord) -> Unit,
    deleteAndBanFile: (FileRecord) -> Unit,
    sendDirTo: (DirRecord) -> Unit,
    sendDirToDesktop: (DirRecord) -> Unit,
    deleteAndBanDir: (DirRecord) -> Unit
) {
    // 添加文件详情弹窗状态
    var selectedFile by remember { mutableStateOf<FileRecord?>(null) }
    var selectedDir by remember { mutableStateOf<DirRecord?>(null) }
    selectedFile != null || selectedDir != null

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
                        val scope = rememberCoroutineScope() + CoroutineName("FileBrowserUIScope")
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.close()
                            }
                        }) {
                            Icon(TablerIcons.X, contentDescription = null)
                        }
                    }
                }

                // 数据源列表 - 手动创建滚动条
                val drawerListState = rememberLazyListState()
                Box(Modifier.padding(4.dp)) {
                    LazyColumn(
                        state = drawerListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(entrys, key = { it.id }) { entry ->
                            val isSelected = currentEntry?.id == entry.id
                            Card(
                                onClick = {
                                    selectedEntry(entry)
                                },
                                Modifier.padding(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
                            ) {
                                Column(Modifier.padding(6.dp)) {
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
        selectedFile?.let {
            FileInfoDialog(
                it,
                cleanSelectedFile = { selectedFile = null },
                sendFileTo = sendFileTo,
                sendFileToDesktop = sendFileToDesktop,
                deleteAndBanFile = deleteAndBanFile
            )
        }
        selectedDir?.let {
            DirInfoDialog(
                it,
                cleanSelectedDir = { selectedDir = null },
                sendDirTo = sendDirTo,
                sendDirToDesktop = sendDirToDesktop,
                deleteAndBanDir = deleteAndBanDir
            )
        }

        Scaffold(
            topBar = {
                val scope = rememberCoroutineScope()
                TopAppBar(
                    title = {
                        Text(
                            text = currentPath,
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
                            AnimatedVisibility(canBack) {
                                IconButton(onClick = backDirectory) {
                                    Icon(TablerIcons.ArrowBack, contentDescription = "返回上一级")
                                }
                            }
                            IconButton(onClick = {
                                reload()
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
                        LazyColumn(
                            state = mainListState,
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxSize()
                        ) {
                            items(
                                items = currentFiles,
                                key = { it.relationFilePath }
                            ) { file ->
                                DelayShowAnimationFromTopLeft(
                                    modifier = Modifier.animateItem()
                                ) {
                                    // 修改文件点击事件：显示详情弹窗
                                    FileItemUI(file = file) {
                                        selectedFile = file
                                    }
                                }
                            }
                            items(
                                items = currentDirs,
                                key = { it.relationDirPath }
                            ) { dir ->
                                DelayShowAnimationFromTopLeft(modifier = Modifier.animateItem()) {
                                    DirItemUI(
                                        dir = dir,
                                        click = { intoDirectory(dir.dirName) },
                                        clickInfo = {
                                            selectedDir = dir
                                        }
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

// 替换原有Row组件
@Composable
fun SpeedSlider() {
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
            valueRange = 512.kb.toFloat()..5.gb.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}