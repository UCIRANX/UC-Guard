package ir.uciranx.ucg.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ir.uciranx.ucg.R
import ir.uciranx.ucg.ui.MainActivity

object Notifications {
    const val CHANNEL = "ucg_status"
    const val ID = 7

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "وضعیت اتصال", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            description = "نمایش وضعیت اتصال و دکمه‌های قطع و وصل"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun serviceAction(ctx: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            ctx, request,
            Intent(ctx, UcgVpnService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun build(ctx: Context, st: VpnState): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = serviceAction(ctx, UcgVpnService.ACTION_DISCONNECT, 1)

        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("UC Guard")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when (st) {
            is VpnState.Connecting -> {
                b.setContentText(st.stage)
                b.addAction(0, "قطع کامل", disconnect)
            }
            is VpnState.Connected -> {
                b.setContentText("متصل • ${st.label}")
                b.setWhen(st.since).setShowWhen(true).setUsesChronometer(true)
                b.addAction(0, "قطع موقت", serviceAction(ctx, UcgVpnService.ACTION_PAUSE, 2))
                b.addAction(0, "قطع کامل", disconnect)
            }
            is VpnState.Paused -> {
                b.setContentText("قطع موقت • ترافیک مستقیم و بدون فیلترشکن")
                b.addAction(0, "اتصال مجدد", serviceAction(ctx, UcgVpnService.ACTION_RESUME, 3))
                b.addAction(0, "قطع کامل", disconnect)
            }
            else -> b.setContentText("")
        }
        return b.build()
    }
}
