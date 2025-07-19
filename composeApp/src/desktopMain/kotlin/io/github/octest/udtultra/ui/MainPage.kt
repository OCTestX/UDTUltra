package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
import compose.icons.tablericons.File
import compose.icons.tablericons.Folder
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.core.AbsUIPage
import io.klogging.noCoLogger
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
            intoDirectory = {
                state.action(MainPageAction.IntoDirectory(it))
            },
            backDirectory = {
                state.action(MainPageAction.BackDirectory)
            })
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
                entrys, currentEntry, currentPath, currentFiles, currentDirs,
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
                is MainPageAction.SwitchPath -> {
                    // 处理路径切换，标准化路径格式
                    val t1 = if (action.path.startsWith(File.separator))
                        action.path.removePrefix(File.separator) else action.path
                    currentPath = if (t1.endsWith(File.separator)) {
                        t1.removeSuffix(File.separator)
                    } else t1
                }
                is MainPageAction.IntoDirectory -> {
                    // 进入子目录：拼接新路径
                    actionExecute(params, MainPageAction.SwitchPath(currentPath + File.separator + action.dirName))
                }
                is MainPageAction.BackDirectory -> {
                    // 返回上一级目录：移除当前路径最后一段
                    actionExecute(
                        params,
                        MainPageAction.SwitchPath(currentPath.removeSuffix(currentPath.split(File.separator).last()))
                    )
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
@Preview
@Composable
fun FileBrowserUI(
    entrys: List<UDTDatabase.DirTreeEntry>,
    currentEntry: UDTDatabase.DirTreeEntry?,
    currentPath: String,
    currentFiles: List<UDTDatabase.FileRecord>,
    currentDirs: List<UDTDatabase.DirRecord>,
    intoDirectory: (String) -> Unit,
    backDirectory: () -> Unit
) {
    Row {
        // 左侧目录树优化：添加边框和悬停效果
        LazyColumn(
            Modifier
                .width(240.dp)
                .padding(8.dp)
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

        // 右侧主内容区
        Column(Modifier.weight(1f)) {
            // 路径导航栏优化
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    // 返回按钮增强
                    IconButton(
                        onClick = backDirectory,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            TablerIcons.ArrowBack,
                            contentDescription = "返回上一级",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 路径文本优化
                    Text(
                        text = currentPath,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alignByBaseline(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // 文件/目录列表优化
            LazyColumn(
                Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // 文件项列表
                items(currentFiles, key = { it.relationFilePath }) {
                    FileItemUI(it)
                }

                // 目录项列表
                items(currentDirs, key = { it.relationDirPath }) { dir ->
                    DirItemUI(dir) {
                        intoDirectory(dir.dirName)
                    }
                }
            }
        }
    }
}

// 文件项UI优化：添加图标和悬停效果
@Preview
@Composable
fun FileItemUI(file: UDTDatabase.FileRecord) {
    Card(
        onClick = { /* 文件点击事件处理（待实现） */ },
        Modifier
            .padding(2.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TablerIcons.File,
                contentDescription = "文件图标",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    storage(file.size),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// 目录项UI优化：添加图标和交互反馈
@Preview
@Composable
fun DirItemUI(dir: UDTDatabase.DirRecord, click: () -> Unit) {
    Card(
        onClick = click,
        Modifier
            .padding(2.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TablerIcons.Folder,
                contentDescription = "目录图标",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                dir.dirName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}