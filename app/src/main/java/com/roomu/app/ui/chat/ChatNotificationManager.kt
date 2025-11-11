package com.roomu.app.ui.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.R
import kotlinx.coroutines.tasks.await

object ChatNotificationManager {
    private const val CHANNEL_ID = "chat_notifications"
    private const val CHANNEL_NAME = "Notificaciones de Chat"
    private const val TAG = "ChatNotifications"

    // ✅ NUEVO: Set para rastrear notificaciones ya mostradas
    private val shownNotifications = mutableSetOf<String>()

    /**
     * Crear canal de notificaciones (solo una vez)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Recibe notificaciones cuando llegan nuevos mensajes"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * ✅ MEJORADA: Mostrar notificación sin duplicados
     */
    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
        otherUserId: String,
        messageId: String = "" // ✅ NUEVO: ID único del mensaje
    ) {
        // ✅ NUEVO: Crear clave única para evitar duplicados
        val notificationKey = "$chatId-$messageId"

        // ✅ Si ya se mostró esta notificación, no mostrar de nuevo
        if (shownNotifications.contains(notificationKey)) {
            android.util.Log.d(TAG, "⏭️ Notificación ya mostrada: $notificationKey")
            return
        }

        // ✅ Marcar como mostrada
        shownNotifications.add(notificationKey)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent para abrir el chat cuando se toca la notificación
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("renterId", otherUserId)
            putExtra("renterName", senderName)
            putExtra("roomId", "")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_profile_placeholder)
            .setContentTitle(senderName)
            .setContentText(messageText.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500))
            .setLights(0xFF5252, 1000, 1000)
            .build()

        // ✅ Usar messageId como ID único de notificación
        val notificationId = messageId.hashCode()
        notificationManager.notify(notificationId, notification)

        android.util.Log.d(TAG, "✅ Notificación mostrada: $senderName - $messageText")
    }

    /**
     * ✅ NUEVO: Limpiar notificaciones cuando se abre el chat
     */
    fun clearNotificationsForChat(context: Context, chatId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Limpiar solo las notificaciones de este chat
        shownNotifications.removeAll { it.startsWith("$chatId-") }

        android.util.Log.d(TAG, "🗑️ Notificaciones limpias para chat: $chatId")
    }

    /**
     * ✅ NUEVO: Obtener todas las notificaciones guardadas
     */
    fun getShowedNotifications(): Set<String> {
        return shownNotifications.toSet()
    }

    /**
     * Marcar todos los mensajes como leídos en un chat
     */
    suspend fun markChatAsRead(chatId: String, userId: String) {
        try {
            FirebaseFirestore.getInstance()
                .document("chats/$chatId")
                .update("unreadCount.$userId", 0)
                .await()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error marcando como leído: ${e.message}")
        }
    }

    /**
     * Marcar como NO leído cuando otro usuario envía mensaje
     */
    suspend fun markChatAsUnread(chatId: String, userId: String) {
        try {
            FirebaseFirestore.getInstance()
                .document("chats/$chatId")
                .update("unreadCount.$userId", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
            android.util.Log.d(TAG, "✅ Chat marcado como NO leído para $userId")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error marcando como no leído: ${e.message}")
        }
    }

    /**
     * Obtener cantidad de mensajes no leídos totales
     */
    suspend fun getTotalUnreadCount(userId: String): Int {
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("chats")
                .whereArrayContains("participants", userId)
                .get()
                .await()

            var totalUnread = 0
            snapshot.documents.forEach { doc ->
                val unreadCountMap = doc.get("unreadCount") as? Map<String, Any>
                val unreadCount = (unreadCountMap?.get(userId) as? Number)?.toInt() ?: 0
                totalUnread += unreadCount
            }

            totalUnread
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo total no leídos: ${e.message}")
            0
        }
    }
}