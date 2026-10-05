package ir.uciranx.ucg

import android.app.Application
import ir.uciranx.ucg.core.Notifications
import ir.uciranx.ucg.data.SettingsStore

class UcgApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        Notifications.createChannel(this)
    }
}
