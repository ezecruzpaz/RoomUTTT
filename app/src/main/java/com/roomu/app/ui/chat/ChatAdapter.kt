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
        val obsceneWords = listOf(
            // Español - México / Latinoamérica
            "mierda", "puta", "puto", "putita", "putazo", "putear",
            "pendejo", "pendeja", "pendejada", "cabron", "cabrón", "cabrona",
            "chingar", "chingada", "chingado", "chingón", "chingona",
            "chingones", "chingaderas", "chingadera", "jodido", "joder",
            "pinche", "pinches", "chingatumadre", "madres", "hijueputa",
            "hijoputa", "hijo de puta", "hijadelachingada", "mamón", "mamona",
            "mamadas", "mamada", "verga", "vergazo", "verguita", "vergazos",
            "culero", "culera", "culo", "culito", "chingón", "chingaos",
            "chingado", "coño", "carajo", "hostia", "capullo", "gilipollas",
            "imbécil", "idiota", "tarado", "baboso", "babosa", "malparido",
            "pelotudo", "boludo", "cornudo", "zorra", "zorrilla", "cerdo",
            "perra", "marica", "maricón", "putón", "putona", "putita",
            "prostituta", "ratero", "ladron", "ladrona", "pajero", "pajera",
            "maldito", "maldita", "asqueroso", "asquerosa", "estúpido",
            "estupida", "tonto", "tonta", "puerco", "puerca", "feo",
            "güey", "wey","we", "guey", "pinchi", "pinshi", "chingoncito",
            "chingaderas", "chingaderita", "chingoncísima", "culazo",
            "culito", "culazo", "pinchazo", "chingones", "chingaderas",
            "mierdero", "mierdita", "mamoncito", "putarraco",

            // Español - España
            "cojones", "cojonudo", "hostia", "jilipollas", "mamarracho",
            "pringao", "capullo", "subnormal", "petardo", "cagón", "cagona",
            "soplapollas", "caraculo", "cabronazo", "gilipuertas", "mierdoso",

            // Inglés - general
            "fuck", "fucking", "motherfucker", "shit", "bullshit", "asshole",
            "dick", "dickhead", "cock", "bastard", "bitch", "slut", "whore",
            "damn", "crap", "piss", "pissed", "hell", "cunt", "twat", "prick",
            "jerk", "idiot", "moron", "stupid", "dumbass", "retard",
            "fag", "faggot", "gayass", "suck", "sucker", "pussy", "balls",
            "nuts", "bloody", "arse", "arsehole", "bollocks", "wanker",
            "tosser", "bugger", "douche", "douchebag", "dipshit",
            "motherfucking", "goddamn", "sonofabitch", "jackass", "shithead",
            "bitchass", "slutty", "whorish", "bastards", "asses", "retarded",
            "fuckface", "cum", "cumshot", "semen", "boobs", "tits", "boobies",
            "nipple", "butthole", "ballsack", "nutsack", "spank", "jerkoff",
            "handjob", "blowjob", "porn", "porno", "pornographic", "screw",
            "screwed", "screwing", "dammit", "goddammit", "fuckhead"
        )

        var filteredText = text
        obsceneWords.forEach { word ->
            val regex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
            filteredText = filteredText.replace(regex) { "*".repeat(it.value.length) }
        }
        return filteredText
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