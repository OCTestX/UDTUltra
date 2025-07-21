package io.github.octest.udtultra

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import io.github.octest.udtultra.repository.SettingRepository
import io.github.octestx.basic.multiplatform.common.BasicMultiplatformConfigModule
import io.github.octestx.basic.multiplatform.common.JVMInitCenter
import io.github.octestx.basic.multiplatform.ui.JVMUIInitCenter
import io.github.octestx.basic.multiplatform.ui.ui.BasicMUIWrapper
import org.koin.core.context.startKoin
import java.io.File

fun main() {
    startKoin {
        modules(
            BasicMultiplatformConfigModule().apply {
                configInnerAppDir(File("/home/octest/Desktop/UDTUltra/"))
            }.asModule()
        )
    }
    JVMInitCenter.init()
    SettingRepository.init()
    application {
        val trayState = rememberTrayState()
        LaunchedEffect(Unit) {
            JVMUIInitCenter.init(trayState)
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "UDTUltra",
        ) {
            BasicMUIWrapper {
                App()
            }
        }
    }
}