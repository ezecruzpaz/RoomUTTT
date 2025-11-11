package com.roomu.app.ui.notifications

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.roomu.app.databinding.ActivityNotificationsBinding
import com.roomu.app.ui.chat.ChatNotificationManager

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter
    private val notificationsList = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadNotifications()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(notificationsList)
        binding.rvNotifications.apply {
            this.adapter = this@NotificationsActivity.adapter
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
        }
    }

    private fun loadNotifications() {
        // ✅ Obtener notificaciones mostradas
        val shownNotifications = ChatNotificationManager.getShowedNotifications()

        if (shownNotifications.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvNotifications.visibility = View.GONE
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.rvNotifications.visibility = View.VISIBLE

        // Convertir a NotificationItem para mostrar
        shownNotifications.forEach { notification ->
            val parts = notification.split("-")
            if (parts.size >= 2) {
                val notificationItem = NotificationItem(
                    id = notification,
                    chatId = parts[0],
                    messageId = parts.getOrNull(1) ?: "",
                    title = "Nuevo mensaje",
                    message = "Has recibido un mensaje",
                    timestamp = System.currentTimeMillis()
                )
                notificationsList.add(notificationItem)
            }
        }

        adapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        notificationsList.clear()
        loadNotifications()
    }
}

data class NotificationItem(
    val id: String,
    val chatId: String,
    val messageId: String,
    val title: String,
    val message: String,
    val timestamp: Long
)