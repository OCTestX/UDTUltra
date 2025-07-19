package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
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
        // 左侧目录树
        LazyColumn {
            items(entrys, key = { it.id }) {
                Card(onClick = {
                    // 点击根目录时的处理逻辑
                }, Modifier.padding(6.dp)) {
                    Column(Modifier.padding(3.dp)) {
                        Text(
                            it.name,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text("总共${storage(it.totalSpace)}, 剩余${storage(it.freeSpace)}")
                    }
                }
            }
        }
        // 右侧文件列表
        Column(Modifier.weight(1f)) {
            Row {
                // 返回按钮和路径显示
                IconButton(onClick = {
                    backDirectory()
                }) {
                    Icon(TablerIcons.ArrowBack, contentDescription = null)
                }
                Text(currentPath)
            }
            // 文件和子目录列表
            LazyColumn(Modifier.weight(1f)) {
                items(currentFiles, key = { it.relationFilePath }) {
                    FileItemUI(it)
                }
                items(currentDirs, key = { it.relationDirPath }) {
                    DirItemUI(it) {
                        intoDirectory(it.dirName)
                    }
                }
            }
        }
    }
}

/**
 * 文件项UI组件
 * 展示单个文件信息
 */
@Preview
@Composable
fun FileItemUI(file: UDTDatabase.FileRecord) {
    Card(onClick = {
        // 文件点击事件处理（待实现）
    }, Modifier.padding(3.dp).fillMaxWidth()) {
        Column(Modifier.padding(1.dp)) {
            Text(
                file.fileName,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            )
            Text(storage(file.size))
        }
    }
}

/**
 * 目录项UI组件
 * 展示单个目录信息
 */
@Preview
@Composable
fun DirItemUI(dir: UDTDatabase.DirRecord, click: () -> Unit) {
    Card(onClick = {
        click()
    }, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(1.dp)) {
            Text(
                dir.dirName,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}