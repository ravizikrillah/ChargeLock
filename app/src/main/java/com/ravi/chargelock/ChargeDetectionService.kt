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
                Intent.ACTION_POWER_DISCONNECTED -> {
                    wasCharging = false
                    stopLockActivity(context)
                    sendWakeToBatteryLock(context)
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
            addAction("com.ravi.chargelock.OPEN_LOCK")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        startForegroundService()
        
        // Check current status on start
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        
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
                "Charge Lock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Charge Lock Aktif")
            .setContentText("Memantau status pengisian daya...")
            .setSmallIcon(R.mipmap.ic_launcher)
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
        // To stop the activity, we can send a broadcast or use an event bus.
        // Or simply start it with a flag that tells it to finish, or just let it handle it.
        // Actually, a simple way is to send a broadcast that LockActivity listens to.
        val intent = Intent("com.ravi.chargelock.CLOSE_LOCK")
        context.sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
