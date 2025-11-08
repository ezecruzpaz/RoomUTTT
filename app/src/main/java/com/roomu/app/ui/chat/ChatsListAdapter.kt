package com.roomu.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.roomu.app.R
import com.roomu.app.databinding.ItemChatRenterBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatsListAdapter(
    private val chats: List<ChatItem>,
    private val onChatClick: (ChatItem) -> Unit
) : RecyclerView.Adapter<ChatsListAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(private val binding: ItemChatRenterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chatItem: ChatItem) {
            binding.apply {
                tvUserName.text = chatItem.otherUserName
                tvLastMessage.text = chatItem.lastMessage.ifEmpty { "Sin mensajes" }
                tvTime.text = formatTime(chatItem.lastMessageTime)

                // Mostrar badge de no leído
                if (chatItem.unreadCount > 0) {
                    badgeUnread.visibility = android.view.View.VISIBLE
                    badgeUnread.text = if (chatItem.unreadCount > 99) "99+" else chatItem.unreadCount.toString()
                } else {
                    badgeUnread.visibility = android.view.View.GONE
                }

                // Cargar avatar
                if (chatItem.otherUserPhoto.isNotEmpty()) {
                    Glide.with(binding.root.context)
                        .load(chatItem.otherUserPhoto)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(ivUserAvatar)
                } else {
                    ivUserAvatar.setImageResource(R.drawable.ic_profile_placeholder)
                }

                // Click listener
                root.setOnClickListener {
                    onChatClick(chatItem)
                }
            }
        }

        private fun formatTime(timestamp: Long): String {
            if (timestamp == 0L) return ""

            val date = Date(timestamp)
            val now = Date()
            val diff = now.time - date.time

            return when {
                diff < 60000 -> "Ahora"
                diff < 3600000 -> "${diff / 60000}m"
                diff < 86400000 -> "${diff / 3600000}h"
                diff < 604800000 -> {
                    val sdf = SimpleDateFormat("EEE", Locale("es"))
                    sdf.format(date)
                }
                else -> {
                    val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    sdf.format(date)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatRenterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size
}