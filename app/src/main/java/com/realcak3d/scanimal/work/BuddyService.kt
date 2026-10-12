package com.realcak3d.scanimal.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.realcak3d.scanimal.MainActivity
import com.realcak3d.scanimal.R
import com.realcak3d.scanimal.core.C
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.BluetoothRadar
import com.realcak3d.scanimal.sensors.BodySensors
import com.realcak3d.scanimal.sensors.DistanceTracker
import com.realcak3d.scanimal.sensors.WifiRadar
import com.realcak3d.scanimal.sensors.granted

/**
 * Optional "background mode": keeps counting distance and steps, and sniffs Wi-Fi/Bluetooth every few
 * minutes while the app is closed. Shows a persistent notification, as Android requires.
 */
class BuddyService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: GameRepository
    private var lm: LocationManager? = null
    private var body: BodySensors? = null
    private var wifi: WifiRadar? = null
    private var bt: BluetoothRadar? = null

    private val listener = LocationListener { loc -> DistanceTracker.feed(repo, loc) }

    private val sniff = object : Runnable {
        override fun run() {
            // Short radio bursts every 4 minutes keep battery use low.
            wifi?.start(); bt?.start()
            handler.postDelayed({ wifi?.stop(); bt?.stop() }, 20_000)
            repo.act { now -> tick(now) }
            updateNotification()
            handler.postDelayed(this, 4 * 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = GameRepository.get(this)
        createChannel(this)
        var types = 0
        if (Build.VERSION.SDK_INT >= 29) {
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT >= 31 && granted(Manifest.permission.BLUETOOTH_SCAN)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), types)
        } catch (e: Exception) {
            stopSelf(); return
        }
        startTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 10f, listener, Looper.getMainLooper()) }
        }
        body = BodySensors(this).also { b ->
            b.onSteps = { n -> DistanceTracker.addSteps(n); repo.act { now -> onSteps(n, now) } }
            b.onCharging = { repo.act { now -> onCharging(now) } }
            b.startStepsOnly()
        }
        wifi = WifiRadar(this).also { w -> w.onBatch = { list -> repo.act { now -> onWifiScan(list, now) } } }
        bt = BluetoothRadar(this).also { r -> r.onBatch = { list -> repo.act { now -> onBluetooth(list, now) } } }
        handler.postDelayed(sniff, 5_000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { lm?.removeUpdates(listener) }
        body?.stop(); wifi?.stop(); bt?.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val g = repo.data
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BuddyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val dist = repo.engine.fmtDist(g.count(C.DISTANCE_M))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${g.buddy.name.ifBlank { "Your Scanimal" }} is exploring with you")
            .setContentText("$dist traveled · ${g.devices.size} devices logged · ${g.count(C.STEPS)} steps")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        runCatching { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification()) }
    }

    companion object {
        const val CHANNEL = "explore"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "stop"

        fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background exploring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while your buddy tracks walks and signals in the background"
            })
        }

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, BuddyService::class.java)) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, BuddyService::class.java)) }
        }
    }
}
