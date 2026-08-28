package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.EaRobotEvent
import com.example.data.NotificationStateManager
import com.example.data.getResolvedGid

/**
 * Utilitário para envio e gerenciamento de notificações do sistema com suporte a GID.
 */
object EaNotificationHelper {

    private const val CHANNEL_ID = "ea_robot_alerts_channel"
    private const val CHANNEL_NAME = "Alertas e Notificações do Robô EA"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações em tempo real sobre eventos, execuções e alertas do robô EA no MT5"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Envia uma notificação do sistema para um evento do robô EA contendo seu GID exclusivo.
     */
    fun postEventNotification(context: Context, event: EaRobotEvent) {
        val gid = event.getResolvedGid()

        // Se já foi visualizado ou resolvido, não notifica
        if (NotificationStateManager.isViewed("notif_evt_$gid", gid)) {
            return
        }

        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notifId = (gid.hashCode() and 0x7FFFFFFF)

        // Intent para "Resolver" (abre o app diretamente no evento correspondente)
        val resolveIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EVENT_GID", gid)
            putExtra("ACTION_RESOLVE", true)
        }
        val resolvePendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            resolveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (event.resumo.isNotBlank()) event.resumo else "Robô EA: ${event.event}"
        val message = if (event.msg.isNotBlank()) event.msg else "${event.sistema} - ${event.novo}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("⚡ $title")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        if (event.data.isNotBlank() || event.hora.isNotBlank()) {
                            "$message\n⏰ ${event.data} ${event.hora}".trim()
                        } else {
                            message
                        }
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(resolvePendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Resolver 🎯",
                resolvePendingIntent
            )

        manager.notify(notifId, builder.build())
    }

    /**
     * Cancela a notificação de um determinado GID.
     */
    fun cancelEventNotification(context: Context, gid: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notifId = (gid.hashCode() and 0x7FFFFFFF)
        manager?.cancel(notifId)
    }
}
