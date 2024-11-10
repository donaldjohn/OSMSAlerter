package com.donaldjohn.smsalerter.alert
import android.content.Context
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.donaldjohn.smsalerter.R

class AlertManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var camera: Camera? = null
    private var isAlerting = false
    private val handler = Handler(Looper.getMainLooper())

    fun startAlert() {
        if (isAlerting) return
        isAlerting = true
        
        // 播放警报声音
        playSound()
        // 开始震动
        startVibration()
        // 开始闪光
        startFlashing()
        

//        // 10秒后停止
//        handler.postDelayed({
//            stopAlert()
//            // 20秒后重新开始
//            handler.postDelayed({
//                if (isAlerting) startAlert()
//            }, 20000)
//        }, 10000)
    }

    private fun playSound() {
        try {
            // 添加详细的日志
            android.util.Log.d("AlertManager", "开始播放警报声音")
            
            val selectedSiren = R.raw.siren2

            mediaPlayer = MediaPlayer().apply {
                // 使用 setDataSource 替代 create
                setDataSource(context.resources.openRawResourceFd(selectedSiren))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                setVolume(1.0f, 1.0f)
                isLooping = true
                prepare() // 添加 prepare 调用
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("AlertManager", "播放自定义声音失败: ${e.message}", e)
            playDefaultAlarm()
        }
    }

    private fun playDefaultAlarm() {
        val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val actualAlarmUri = if (defaultAlarmUri == null)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else defaultAlarmUri

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, actualAlarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            setVolume(1.0f, 1.0f)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun startVibration() {
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            val pattern = longArrayOf(0, 1000, 500, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                it.vibrate(pattern, 0)
            }
        }
    }

    private fun startFlashing() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.firstOrNull { 
                val characteristics = cameraManager.getCameraCharacteristics(it)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }?.let { cameraId ->
                var isOn = false
                val handler = Handler(Looper.getMainLooper())
                
                val runnable = object : Runnable {
                    override fun run() {
                        if (isAlerting) try {
                            cameraManager.setTorchMode(cameraId, isOn)
                            isOn = !isOn
                            handler.postDelayed(this, 500)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                handler.post(runnable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlert() {
        isAlerting = false
        
        // 停止声音
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        
        // 确保正确停止震动
        vibrator?.cancel()
        vibrator = null
        
        // 停止闪光
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraManager.getCameraIdList().forEach { cameraId ->
                    cameraManager.setTorchMode(cameraId, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            camera?.apply {
                stopPreview()
                release()
            }
            camera = null
        }
        
        // 移除所有待执行的延迟任务
        handler.removeCallbacksAndMessages(null)
    }
}