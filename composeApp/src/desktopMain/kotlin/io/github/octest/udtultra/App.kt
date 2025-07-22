package io.github.octest.udtultra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.octest.udtultra.ui.pages.FileBrowserPageMVIBackend
import io.github.octest.udtultra.ui.pages.FileBrowserUI
import io.klogging.noCoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import moe.tlaster.precompose.navigation.NavHost
import moe.tlaster.precompose.navigation.path
import moe.tlaster.precompose.navigation.rememberNavigator
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    remember {
        noCoLogger("KRecallRouter")
    }
    remember { CoroutineScope(Dispatchers.IO) }
    val navigator = rememberNavigator()
    NavHost(
        navigator,
        initialRoute = "/fileBrowser",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        scene("/fileBrowser") {
            val backend = remember {
                FileBrowserPageMVIBackend(jumpToUDiskEditor = {
                    navigator.navigate("/UDiskEditor/$it")
                })
            }
            FileBrowserUI(backend, backend.CurrentState())
        }
        scene("/UDiskEditor/{UDiskId}") { backStackEntry ->
            backStackEntry.path<String>("UDiskId")!!
        }
    }
}

@Serializable
object KRecallRouterIndex {
    @Serializable
    data object StartUp

    @Serializable
    data object Welcome

    @Serializable
    data object CheckPlugins

    @Serializable
    data object Main
}