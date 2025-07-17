package io.github.octest.udtultra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.octest.udtultra.repository.UDTDatabase
import kotlinx.coroutines.runBlocking

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "UDTUltra",
        ) {
            App()
        }
    }
}