package com.ravi.chargelock

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings

class ChargeLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!packageManager.isPackageSuspended && Settings.canDrawOverlays(this)) {
            runCatching {
                val intent = Intent(this, ChargeDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }
}
