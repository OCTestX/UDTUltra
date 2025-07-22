package io.github.octest.udtultra.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.github.octestx.basic.multiplatform.ui.ui.utils.MVIBackend
import io.klogging.noCoLogger
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.text.DecimalFormat

/**
 * 主页面对象，继承自AbsUIPage
 * 管理文件浏览相关状态和UI展示
 */
/**
 * 文件浏览器UI组件
 * 展示目录树和文件列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun UDiskEditorUI(
    backend: UDiskEditorPageMVIBackend,
    state: UDiskEditorPageMVIBackend.UDiskEditorPageIntentState,
) {
    val usedSpace = state.totalSpace - state.freeSpace
    val progress = if (state.totalSpace > 0) usedSpace.toFloat() / state.totalSpace else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row {
            IconButton(onClick = {
                backend.emitIntent(UDiskEditorPageMVIBackend.UDiskEditorPageEvent.Back)
            }) {
                Icon(TablerIcons.ArrowBack, contentDescription = null)
            }
            // U盘名称
            Text(
                text = "U盘名称: ${state.name}",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        // 空间信息
        SpaceInfoItem("总空间", storage(state.totalSpace))
        SpaceInfoItem("已用空间", storage(usedSpace))
        SpaceInfoItem("可用空间", storage(state.freeSpace))

        // 进度条
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = ProgressIndicatorDefaults.linearColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )

        // 空间比例文本
        Text(
            text = "已用 ${DecimalFormat("#.##").format(progress * 100)}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // U盘类型选择器
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        Text(
            text = "U盘类型:",
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            UDiskEntry.Companion.Type.entries.forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        backend.emitIntent(
                            UDiskEditorPageMVIBackend.UDiskEditorPageEvent.ChangeType(type)
                        )
                    }
                ) {
                    RadioButton(
                        selected = state.currentType == type.value,
                        onClick = {
                            backend.emitIntent(
                                UDiskEditorPageMVIBackend.UDiskEditorPageEvent.ChangeType(type)
                            )
                        }
                    )
                    Text(
                        text = type.value,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// 空间信息展示组件
@Composable
private fun SpaceInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

class UDiskEditorPageMVIBackend(private val entry: UDiskEntry, private val back: () -> Unit) :
    MVIBackend<UDiskEditorPageMVIBackend.UDiskEditorPageIntentState, UDiskEditorPageMVIBackend.UDiskEditorPageEvent>() {
    private val ologger = noCoLogger<UDiskEditorPageMVIBackend>()
    private var _currentName by mutableStateOf(entry.name)
    private var _currentType by mutableStateOf(entry.type)

    /**
     * 事件处理器
     * 处理路径切换、进入目录、返回目录等操作
     */
    override suspend fun processIntent(event: UDiskEditorPageEvent) {
        when (event) {
            UDiskEditorPageEvent.Back -> back()
            is UDiskEditorPageEvent.ChangeName -> {
                // 新增：处理名称变更事件
                UDTDatabase.changeUDiskName(entry, event.newName)
                _currentName = event.newName
            }

            is UDiskEditorPageEvent.ChangeType -> {
                // 新增：处理类型变更事件
                UDTDatabase.changeUDiskType(entry, event.newType)
                _currentType = event.newType.value
            }
        }

    }


    /**
     * 创建页面状态
     * 包含LaunchedEffect监听路径变化
     */
    @Composable
    override fun CalculateState(): UDiskEditorPageIntentState {
        LaunchedEffect(Unit) {

        }
        return UDiskEditorPageIntentState(
            _currentName,
            entry.totalSpace,
            entry.freeSpace,
            _currentType,
        )

    }

    /**
     * 页面状态类
     */
    data class UDiskEditorPageIntentState(
//        val entry: UDiskEntry,
        val name: String,
        val totalSpace: Long,
        val freeSpace: Long,
        val currentType: String
    ) : IntentState()

    /**
     * 页面动作密封类
     * 包含路径切换、进入目录、返回目录等操作
     */
    sealed class UDiskEditorPageEvent : IntentEvent() {
        data object Back : UDiskEditorPageEvent()
        data class ChangeName(val newName: String) : UDiskEditorPageEvent()
        data class ChangeType(val newType: UDiskEntry.Companion.Type) : UDiskEditorPageEvent()
    }
}