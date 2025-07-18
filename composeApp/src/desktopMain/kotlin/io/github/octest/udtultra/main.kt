package io.github.octest.udtultra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
//    Daemon.start()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "UDTUltra",
        ) {
            App()
        }
    }
}