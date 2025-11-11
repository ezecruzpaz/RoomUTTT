package com.roomu.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.roomu.app.data.model.Message
import com.roomu.app.databinding.ItemMessageReceivedBinding
import com.roomu.app.databinding.ItemMessageSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: String,
    private val onMessageDelete: (Message) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            // ✅ Filtrar palabras obscenas
            val filteredText = filterObsceneWords(message.text)
            binding.tvMessage.text = filteredText
            binding.tvTime.text = formatTime(message.timestamp?.time ?: 0L)

            // ✅ Long click para eliminar
            binding.root.setOnLongClickListener {
                showDeleteDialog(message, binding.root.context)
                true
            }
        }
    }

    inner class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            // ✅ Filtrar palabras obscenas
            val filteredText = filterObsceneWords(message.text)
            binding.tvMessage.text = filteredText
            binding.tvTime.text = formatTime(message.timestamp?.time ?: 0L)

            // Long click solo para mensajes propios (no mostrar delete para recibidos)
            binding.root.setOnLongClickListener {
                false
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) 1 else 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 1) {
            val binding = ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentViewHolder(binding)
        } else {
            val binding = ItemMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ReceivedViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(newMessage: Message) {
        messages.add(newMessage)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * ✅ Filtrar palabras obscenas
     */
    private fun filterObsceneWords(text: String): String {
        // Lista de palabras prohibidas (agregar las que quieras)
        val obsceneWords = listOf(
            "mierda", "puta", "culo", "bastardo", "idiota",
            "pendejo", "chingada", "cabrón", "jodido",
            "shit", "fuck", "ass", "damn", "hell",
            "bitch", "bastard", "crap"
        )

        var filtered = text

        obsceneWords.forEach { word ->
            val regex = Regex("\\b$word\\b", RegexOption.IGNORE_CASE)
            filtered = filtered.replace(regex, "*".repeat(word.length))
        }

        return filtered
    }

    /**
     * ✅ Formatear hora del mensaje
     */
    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return ""

        return try {
            val date = Date(timestamp)
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * ✅ Dialog para confirmar eliminación
     */
    private fun showDeleteDialog(message: Message, context: android.content.Context) {
        AlertDialog.Builder(context)
            .setTitle("Eliminar mensaje")
            .setMessage("¿Deseas eliminar este mensaje?")
            .setPositiveButton("Eliminar") { _, _ ->
                onMessageDelete(message)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}