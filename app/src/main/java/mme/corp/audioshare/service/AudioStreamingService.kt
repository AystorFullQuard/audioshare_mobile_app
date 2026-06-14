package mme.corp.audioshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import mme.corp.audioshare.R
import mme.corp.audioshare.audio.SystemAudioStreamManager
import mme.corp.audioshare.audio.capture.SystemAudioCapture
import mme.corp.audioshare.audio.playback.AudioPlayer
import mme.corp.audioshare.network.udp.UdpAudioReceiver
import mme.corp.audioshare.network.udp.UdpAudioSender

class AudioStreamingService : Service() {

    private enum class ForegroundMode {
        SYSTEM_AUDIO,
        RECEIVER
    }

    companion object {
        private const val CHANNEL_ID = "audio_streaming"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START_SYSTEM_AUDIO =
            "mme.corp.audioshare.action.START_SYSTEM_AUDIO"

        private const val ACTION_START_RECEIVER =
            "mme.corp.audioshare.action.START_RECEIVER"

        private const val ACTION_STOP =
            "mme.corp.audioshare.action.STOP"

        private const val EXTRA_RECEIVER_IP = "receiver_ip"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_PROJECTION_DATA = "projection_data"

        fun startSystemAudio(
            context: Context,
            receiverIp: String,
            port: Int,
            resultCode: Int,
            projectionData: Intent
        ) {
            val intent = Intent(context, AudioStreamingService::class.java).apply {
                action = ACTION_START_SYSTEM_AUDIO
                putExtra(EXTRA_RECEIVER_IP, receiverIp)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, projectionData)
            }

            startForegroundOrNormalService(context, intent)
        }

        fun startReceiver(
            context: Context,
            port: Int
        ) {
            val intent = Intent(context, AudioStreamingService::class.java).apply {
                action = ACTION_START_RECEIVER
                putExtra(EXTRA_PORT, port)
            }

            startForegroundOrNormalService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioStreamingService::class.java).apply {
                action = ACTION_STOP
            }

            context.startService(intent)
        }

        private fun startForegroundOrNormalService(
            context: Context,
            intent: Intent
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var systemAudioStreamManager: SystemAudioStreamManager? = null
    private var receiver: UdpAudioReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START_SYSTEM_AUDIO -> handleStartSystemAudio(intent)
            ACTION_START_RECEIVER -> handleStartReceiver(intent)
            ACTION_STOP -> handleStop()
        }

        return START_NOT_STICKY
    }

    private fun handleStartSystemAudio(intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            stopSelf()
            return
        }

        startForegroundNotification(
            contentText = "System audio sharing is active",
            mode = ForegroundMode.SYSTEM_AUDIO
        )

        startSystemAudio(intent)
    }

    private fun handleStartReceiver(intent: Intent) {
        startForegroundNotification(
            contentText = "Receiving audio stream",
            mode = ForegroundMode.RECEIVER
        )

        startReceiver(intent)
    }

    private fun handleStop() {
        stopAll()
        stopSelf()
    }

    private fun startSystemAudio(intent: Intent) {
        val receiverIp = intent.getStringExtra(EXTRA_RECEIVER_IP)
            ?: return stopSelf()

        val port = intent.getIntExtra(EXTRA_PORT, -1)
        if (port <= 0) {
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projectionData = getProjectionData(intent)

        if (projectionData == null) {
            stopSelf()
            return
        }

        stopAll()
        startSystemAudioManager(receiverIp, port, resultCode, projectionData)
    }

    private fun getProjectionData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
    }

    private fun startSystemAudioManager(
        receiverIp: String,
        port: Int,
        resultCode: Int,
        projectionData: Intent
    ) {
        val sender = UdpAudioSender(
            hostIp = receiverIp,
            port = port
        )

        val capture = SystemAudioCapture(
            context = applicationContext,
            resultCode = resultCode,
            projectionData = projectionData
        )

        systemAudioStreamManager = SystemAudioStreamManager(
            capture = capture,
            sender = sender,
            frameSize = SystemAudioCapture.FRAME_SIZE_BYTES,
            onError = {
                stopAll()
                stopSelf()
            }
        )

        systemAudioStreamManager?.start()
    }

    private fun startReceiver(intent: Intent) {
        val port = intent.getIntExtra(EXTRA_PORT, -1)

        if (port <= 0) {
            stopSelf()
            return
        }

        stopAll()

        val player = AudioPlayer()

        receiver = UdpAudioReceiver(
            port = port,
            audioPlayer = player
        )

        receiver?.start()
    }

    private fun stopAll() {
        systemAudioStreamManager?.stop()
        receiver?.stop()

        systemAudioStreamManager = null
        receiver = null
    }

    private fun startForegroundNotification(
        contentText: String,
        mode: ForegroundMode
    ) {
        val notification = buildNotification(contentText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                foregroundServiceTypeFor(mode)
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun foregroundServiceTypeFor(mode: ForegroundMode): Int {
        return when (mode) {
            ForegroundMode.SYSTEM_AUDIO ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

            ForegroundMode.RECEIVER ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AudioShare is running")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio streaming",
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}