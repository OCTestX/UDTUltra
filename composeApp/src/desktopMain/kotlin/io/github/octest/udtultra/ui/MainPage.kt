package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
        TODO("Not yet implemented")
    }

    class MainPageModel() : AbsUIModel<Unit, MainPageState, MainPageAction>() {
        @Composable
        override fun CreateState(params: Unit): MainPageState {
            TODO("Not yet implemented")
        }

        override fun actionExecute(params: Unit, action: MainPageAction) {
            TODO("Not yet implemented")
        }

    }

    data class MainPageState(
        val text: String = "Hello World"
    ) : AbsUIState<MainPageAction>()

    sealed class MainPageAction : AbsUIAction() {
        object OnClick : MainPageAction()
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
fun FileBrowserUI(entrys: List<UDTDatabase.DirTreeEntry>) {
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
//            FileBrowserUI(
//                listOf(
//                    UDTDatabase.DirTreeEntry(
//                        name = "U盘1",
//                        target = File("D:\\"),
//                        id = "1",
//                        totalSpace = 1000000000000,
//                        freeSpace = 500000000000
//                    ),
//                    UDTDatabase.DirTreeEntry(
//                        name = "U盘2",
//                        target = File("D:\\"),
//                        id = "2",
//                        totalSpace = 31000000000000,
//                        freeSpace = 500000000000
//                    )
//                )
//            )
            FileItemUI(
                UDTDatabase.FileRecord(
                    entryId = "1",
                    filePath = "D:\\1.txt",
                    fileName = "1.txt",
                    size = 1000000000000,
                    createDate = 1000000000000,
                    modifierData = 1000000000000,
                    status = 0
                )
            )
        }
    }
}