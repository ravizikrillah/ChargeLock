package com.ravi.chargelock

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ravi.chargelock.R
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravi.chargelock.ui.theme.ChargeLockTheme

class LockActivity : ComponentActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.ravi.chargelock.CLOSE_LOCK") {
                goToHome()
            }
        }
    }

    private var batteryLevel = mutableIntStateOf(0)

    private var isCharging = true

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Cek level baterai
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                batteryLevel.intValue = (level * 100 / scale.toFloat()).toInt()
            }

            // Cek status charging (termasuk EXTRA_PLUGGED saat negosiasi arus awal)
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL ||
                         plugged != 0

            // Jika kabel dicabut saat activity sedang aktif, segera tutup dan ke Home
            if (!isCharging) {
                goToHome()
            }
        }
    }

    private fun goToHome() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
        finishAffinity()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Cek status awal saat dibuka
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0

        // Jika tidak sengaja terbuka padahal tidak dicas, langsung tutup
        if (!isCharging) {
            goToHome()
            return
        }
        
        // Make the activity show over lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val closeFilter = IntentFilter("com.ravi.chargelock.CLOSE_LOCK")
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, closeFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(closeReceiver, closeFilter)
            registerReceiver(batteryReceiver, batteryFilter)
        }
        
        enableEdgeToEdge()
        setContent {
            ChargeLockTheme {
                BackHandler {
                    // Do nothing, block back button
                }
                LockScreenContent(batteryLevel.intValue)
            }
        }
        
        // Hide status bar and navigation bar (Immersive Mode)
        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /*
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If user tries to leave, bring it back
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }
    */

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun bringBackToFront() {
        if (!isScreenInteractive() || !isCharging || isFinishing) return
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Terpanggil HANYA saat user secara aktif menekan tombol Home atau Recents
        // (Sistem Android tidak memanggil onUserLeaveHint saat layar dimatikan/sleep)
        if (isCharging && isScreenInteractive()) {
            bringBackToFront()
        }
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()
        // Biarkan lifecycle pause berjalan normal tanpa memaksakan activity kembali ke depan,
        // agar proses screen-off / sleep tidak dicegat.
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(closeReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
    }
}

@Composable
fun LockScreenContent(batteryLevel: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "charging")
    
    // Animasi untuk ikon petir
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash_alpha"
    )

    // Animasi untuk lingkaran progress
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circle_sweep"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F0F) // Hitam pekat modern
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Lingkaran Progres Baterai (Animasi)
                Canvas(modifier = Modifier.size(220.dp)) {
                    drawArc(
                        color = Color.DarkGray,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF00C853), Color(0xFFB2FF59), Color(0xFF00C853))
                        ),
                        startAngle = sweepAngle,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Charging",
                        tint = Color.Yellow.copy(alpha = alpha),
                        modifier = Modifier.size(60.dp)
                    )
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Card Pesan yang lebih menarik
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.lock_badge),
                        color = Color(0xFFB2FF59),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.lock_message),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )
                }
            }
        }
    }
}
