package com.roomu.app.ui.renter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.databinding.ActivityChatsListBinding
import com.roomu.app.ui.chat.ChatActivity
import com.roomu.app.ui.chat.ChatItem
import com.roomu.app.ui.chat.ChatsListAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RenterChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsListBinding
    private lateinit var adapter: ChatsListAdapter
    private val chatsList = mutableListOf<ChatItem>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "RenterChats"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadChats()
    }   

    private fun setupToolbar() {
        binding.toolbar.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatsListAdapter(chatsList) { chatItem ->
            openChat(chatItem)
        }
        binding.rvChats.apply {
            this.adapter = this@RenterChatsActivity.adapter
            layoutManager = LinearLayoutManager(this@RenterChatsActivity)
        }
    }

    private fun loadChats() {
        val currentUserId = auth.currentUser?.uid ?: return

        Log.d(TAG, "🔄 Cargando chats para renter: $currentUserId")

        // Buscar chats donde el usuario actual es participante
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error cargando chats: ${error.message}")
                    showEmptyState()
                    return@addSnapshotListener
                }

                Log.d(TAG, "📨 Chats encontrados: ${snapshots?.size() ?: 0}")
                chatsList.clear()

                snapshots?.documents?.forEach { doc ->
                    try {
                        val chatId = doc.id
                        val lastMessage = doc.getString("lastMessage") ?: ""
                        val lastMessageTime = doc.getLong("lastMessageTime") ?: 0L
                        val participants = doc.get("participants") as? List<String> ?: listOf()
                        val unreadCountMap = doc.get("unreadCount") as? Map<String, Any>
                        val unreadCount = (unreadCountMap?.get(currentUserId) as? Number)?.toInt() ?: 0

                        // Obtener el otro usuario (el que NO es el actual)
                        val otherUserId = participants.firstOrNull { it != currentUserId }

                        if (otherUserId != null) {
                            // Obtener datos del otro usuario desde users
                            firestore.collection("users").document(otherUserId).get()
                                .addOnSuccessListener { userDoc ->
                                    val otherUserName = userDoc.getString("name") ?: "Usuario"
                                    val photoUrl = userDoc.getString("photoUrl")

                                    val chatItem = ChatItem(
                                        chatId = chatId,
                                        otherUserId = otherUserId,
                                        otherUserName = otherUserName,
                                        otherUserPhoto = photoUrl ?: "",
                                        lastMessage = lastMessage,
                                        lastMessageTime = lastMessageTime,
                                        unreadCount = unreadCount,
                                        roomId = ""
                                    )

                                    // Actualizar lista y ordenar por fecha más reciente
                                    val existingIndex = chatsList.indexOfFirst { it.chatId == chatId }
                                    if (existingIndex >= 0) {
                                        chatsList[existingIndex] = chatItem
                                    } else {
                                        chatsList.add(chatItem)
                                    }

                                    chatsList.sortByDescending { it.lastMessageTime }
                                    adapter.notifyDataSetChanged()

                                    hideEmptyState()
                                    Log.d(TAG, "✅ Chat actualizado: $otherUserName (unread: $unreadCount)")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "❌ Error obteniendo usuario $otherUserId: ${e.message}")
                                }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error procesando chat: ${e.message}")
                    }
                }

                // Mostrar empty state si no hay chats
                if (snapshots?.isEmpty == true) {
                    showEmptyState()
                    Log.d(TAG, "📭 No hay chats disponibles")
                } else {
                    hideEmptyState()
                }
            }
    }

    private fun openChat(chatItem: ChatItem) {
        Log.d(TAG, "📞 Abriendo chat con ${chatItem.otherUserName} (${chatItem.otherUserId})")

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("roomId", chatItem.roomId)
            putExtra("renterId", chatItem.otherUserId)
            putExtra("renterName", chatItem.otherUserName)
        }
        startActivity(intent)
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.rvChats.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
        binding.rvChats.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Recargar chats cuando vuelve a primer plano
        loadChats()
    }
}