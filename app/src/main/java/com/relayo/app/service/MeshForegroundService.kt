package com.relayo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.relayo.app.MainActivity
import com.relayo.app.R
import com.relayo.core.mesh.MeshSessionManager
import com.relayo.domain.repository.MeshRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MeshForegroundService:Service() {

    @Inject lateinit var sessionManager:MeshSessionManager
    @Inject lateinit var meshRepository:MeshRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val CHANNEL_ID = "relayo_mesh_channel"
        private const val CHANNEL_NAME = "Relayo Mesh"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.relayo.action.START_MESH"
        const val ACTION_STOP = "com.relayo.action.STOP_MESH"

        fun start(context:Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply { action = ACTION_START }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context:Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply { action = ACTION_STOP }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sessionManager.startIfNeeded()

        serviceScope.launch {
            meshRepository.observeNearbyDevices().collectLatest { devices ->
                updateNotification(devices.size)
            }
        }
    }

    override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int {
        when(intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val notification = buildNotification(0)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Keep mesh alive as long as service is alive; stop discovery when service dies
        // sessionManager.stopIfNeeded() // uncomment to stop on destroy if desired
        super.onDestroy()
    }

    override fun onBind(intent:Intent?):IBinder? = null

    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Relayo mesh networking alive in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(peerCount:Int):Notification {
        val title = "Relayo mesh running"
        val content = if(peerCount == 0) "Searching for nearby peers..." else "$peerCount peer${if(peerCount==1) "" else "s"} nearby"
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(peerCount:Int) {
        val notification = buildNotification(peerCount)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
