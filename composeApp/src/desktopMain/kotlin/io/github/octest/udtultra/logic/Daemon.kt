package io.github.octest.udtultra.logic

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.octest.udtultra.DirRecorder
import io.github.octest.udtultra.repository.SettingRepository
import io.github.octest.udtultra.repository.UDTDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object Daemon {
    private val ioscope = CoroutineScope(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _daemonSwitch = mutableStateOf(loadFromFile())
    val daemonSwitch: State<Boolean> = _daemonSwitch

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun start() {
        ioscope.launch {
            usbStorageListener { id, root ->
                scope.launch {
                    if (daemonSwitch.value) {
                        println("NewInsert: $id, $root")
                        DirRecorder(UDTDatabase.DirTreeEntry("NAME_$id", root, id, root.totalSpace, root.freeSpace))
                            .start()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun switch(enable: Boolean) {
        scope.launch {
            _daemonSwitch.value = enable
            saveToFile()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun saveToFile() {
        scope.launch {
            SettingRepository.settings.putBoolean("daemonSwitch", daemonSwitch.value)
        }
    }

    private fun loadFromFile(): Boolean {
        return SettingRepository.settings.getBoolean("daemonSwitch", true)
    }
}