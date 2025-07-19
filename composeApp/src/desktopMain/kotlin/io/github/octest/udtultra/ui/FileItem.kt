package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.File
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octestx.basic.multiplatform.common.utils.storage
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun FileItemUI(
    file: UDTDatabase.FileRecord,
    modifier: Modifier = Modifier // 新增modifier参数支持动画
) {
    Card(
        onClick = { /* 文件点击事件处理（待实现） */ },
        modifier = modifier // 使用传入的modifier
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