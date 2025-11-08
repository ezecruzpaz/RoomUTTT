package com.roomu.app.data.model

data class Chat(
    val chatId: String = "",
    val roomId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val unreadCount: Map<String, Int> = emptyMap()
)