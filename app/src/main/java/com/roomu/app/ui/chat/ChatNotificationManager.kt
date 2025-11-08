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
import com.roomu.app.ui.home.MainActivity
import kotlinx.coroutines.tasks.await

object ChatNotificationManager {
    private const val CHANNEL_ID = "chat_notifications"
    private const val CHANNEL_NAME = "Notificaciones de Chat"
    private const val TAG = "ChatNotifications"

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
     * Mostrar notificación cuando llega un nuevo mensaje
     */
    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
        otherUserId: String
    ) {
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
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_profile_placeholder) // Cambiar por tu icono
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500))
            .setLights(0xFF5252, 1000, 1000)
            .build()

        notificationManager.notify(chatId.hashCode(), notification)
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
     * Incrementar contador de no leídos cuando llega un mensaje
     */
    suspend fun incrementUnreadCount(chatId: String, userId: String) {
        try {
            FirebaseFirestore.getInstance()
                .document("chats/$chatId")
                .update("unreadCount.$userId", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error incrementando no leídos: ${e.message}")
        }
    }

    /**
     * ✅ NUEVO: Marcar como NO leído cuando otro usuario envía mensaje
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

/**
 * Extensión para usar en ChatActivity
 */
// ✅ ELIMINADA - No es necesaria


/**
 * Actualizar el contador de no leídos en tiempo real
 */
fun updateUnreadBadge(context: Context, chatId: String, unreadCount: Int) {
    // Esto se llama desde el adapter cuando actualiza datos
    if (unreadCount > 0) {
        // Mostrar badge en el ícono de la app (opcional)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Aquí puedes actualizar el ícono de la app con el contador
    }
}