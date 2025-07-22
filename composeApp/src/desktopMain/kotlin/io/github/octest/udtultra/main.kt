package io.github.octest.udtultra

import androidx.compose.runtime.*
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import io.github.octestx.basic.multiplatform.common.utils.OS
import io.github.octestx.basic.multiplatform.ui.ui.BasicMUIWrapper
import org.jetbrains.compose.resources.painterResource
import udtultra.composeapp.generated.resources.Res
import udtultra.composeapp.generated.resources.icon

fun main() {
    application {
        val trayState = rememberTrayState()
        var windowVisible by remember {
            mutableStateOf(
                OS.currentOS == OS.OperatingSystem.LINUX
            )
        }
        Window(
            visible = windowVisible,
            onCloseRequest = {
                windowVisible = false
            },
            title = "UDTUltra",
        ) {
            BasicMUIWrapper {
                App()
            }
        }
        Tray(
            icon = painterResource(Res.drawable.icon),
            menu = {
                Item("Show", onClick = { windowVisible = true })
                Separator()
                Item("Exit", onClick = ::exitApplication)
            },
            onAction = {
                windowVisible = windowVisible.not()
            },
            tooltip = "KRecall",
            state = trayState
        )
        LaunchedEffect(Unit) {
            Core.init(trayState)
        }
    }
}