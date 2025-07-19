package io.github.octest.udtultra.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Folder
import io.github.octest.udtultra.repository.UDTDatabase
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun DirItemUI(
    dir: UDTDatabase.DirRecord,
    modifier: Modifier = Modifier, // 新增modifier参数支持动画
    click: () -> Unit
) {
    Card(
        onClick = click,
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