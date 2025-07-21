package io.github.octest.udtultra.logic

import io.github.octest.udtultra.DirRecorder
import io.github.octest.udtultra.repository.SettingRepository
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.DirTreeEntry
import io.github.octestx.basic.multiplatform.common.utils.sec
import io.klogging.noCoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object Daemon {
    private val ologger = noCoLogger<Daemon>()
    private val ioscope = CoroutineScope(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.Main)

    @OptIn(ExperimentalAtomicApi::class)
    fun start() {
        ioscope.launch {
            usbStorageListener(UDTDatabase.getEntrys().map { it.id }.toSet()) { event, id, root ->
                if (event == UsbEvent.INSERT) {
                    ologger.info { "NewInsert: $id, $root" }
                    scope.launch {
                        delay(10.sec)
                        if (SettingRepository.daemonSwitch.value) {
                            DirRecorder(DirTreeEntry("NAME_$id", root, id, root.totalSpace, root.freeSpace))
                                .start()
                        } else {
                            ologger.info { "Daemon is disabled, so skip it" }
                        }
                    }
                }
            }
        }
    }
}