package io.github.octest.udtultra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.octest.udtultra.ui.pages.MainPage
import io.klogging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    remember {
        logger("KRecallRouter")
    }
    val ioscope = remember { CoroutineScope(Dispatchers.IO) }
    val navigator = rememberNavController()
    NavHost(
        navigator,
        startDestination = "/main",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        composable("/main") {
            MainPage.Main(Unit)
        }
        composable("/personalDictionary") {
            LaunchedEffect(Unit) {
                PersonalDictionaryRepository.refreshAllWords()
            }
            val model = remember {
                PersonalDictionaryPage.PersonalDictionaryPageModel(
                    PersonalDictionaryRepository.allWords,
                    removeWord = {
                        ioscope.launch {
                            PersonalDictionaryRepository.removeWord(it)
                        }
                    },
                    {
                        navigator.popBackStack()
                    },
                    {
                        navigator.navigate("/wordInfo/$it")
                    }
                )
            }
            val page = remember { PersonalDictionaryPage(model) }
            page.Main(Unit)
        }
        composable("/wordInfo/{word}") {
            val word = it.arguments?.getString("word")
            if (word == null) {
                LaunchedEffect(Unit) {
                    toast.showBlocking("Error: $word is null")
                    navigator.popBackStack()
                }
            } else {
                val model = remember {
                    WordInfoPage.WordInfoPageModel(word) {
                        navigator.popBackStack()
                    }
                }
                val page = remember { WordInfoPage(model) }
                page.Main(Unit)
            }
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