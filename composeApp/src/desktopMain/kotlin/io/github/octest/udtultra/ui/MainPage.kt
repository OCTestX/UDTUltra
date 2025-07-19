package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.core.AbsUIPage
import org.jetbrains.compose.ui.tooling.preview.Preview

object MainPage : AbsUIPage<Unit, MainPage.MainPageState, MainPage.MainPageAction>(MainPageModel()) {
    @Composable
    override fun UI(state: MainPageState) {
        FileBrowserUI(state.entrys, state.currentEntry, state.currentFiles, state.currentDirs)
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
                is MainPageAction.IntoDirectory -> {
//                    // 进入目录时更新路径
//                    currentPath = "${action.dir.relationDirPath}${File.separator}"
                }

                is MainPageAction.OutOfDirectory -> {
//                    // 返回上一级目录
//                    currentPath = currentPath.substringBeforeLast(File.separator).substringBeforeLast(File.separator)
                }
            }
        }
    }

    sealed class MainPageAction : AbsUIAction() {
        data class IntoDirectory(val dir: UDTDatabase.DirRecord) : MainPageAction()
        data object OutOfDirectory : MainPageAction()
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
    currentFiles: List<UDTDatabase.FileRecord>,
    currentDirs: List<UDTDatabase.DirRecord>
) {
    Row {
        LazyColumn {
            items(entrys) {
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
        LazyColumn(Modifier.weight(1f)) {
            items(currentFiles, key = { it.relationFilePath }) {
                FileItemUI(it)
            }
            items(currentDirs, key = { it.relationDirPath }) {
                DirItemUI(it)
            }
        }
    }
}

@Preview
@Composable
fun FileItemUI(file: UDTDatabase.FileRecord) {
    Card(onClick = {

    }, Modifier.padding(6.dp)) {
        Column(Modifier.padding(3.dp)) {
            Text(
                file.fileName,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            )
            Text(storage(file.size))
            Text(file.relationFilePath)
        }
    }
}

@Preview
@Composable
fun DirItemUI(dir: UDTDatabase.DirRecord) {
    Card(onClick = {

    }, Modifier.padding(6.dp)) {
        Column(Modifier.padding(3.dp)) {
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