package io.github.octest.udtultra

import androidx.compose.ui.window.TrayState
import io.github.octest.udtultra.logic.Daemon
import io.github.octest.udtultra.repository.SettingRepository
import io.github.octestx.basic.multiplatform.common.BasicMultiplatformConfigModule
import io.github.octestx.basic.multiplatform.common.JVMInitCenter
import io.github.octestx.basic.multiplatform.common.exceptions.SingleInstanceException
import io.github.octestx.basic.multiplatform.common.utils.checkSelfIsSingleInstance
import io.github.octestx.basic.multiplatform.ui.JVMUIInitCenter
import io.klogging.noCoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import java.io.File

object Core {
    private val ologger = noCoLogger<Core>()
    private val ioscope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var initialized = false

    /**
     * 初始化核心模块
     * @param trayState 托盘状态
     * @exception SingleInstanceException 如果已经运行了一个实例
     * @exception InvalidKeyPluginException 关键插件没有集全
     */
    suspend fun init(trayState: TrayState): Result<Unit> {
        if (initialized) return Result.success(Unit)
        if (checkSelfIsSingleInstance().not()) {
            ologger.error(SingleInstanceException()) { "Already run one now!" }
            Result.failure(SingleInstanceException())
        } else {
            Result.success(Unit)
        }
        startKoin {
            modules(
                BasicMultiplatformConfigModule().apply {
                    configInnerAppDir(File("/home/octest/Desktop/UDTUltra/"))
                }.asModule()
            )
        }
        JVMInitCenter.init()
        JVMUIInitCenter.init(trayState)
        SettingRepository.init()
        Daemon.start()
        ologger.info { "INITIALIZED" }
        return Result.success(Unit)
    }
}