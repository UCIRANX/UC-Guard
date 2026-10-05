package ir.uciranx.ucg

import android.app.Application
import android.util.Log
import ir.uciranx.ucg.core.Notifications
import ir.uciranx.ucg.core.StateBus
import ir.uciranx.ucg.data.SettingsStore

class UcgApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StateBus.init(this)
        SettingsStore.init(this)
        Notifications.createChannel(this)

        // Keep the reason of any crash in the log file, so it can be read after reopening.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                StateBus.logNow("[ucg] CRASH in thread ${thread.name}:\n" + Log.getStackTraceString(error))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
