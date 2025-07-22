package io.github.octest.udtultra.logic

import androidx.compose.runtime.mutableStateListOf
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.github.octestx.basic.multiplatform.common.utils.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UDiskManager {
    private val _currentUDisks: MutableList<UDiskEntry> = mutableStateListOf()
    val currentUDisks: List<UDiskEntry> = _currentUDisks
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            _currentUDisks.addAll(listUDisk())
            usbStorageListener { event, id, root ->
                if (event == UsbEvent.INSERT) {
                    val entry = UDTDatabase.getEntry(id)
                    if (currentUDisks.find { it.id == entry.id } == null) {
                        _currentUDisks.add(entry)
                    } else {
                        val index = _currentUDisks.indexOfFirst { it.id == entry.id }
                        _currentUDisks[index] = entry
                    }
                } else if (event == UsbEvent.REMOVE) {
                    val index = _currentUDisks.indexOfFirst { it.id == id }
                    _currentUDisks.removeAt(index)
                }
            }
        }
    }

    suspend fun listUDisk(): List<UDiskEntry> {
        return when (OS.currentOS) {
            OS.OperatingSystem.WIN -> {
                return listInWindowsUDrivers().map {
                    val entry = UDTDatabase.getEntry(it.key)
                    UDiskEntry(
                        name = entry.name,
                        target = it.value,
                        id = it.key,
                        totalSpace = it.value.totalSpace,
                        freeSpace = it.value.freeSpace,
                        type = entry.type
                    )
                }
            }

            OS.OperatingSystem.LINUX -> {
                listInLinuxUDrivers().map {
                    val entry = UDTDatabase.getEntry(it.key)
                    UDiskEntry(
                        name = entry.name,
                        target = it.value,
                        id = it.key,
                        totalSpace = it.value.totalSpace,
                        freeSpace = it.value.freeSpace,
                        type = entry.type
                    )
                }
            }

            else -> {
                throw UnsupportedOperationException("Not support this OS")
            }
        }
    }
}