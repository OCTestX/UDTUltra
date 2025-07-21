package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.octest.udtultra.repository.UDTDatabase

@Composable
fun DirInfoDialog(
    selectedDir: UDTDatabase.DirRecord,
    cleanSelectedDir: () -> Unit,
    sendDirTo: (UDTDatabase.DirRecord) -> Unit,
    sendDirToDesktop: (UDTDatabase.DirRecord) -> Unit,
    deleteAndBanDir: (UDTDatabase.DirRecord) -> Unit
) {
    // 添加文件详情弹窗
    AlertDialog(
        onDismissRequest = { cleanSelectedDir() },
        title = { Text("文件详情") },
        text = {
            Column {
                Text("文件名: ${selectedDir.dirName}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
//                Text("大小: ${storage(selectedDir.size)}", style = MaterialTheme.typography.bodyMedium)
                Text("路径: ${selectedDir.relationDirPath}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        // 导出文件逻辑
                        sendDirTo(selectedDir)
                        cleanSelectedDir()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("导出")
                }
                Button(
                    onClick = {
                        // 发送到桌面逻辑
                        sendDirToDesktop(selectedDir)
                        cleanSelectedDir()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("发送到桌面")
                }
                var lastClickTime by remember(selectedDir) { mutableStateOf(0L) }
                Button(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 300) { // 双击检测
                            // 删除文件记录
                            deleteAndBanDir(selectedDir)
                            // 关闭对话框
                            cleanSelectedDir()
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