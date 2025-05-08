import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import ru.doomscroll.slayer.R


import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private val gestureDetector by lazy { createGestureDetector() }
    private var swipeCount = 0
    private val swipeThreshold = 10 // 10 свайпов = думскроллинг

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Settings.canDrawOverlays(this)) {
            setupOverlay()
            startForeground(1, createNotification())
        } else {
            stopSelf() // Останавливаем сервис если нет разрешения
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW)
    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(
            R.layout.overlay_layout,  // Создайте этот файл в res/layout/
            FrameLayout(this),
            false
        )

        overlayView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)
    }

    private fun createGestureDetector() = GestureDetector(
        this,
        object : GestureDetector.SimpleOnGestureListener() {
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null || e2 == null) return false

                val diffY = e2.y - e1.y
                if (abs(diffY) > 100) { // Вертикальный свайп
                    swipeCount++
                    if (swipeCount >= swipeThreshold) {
                        triggerDoomscrollAlert()
                        swipeCount = 0
                    }
                }
                return true
            }
        }
    )

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun triggerDoomscrollAlert() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(200)
        Toast.makeText(this, "Возможен думскроллинг!", Toast.LENGTH_SHORT).show()
    }

    private fun createNotification(): Notification {
        val channelId = "doomscroll_channel"

        val channel = NotificationChannel(
            channelId,
            "Детектор думскроллинга",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Детектор думскроллинга")
            .setContentText("Анализ свайпов...")
            .setSmallIcon(R.drawable.ic_notification)  // Создайте этот ресурс
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error removing view", e)
        }
    }
}