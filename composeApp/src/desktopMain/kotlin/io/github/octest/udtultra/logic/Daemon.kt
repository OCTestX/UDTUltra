package io.github.octest.udtultra.logic

import io.github.octest.udtultra.repository.SettingRepository
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.github.octestx.basic.multiplatform.common.utils.sec
import io.klogging.noCoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * 守护进程对象，用于监听USB存储设备的插入事件，并根据设备类型执行相应的初始化操作。
 */
object Daemon {
    private val ologger = noCoLogger<Daemon>()
    private val ioscope = CoroutineScope(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * 启动守护进程，开始监听USB存储设备的插入事件。
     *
     * 该方法会在IO线程中启动一个协程来监听USB设备的插入事件。当检测到新设备插入时，
     * 会延迟10秒后检查设备根目录下是否存在特定标识文件（.udtUltraKeyUDisk 或 .udtUltraMasterUDisk），
     * 并根据这些文件的存在情况确定设备类型，然后启动目录记录器进行后续处理。
     *
     * 如果设置中的守护进程开关关闭，则跳过处理并记录日志。
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun start() {
        ioscope.launch {
            usbStorageListener(UDTDatabase.getEntrys().map { it.id }.toSet()) { event, id, root ->
                if (event == UsbEvent.INSERT) {
                    ologger.info { "NewInsert: $id, $root" }
                    scope.launch {
                        delay(10.sec)

                        val entry = UDTDatabase.getEntry(id)
                        val isKeyFile = File(root, ".udtUltraKeyUDisk")
                        val isMasterFile = File(root, ".udtUltraMasterUDisk")

                        // 根据数据库中的记录和实际文件存在情况更新或创建标识文件
                        if (entry != null) {
                            when(entry.type) {
                                UDiskEntry.Companion.Type.MASTER.value -> {
                                    isKeyFile.delete()
                                    if (isMasterFile.exists().not()) {
                                        isMasterFile.createNewFile()
                                    }
                                }
                                UDiskEntry.Companion.Type.KEY.value -> {
                                    if (isKeyFile.exists().not()) {
                                        isKeyFile.createNewFile()
                                    }
                                }
                            }
                        }

                        // 判断当前U盘的类型：KEY、MASTER 或者默认 COMMON
                        val type =
                            if (isKeyFile.exists() && isKeyFile.isFile && isKeyFile.isHidden) UDiskEntry.Companion.Type.KEY
                            else if (isMasterFile.exists() && isMasterFile.isFile && isMasterFile.isHidden) UDiskEntry.Companion.Type.MASTER
                            else UDiskEntry.Companion.Type.COMMON

                        // 如果守护进程开关开启，则启动目录记录器；否则记录日志并跳过
                        if (SettingRepository.daemonSwitch.value) {
                            val entry = UDiskEntry(
                                UDiskManager.getUDiskName(id, root),
                                root,
                                id,
                                root.totalSpace,
                                root.freeSpace,
                                // 如果u盘里没有文件能证明是key或者master，那么就按以前的记录来，如果以前没有记录（新u盘）则使用Common类型
                                type.value
                            )
                            DirRecorder(
                                entry
                            ).start()
                            if (type.value == UDiskEntry.Companion.Type.KEY.value) {
                                QuitCopper(entry).start()
                            }
                        } else {
                            ologger.info { "Daemon is disabled, so skip it" }
                        }
                    }
                }
            }
        }
    }
}
