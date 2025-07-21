package io.github.octest.udtultra.repository

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.russhwolf.settings.Settings
import io.github.octest.udtultra.utils.DebounceExecution
import io.github.octestx.basic.multiplatform.common.utils.mb
import io.klogging.noCoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object SettingRepository {
    private val ologger = noCoLogger<SettingRepository>()
    private val ioscope = CoroutineScope(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.Main)
    val settings: Settings = Settings()


    //< ======================================================>//
    private val _daemonSwitch = mutableStateOf(false)
    val daemonSwitch: State<Boolean> = _daemonSwitch

    private val _copySpeed = mutableLongStateOf(5.mb)
    val copySpeed: State<Long> = _copySpeed
    //< ======================================================>//

    fun init() {
        _daemonSwitch.value = settings.getBoolean("daemonSwitch", true)
        _copySpeed.value = settings.getLong("copySpeed", 5.mb)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun switchDaemonStatus(enable: Boolean) {
        scope.launch {
            _daemonSwitch.value = enable
            with(Dispatchers.IO) {
                settings.putBoolean("daemonSwitch", daemonSwitch.value)
            }
        }
    }

    suspend fun changeCopySpeed(speed: Long) {
        with(Dispatchers.Main) {
            _copySpeed.value = speed
        }
        DebounceExecution.debounceExecution("changeCopySpeed") {
            ologger.info { "SaveChangeCopySpeed" }
            settings.putLong("copySpeed", copySpeed.value)
        }
    }
}