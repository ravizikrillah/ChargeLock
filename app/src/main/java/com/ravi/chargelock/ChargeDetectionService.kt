package com.ravi.chargelock

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import com.ravi.chargelock.R
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChargeDetectionService : Service() {

    private var wasCharging = false

    private fun sendWakeToBatteryLock(context: Context) {
        val wakeBatteryLock = Intent("com.ravi.batterylock.WAKE_CHECK").apply {
            setPackage("com.ravi.batterylock")
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(wakeBatteryLock)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED,
                "com.ravi.chargelock.OPEN_LOCK" -> {
                    wasCharging = true
                    startLockActivity(context)
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (wasCharging) {
                        startLockActivity(context)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    wasCharging = false
                    stopLockActivity(context)
                    sendWakeToBatteryLock(context)
                }
                Intent.ACTION_LOCALE_CHANGED -> {
                    startForegroundService()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val nowCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0
                    if (nowCharging && !wasCharging) {
                        wasCharging = true
                        startLockActivity(context)
                    } else if (!nowCharging && wasCharging) {
                        wasCharging = false
                        stopLockActivity(context)
                        sendWakeToBatteryLock(context)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction("com.ravi.chargelock.OPEN_LOCK")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        startForegroundService()
        checkCurrentChargingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (packageManager.isPackageSuspended) {
            stopSelf()
            return START_NOT_STICKY
        }
        checkCurrentChargingState()
        return START_STICKY
    }

    private fun checkCurrentChargingState() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        wasCharging = isCharging
        if (isCharging) {
            startLockActivity(this)
        }
    }

    private fun startForegroundService() {
        val channelId = "charge_lock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_message))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startLockActivity(context: Context) {
        val intent = Intent(context, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    private fun stopLockActivity(context: Context) {
        val intent = Intent("com.ravi.chargelock.CLOSE_LOCK").setPackage(packageName)
        context.sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
