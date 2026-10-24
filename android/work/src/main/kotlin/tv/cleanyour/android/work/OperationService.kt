package tv.cleanyour.android.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.cleanyour.core.flows.OperationNotice

// D18: a foreground service of type connectedDevice, started only for a run, install, push or
// backup longer than 5 s, and stopped within 1 s of its end. It holds nothing itself: the run lives
// in the engine's scope; the service only keeps the process in the foreground and shows "Setting up
// Mi TV 4A, 58%" or, at the check screen, "Look at the TV. Tap to answer." Not exported; the
// notification's PendingIntent is immutable and opens the launcher activity (SECURITY 4.2).
class OperationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started with startForegroundService must call startForeground before it stops,
        // even when the run ended before this ran (Android 9+ crashes the app otherwise); the
        // collector below then stops it at once on the null notice.
        val first = Bus.notice.value ?: Bus.last
        startInForeground(build(first))
        watch?.cancel()
        watch = scope.launch {
            Bus.notice.collect { n ->
                if (n == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(n))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun build(n: OperationNotice?): Notification {
        ensureChannel(this)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val pi = open?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(Bus.smallIcon)
            .setContentTitle(n?.text ?: Bus.channelName)
            .setOngoing(true)
            .setOnlyAlertOnce(n?.look != true)
            .setCategory(if (n?.look == true) Notification.CATEGORY_CALL else Notification.CATEGORY_PROGRESS)
            .setContentIntent(pi)
        n?.percent?.let { b.setProgress(100, it, false) }
        return b.build()
    }

    // The app publishes the notice of the active run here (RunWatcher in :app); null ends the service.
    object Bus {
        private val mutable = MutableStateFlow<OperationNotice?>(null)
        val notice: StateFlow<OperationNotice?> = mutable.asStateFlow()
        @Volatile var last: OperationNotice? = null
            private set
        var smallIcon: Int = android.R.drawable.stat_sys_upload
        var channelName: String = "Setting up a TV"

        fun publish(context: Context, notice: OperationNotice?) {
            val wasIdle = mutable.value == null
            mutable.value = notice
            if (notice != null) last = notice
            if (notice != null && wasIdle) {
                val i = Intent(context, OperationService::class.java)
                runCatching { context.startForegroundService(i) }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "setup"
        const val NOTIFICATION_ID = 58

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, Bus.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
            }
        }
    }
}
