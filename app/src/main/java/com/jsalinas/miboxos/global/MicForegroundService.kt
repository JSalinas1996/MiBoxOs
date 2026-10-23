package com.jsalinas.miboxos.global

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Desde Android 9 una app en segundo plano recibe silencio del micrófono. Mientras el asistente
 * global graba (encima de otra app) este servicio en primer plano mantiene el acceso al micrófono.
 * En Android TV la notificación no se muestra en pantalla.
 */
class MicForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Asistente", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Asistente escuchando")
            .build()
        startForeground(ID, notification)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "assistant"
        private const val ID = 1

        fun set(context: Context, recording: Boolean) {
            val intent = Intent(context, MicForegroundService::class.java)
            runCatching { if (recording) context.startForegroundService(intent) else context.stopService(intent) }
        }
    }
}
