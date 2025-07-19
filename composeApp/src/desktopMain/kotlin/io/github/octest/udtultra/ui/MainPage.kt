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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.core.AbsUIPage
import io.klogging.noCoLogger
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File

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

    data class MainPageState(
        val entrys: List<UDTDatabase.DirTreeEntry>,
        val currentEntry: UDTDatabase.DirTreeEntry?,
        val currentPath: String, // 新增当前路径状态
        val currentFiles: List<UDTDatabase.FileRecord>,
        val currentDirs: List<UDTDatabase.DirRecord>,
        val action: (MainPageAction) -> Unit,
    ) : AbsUIState<MainPageAction>()

    class MainPageModel() : AbsUIModel<Unit, MainPageState, MainPageAction>() {
        private val entrys = UDTDatabase.getEntrys()
        private var currentEntry: UDTDatabase.DirTreeEntry? by mutableStateOf(entrys.firstOrNull())
        private var currentPath: String by mutableStateOf("") // 新增路径状态管理
        private val currentFiles = mutableStateListOf<UDTDatabase.FileRecord>()
        private val currentDirs = mutableStateListOf<UDTDatabase.DirRecord>()
        
        @Composable
        override fun CreateState(params: Unit): MainPageState {
            LaunchedEffect(currentEntry, currentPath) { // 监听路径变化
                ologger.debug { "ReloadData: $currentPath" }
                val entry = currentEntry
                if (entry != null) {
                    currentFiles.clear()
                    currentDirs.clear()
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

        override fun actionExecute(params: Unit, action: MainPageAction) {
            when (action) {
                is MainPageAction.SwitchPath -> {
                    // 切换路径
                    val t1 =
                        if (action.path.startsWith(File.separator)) action.path.removePrefix(File.separator) else action.path
                    currentPath = if (t1.endsWith(File.separator)) {
                        t1.removeSuffix(File.separator)
                    } else t1
                }
                is MainPageAction.IntoDirectory -> {
                    actionExecute(params, MainPageAction.SwitchPath(currentPath + File.separator + action.dirName))
                }

                is MainPageAction.BackDirectory -> {
                    actionExecute(
                        params,
                        MainPageAction.SwitchPath(currentPath.removeSuffix(currentPath.split(File.separator).last()))
                    )
                }
            }
        }
    }

    sealed class MainPageAction : AbsUIAction() {
        data class SwitchPath(val path: String) : MainPageAction()
        data class IntoDirectory(val dirName: String) : MainPageAction()
        data object BackDirectory : MainPageAction()
    }
}

@Preview
@Composable
fun DrawerUI() {
    LazyColumn(Modifier.padding(15.dp)) {
        item {
            Card {
                Column(Modifier.padding(8.dp)) {
                    Text("运行u盘小偷")
                    Switch(checked = true, onCheckedChange = {})
                }
            }
        }
        item {
            OutlinedButton(onClick = {}) {
                Text("白名单管理")
            }
        }
        item {
            OutlinedButton(onClick = {}) {
                Text("复制速度管理")
            }
        }
        item {
            OutlinedButton(onClick = {}) {
                Text("文件过滤管理")
            }
        }
        item {
            OutlinedButton(onClick = {}) {
                Text("MasterU盘管理")
            }
        }
    }
}

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
        LazyColumn {
            items(entrys, key = { it.id }) {
                Card(onClick = {

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
        Column(Modifier.weight(1f)) {
            Row {
                IconButton(onClick = {
                    backDirectory()
                }) {
                    Icon(TablerIcons.ArrowBack, contentDescription = null)
                }
                Text(currentPath)
            }
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

@Preview
@Composable
fun FileItemUI(file: UDTDatabase.FileRecord) {
    Card(onClick = {

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

private fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "UDTUltra",
        ) {
            MainPage.Main(Unit)
        }
    }
}