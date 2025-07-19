package io.github.octest.udtultra.logic

import io.github.octest.udtultra.DirRecorder
import io.github.octest.udtultra.repository.UDTDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Daemon {
    private val scope = CoroutineScope(Dispatchers.IO)
    suspend fun start() {
        scope.launch {
            usbStorageListener { id, root ->
                scope.launch {
                    println("NewInsert: $id, $root")
                    DirRecorder(UDTDatabase.DirTreeEntry("NAME_$id", root, id, root.totalSpace, root.freeSpace))
                        .start()
                }
            }
        }
    }
}

//private fun main() {
//    runBlocking {
//        Daemon.start()
//        print("STARTED")
//        delay(Long.MAX_VALUE)
//    }
//}