package com.astrbot.control

import android.app.Application
import com.astrbot.control.data.ApiClient
import com.astrbot.control.data.SettingsStore
import com.astrbot.control.util.ConfigI18n
import com.astrbot.control.util.CrashReport

class AstrBotApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var api: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        ConfigI18n.init(this)
        settingsStore = SettingsStore(this)
        api = ApiClient(this, settingsStore)
    }
}
