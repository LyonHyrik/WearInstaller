package com.lyon.wearinstaller

import android.app.Application
import com.lyon.wearinstaller.adb.AdbKeyStore
import com.lyon.wearinstaller.data.InstallCoordinator
import com.lyon.wearinstaller.data.SettingsRepository
import java.io.File

/** 进程级依赖容器：安装任务需要活得比界面久。 */
class AppContainer(application: Application) {
    val keyStore = AdbKeyStore(File(application.filesDir, "adb"))
    val settings = SettingsRepository(application)
    val coordinator = InstallCoordinator(application, settings, keyStore)
}

class WearInstallerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 提前准备密钥，保证第一次连接就能直接完成认证
        container.keyStore.ensure()
    }
}