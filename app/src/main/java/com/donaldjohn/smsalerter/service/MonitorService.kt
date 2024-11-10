package  com.donaldjohn.smsalerter.service
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.donaldjohn.smsalerter.R
import com.donaldjohn.smsalerter.service.MonitorService.Companion.NOTIFICATION_ID

class MonitorService : Service() {
    private val CHANNEL_ID = "SmsMonitorChannel"
    companion object {
        const val NOTIFICATION_ID = 1
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            startForeground(
                NOTIFICATION_ID, 
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信监控服务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "用于显示短信监控服务的通知"
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    public fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("短信监控服务运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ForegroundServiceType")
    private fun keepAliveForMiui() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 提高服务优先级
            try {
                startForeground(NOTIFICATION_ID, createNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

                // 启动双进程保活
                val intent = Intent(this, KeepAliveService::class.java)
                startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        keepAliveForMiui()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null
}

// 创建双进程保活服务
class KeepAliveService : Service() {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        // 确保在使用 Context 之前进行空值检查
        if (applicationContext != null) {
            startForeground(NOTIFICATION_ID, createNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    private fun createNotification(): Notification {

        // 使用 applicationContext 而不是 this
        val context = applicationContext
        
        val channelId = "keep_alive_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "保活服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("SMS提醒服务")
            .setContentText("服务正在运行中...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 