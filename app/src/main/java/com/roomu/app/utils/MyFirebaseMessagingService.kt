package com.roomu.app.utils

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.roomu.app.R
import com.roomu.app.ui.chat.ChatActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "📨 Mensaje recibido desde Firebase")

        remoteMessage.data.let { data ->
            val title = data["title"] ?: "Nuevo mensaje"
            val body = data["body"] ?: ""
            val roomId = data["roomId"] ?: ""
            val renterId = data["renterId"] ?: ""
            val renterName = data["renterName"] ?: "Usuario"

            Log.d("FCM", "📦 Datos: title=$title, roomId=$roomId, renterId=$renterId")

            showNotification(title, body, roomId, renterId, renterName)
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        roomId: String,
        renterId: String,
        renterName: String
    ) {
        // ✅ Intent con los datos correctos para ChatActivity
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("roomId", roomId)
            putExtra("renterId", renterId)
            putExtra("renterName", renterName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            roomId.hashCode(), // ID único basado en roomId
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "chat_channel")
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(roomId.hashCode(), notification)

        Log.d("FCM", "✅ Notificación mostrada: $title")
    }
}