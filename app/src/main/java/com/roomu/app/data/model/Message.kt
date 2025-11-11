package com.roomu.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.*

data class Message(
    val messageId: String = "",
    val text: String = "",
    val senderId: String = "",
    @ServerTimestamp
    val timestamp: Date? = null  // ← AÑADIR ESTO
) : java.io.Serializable