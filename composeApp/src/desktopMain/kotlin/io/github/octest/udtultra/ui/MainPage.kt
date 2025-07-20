package io.github.octest.udtultra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
import compose.icons.tablericons.Menu2
import io.github.octest.udtultra.logic.WorkStacker
import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.ui.MainPage.MainPageAction.SwitchPath
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.core.AbsUIPage
import io.klogging.noCoLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            intoDirectory = {
                state.action(MainPageAction.IntoDirectory(it))
            },
            backDirectory = {
                state.action(MainPageAction.BackDirectory)
            },
            sendFileTo = {

            },
            sendFileToDesktop = {
                state.action(MainPageAction.SendFileToDesktop(it))
            },
            deleteAndBanFile = {
                TODO()
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
        val entrys: List<UDTDatabase.DirTreeEntry>,
        val currentEntry: UDTDatabase.DirTreeEntry?,
        val currentPath: String,
        val currentFiles: List<UDTDatabase.FileRecord>,
        val currentDirs: List<UDTDatabase.DirRecord>,
        val canBack: Boolean,
        val action: (MainPageAction) -> Unit,
    ) : AbsUIState<MainPageAction>()

    class MainPageModel() : AbsUIModel<Unit, MainPageState, MainPageAction>() {
        private val entrys = UDTDatabase.getEntrys()
        private var currentEntry: UDTDatabase.DirTreeEntry? by mutableStateOf(entrys.firstOrNull())
        private var currentPath: String by mutableStateOf("")
        private val currentFiles = mutableStateListOf<UDTDatabase.FileRecord>()
        private val currentDirs = mutableStateListOf<UDTDatabase.DirRecord>()

        /**
         * 创建页面状态
         * 包含LaunchedEffect监听路径变化
         */
        @Composable
        override fun CreateState(params: Unit): MainPageState {
            LaunchedEffect(currentEntry, currentPath) {
                ologger.debug { "ReloadData: $currentPath" }
                val entry = currentEntry
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
                        source.onSuccess {
                            WorkStacker.putWork("正在复制从${it.absolutePath}到${target.absolutePath}") {
                                try {
                                    it.copyTo(target)
//                                toast.applyShow(ToastModel("复制完成",  type = ToastModel.Type.Info))
                                    ologger.info { "复制完成" }
                                } catch (e: Exception) {
//                                toast.applyShow(ToastModel("复制过程中失败失败: ${e.message}",  type = ToastModel.Type.Error))
                                    ologger.error(e) { "复制过程中失败失败: ${e.message}" }
                                }
                            }
                        }
                        source.onFailure {
//                            toast.applyShow(ToastModel("复制失败",  type = ToastModel.Type.Error))
                            ologger.error(it) { "复制失败" }
                        }
                    }
                }
            }
        }
    }

    /**
     * 页面动作密封类
     * 包含路径切换、进入目录、返回目录等操作
     */
    sealed class MainPageAction : AbsUIAction() {
        data class SwitchPath(val path: String) : MainPageAction()
        data class IntoDirectory(val dirName: String) : MainPageAction()
        data object BackDirectory : MainPageAction()
        data class SendFileTo(val file: UDTDatabase.FileRecord) : MainPageAction()
        data class SendFileToDesktop(val file: UDTDatabase.FileRecord) : MainPageAction()
        data class DeleteAndBanFile(val file: UDTDatabase.FileRecord) : MainPageAction()
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
    entrys: List<UDTDatabase.DirTreeEntry>,
    currentEntry: UDTDatabase.DirTreeEntry?,
    currentPath: String,
    currentFiles: List<UDTDatabase.FileRecord>,
    currentDirs: List<UDTDatabase.DirRecord>,
    canBack: Boolean,
    intoDirectory: (String) -> Unit,
    backDirectory: () -> Unit,
    sendFileTo: (UDTDatabase.FileRecord) -> Unit,
    sendFileToDesktop: (UDTDatabase.FileRecord) -> Unit,
    deleteAndBanFile: (UDTDatabase.FileRecord) -> Unit
) {
    // 添加文件详情弹窗状态
    var selectedFile by remember { mutableStateOf<UDTDatabase.FileRecord?>(null) }
    val showFileDetail = selectedFile != null

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
                // 顶部添加圆角Row布局
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "数据源",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                        items(entrys, key = { it.id }) { entry ->
                            val isSelected = currentEntry?.id == entry.id
                            Card(
                                onClick = {
                                    // 点击根目录时的处理逻辑
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
        // 添加文件详情弹窗
        if (showFileDetail) {
            AlertDialog(
                onDismissRequest = { selectedFile = null },
                title = { Text("文件详情") },
                text = {
                    selectedFile?.let { file ->
                        Column {
                            Text("文件名: ${file.fileName}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("大小: ${storage(file.size)}", style = MaterialTheme.typography.bodyMedium)
                            Text("路径: ${file.relationFilePath}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                // 导出文件逻辑
                                selectedFile?.let {
                                    sendFileTo(it)
                                }
                                selectedFile = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("导出")
                        }
                        Button(
                            onClick = {
                                // 发送到桌面逻辑
                                selectedFile?.let {
                                    sendFileToDesktop(it)
                                }
                                selectedFile = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("发送到桌面")
                        }
                        var lastClickTime by remember(selectedFile) { mutableStateOf(0L) }
                        Button(
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastClickTime < 300) { // 双击检测
                                    selectedFile?.let { file ->
                                        // 删除文件记录
                                        deleteAndBanFile(file)
                                        // 关闭对话框
                                        selectedFile = null
                                    }
                                    lastClickTime = 0
                                } else {
                                    // 记录单击时间
                                    lastClickTime = currentTime
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        ) {
                            Text("删除并排除(双击)", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
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
                        // 保留返回按钮
                        AnimatedVisibility(canBack) {
                            IconButton(onClick = backDirectory) {
                                Icon(TablerIcons.ArrowBack, contentDescription = "返回上一级")
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
                                        click = { intoDirectory(dir.dirName) }
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
            }
        }
    }
}

@Composable
fun DelayShowAnimationFromTopLeft(
    delay: Long = 10,
    animationTime: Int = 500,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 透明度动画
    val alpha = remember { Animatable(0.1f) }
    // 位移动画（左上角外偏移）
    val offset = remember { Animatable(-50f) }

    LaunchedEffect(Unit) {
        delay(delay)
        // 同时启动透明度与位移动画
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .offset(x = offset.value.dp, y = offset.value.dp)
    ) {
        content()
    }
}
