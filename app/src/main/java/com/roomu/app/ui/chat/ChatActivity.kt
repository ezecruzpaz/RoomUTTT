package com.roomu.app.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.roomu.app.R
import com.roomu.app.data.model.Message
import com.roomu.app.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var chatId: String
    private lateinit var roomId: String
    private lateinit var currentUserId: String
    private lateinit var otherUserId: String
    private lateinit var otherUserName: String
    private var predefinedMessage: String? = null
    private var pendingPhoneNumber: String? = null

    private var messagesListener: ListenerRegistration? = null

    companion object {
        private const val REQUEST_CALL_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ChatNotificationManager.createNotificationChannel(this)

        // ✅ NUEVO: Ocultar botón de llamada INMEDIATAMENTE
        binding.btnCall.visibility = android.view.View.GONE

        // Obtener datos del intent
        roomId = intent.getStringExtra("roomId") ?: ""
        otherUserId = intent.getStringExtra("renterId") ?: ""
        otherUserName = intent.getStringExtra("renterName") ?: "Usuario"
        predefinedMessage = intent.getStringExtra("predefinedMessage")
        currentUserId = auth.currentUser?.uid ?: ""

        // Validar datos
        if (otherUserId.isEmpty() || currentUserId.isEmpty()) {
            Log.e("ChatActivity", "❌ Datos incompletos: otherUserId=$otherUserId, currentUserId=$currentUserId")
            finish()
            return
        }

        Log.d("ChatActivity", "✅ Datos válidos. Iniciando chat...")
        Log.d("ChatActivity", "📋 currentUserId=$currentUserId")
        Log.d("ChatActivity", "📋 otherUserId=$otherUserId")
        Log.d("ChatActivity", "📋 roomId=$roomId")
        if (predefinedMessage != null) {
            Log.d("ChatActivity", "📝 Mensaje predefinido: $predefinedMessage")
        }

        chatId = getChatId(currentUserId, otherUserId)
        Log.d("ChatActivity", "🔑 chatId generado: $chatId")

        setupUI()
        setupRecyclerView()
        setupSendButton()
        setupBackButton()
        listenToMessages()
        createChatIfNotExists()

        // Enviar mensaje predefinido automáticamente
        if (predefinedMessage != null && predefinedMessage!!.isNotEmpty()) {
            binding.etMessage.setText(predefinedMessage)
            binding.btnSend.post {
                binding.btnSend.performClick()
                binding.etMessage.text.clear()
            }
        }
    }
    private fun setupUI() {
        // Configurar nombre del contacto
        binding.tvContactName.text = otherUserName

        // Cargar avatar del usuario desde Firestore
        loadUserAvatar()

        // ✅ VERIFICAR ROL del OTRO usuario (otherUserId) en el chat
        checkOtherUserRoleAndSetupCallButton()
    }

    private fun checkOtherUserRoleAndSetupCallButton() {
        // ✅ Buscar en renters primero
        firestore.collection("renters").document(otherUserId).get()
            .addOnSuccessListener { renterDoc ->
                if (renterDoc.exists()) {
                    // ✅ El otro usuario ES RENTER - MOSTRAR botón de llamada
                    Log.d("ChatActivity", "✅ Otra persona es RENTER - Mostrando botón de llamada")
                    binding.btnCall.visibility = android.view.View.VISIBLE
                    setupCallButton()
                } else {
                    // El otro NO está en renters, verificar si es usuario normal
                    firestore.collection("users").document(otherUserId).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val role = userDoc.getString("role") ?: "user"

                                if (role == "renter") {
                                    // ✅ Es RENTER - MOSTRAR botón
                                    Log.d("ChatActivity", "✅ Otra persona es RENTER - Mostrando botón de llamada")
                                    binding.btnCall.visibility = android.view.View.VISIBLE
                                    setupCallButton()
                                } else {
                                    // ⛔ Es usuario normal - OCULTAR botón
                                    Log.d("ChatActivity", "⛔ Otra persona es USUARIO NORMAL - Ocultando botón de llamada")
                                    binding.btnCall.visibility = android.view.View.GONE
                                }
                            } else {
                                // Usuario no encontrado - OCULTAR
                                Log.w("ChatActivity", "⚠️ Otra persona no encontrada - Ocultando botón")
                                binding.btnCall.visibility = android.view.View.GONE
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatActivity", "❌ Error verificando en users: ${e.message}")
                            binding.btnCall.visibility = android.view.View.GONE
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Error verificando en renters: ${e.message}")
                // Fallback: buscar en users
                firestore.collection("users").document(otherUserId).get()
                    .addOnSuccessListener { userDoc ->
                        if (userDoc.exists()) {
                            val role = userDoc.getString("role") ?: "user"
                            if (role == "renter") {
                                binding.btnCall.visibility = android.view.View.VISIBLE
                                setupCallButton()
                            } else {
                                binding.btnCall.visibility = android.view.View.GONE
                            }
                        } else {
                            binding.btnCall.visibility = android.view.View.GONE
                        }
                    }
            }
    }
    private fun setupCallButton() {
        binding.btnCall.setOnClickListener {
            Log.d("ChatActivity", "🔍 Buscando teléfono de usuario: $otherUserId")

            // Buscar primero en renters (arrendatarios)
            firestore.document("renters/$otherUserId").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        Log.d("ChatActivity", "📄 Usuario encontrado en renters")
                        Log.d("ChatActivity", "📄 Documento completo: ${doc.data}")

                        val telefono = doc.getString("telefono")
                        Log.d("ChatActivity", "📞 Campo 'telefono' extraído: '$telefono'")

                        if (!telefono.isNullOrEmpty()) {
                            Log.d("ChatActivity", "✅ Teléfono válido encontrado en renters: $telefono")
                            pendingPhoneNumber = telefono
                            checkAndRequestCallPermission()
                        } else {
                            Log.w("ChatActivity", "⚠️ Renter sin teléfono, buscando en users...")
                            searchPhoneInUsers()
                        }
                    } else {
                        Log.d("ChatActivity", "⚠️ Usuario no encontrado en renters, buscando en users...")
                        searchPhoneInUsers()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "❌ Error buscando en renters: ${e.message}")
                    searchPhoneInUsers()
                }
        }
    }
    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            messages,
            currentUserId,
            onMessageDelete = { message ->
                deleteMessage(message)
            }
        )
        binding.rvMessages.apply {
            adapter = this@ChatActivity.adapter
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
        }
    }

    private fun deleteMessage(message: Message) {
        firestore.collection("chats/$chatId/messages")
            .document(message.messageId)
            .delete()
            .addOnSuccessListener {
                Log.d("CHAT", "✅ Mensaje eliminado: ${message.messageId}")
                Toast.makeText(this, "Mensaje eliminado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("CHAT", "❌ Error eliminando mensaje: ${e.message}")
                Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                // ✅ VALIDAR si tiene palabras obscenas
                if (containsObsceneWords(text)) {
                    showObsceneWordsDialog(text)
                } else {
                    sendMessage(text)
                    binding.etMessage.text.clear()
                }
            }
        }
    }
    private fun containsObsceneWords(text: String): Boolean {
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

        val lowerText = text.lowercase()
        return obsceneWords.any { word ->
            Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)
        }
    }
    private fun showObsceneWordsDialog(messageText: String) {
        cn.pedant.SweetAlert.SweetAlertDialog(this, cn.pedant.SweetAlert.SweetAlertDialog.WARNING_TYPE)
            .setTitleText("⚠️ Lenguaje inapropiado")
            .setContentText("Tu mensaje contiene palabras inapropiadas.\n\n¿Deseas editarlo?")
            .setConfirmText("Editar")
            .setCancelText("Cancelar")
            .setConfirmClickListener { dialog ->
                dialog.dismissWithAnimation()
                binding.etMessage.setText(messageText)
                binding.etMessage.setSelection(messageText.length)
                binding.etMessage.requestFocus()
            }
            .setCancelClickListener { dialog ->
                dialog.dismissWithAnimation()
                binding.etMessage.setText("")
            }
            .show()
    }
    private fun showDetectedObsceneWords(messageText: String) {
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

        val detectedWords = obsceneWords.filter { word ->
            Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(messageText.lowercase())
        }

        val wordsList = if (detectedWords.isNotEmpty()) {
            detectedWords.joinToString(", ")
        } else {
            "Ninguna detectada"
        }

        cn.pedant.SweetAlert.SweetAlertDialog(this, cn.pedant.SweetAlert.SweetAlertDialog.ERROR_TYPE)
            .setTitleText("❌ Palabras detectadas")
            .setContentText("Palabras inapropiadas encontradas:\n\n$wordsList")
            .setConfirmText("Editar")
            .setCancelText("Cancelar")
            .setConfirmClickListener { dialog ->
                dialog.dismissWithAnimation()
                binding.etMessage.setText(messageText)
                binding.etMessage.setSelection(messageText.length)
                binding.etMessage.requestFocus()
            }
            .setCancelClickListener { dialog ->
                dialog.dismissWithAnimation()
            }
            .show()
    }

    // ✅ MANTENER: sendMessage() - SIN CAMBIOS
    private fun sendMessage(text: String) {
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to currentUserId,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("chats/$chatId/messages")
            .add(messageData)
            .addOnSuccessListener {
                Log.d("CHAT", "✅ Mensaje enviado: $text")
                updateChatLastMessage(text)

                // ✅ Marcar como NO LEÍDO para el otro usuario
                lifecycleScope.launch {
                    ChatNotificationManager.markChatAsUnread(chatId, otherUserId)
                    Log.d("CHAT", "✅ Marcado como NO LEÍDO para: $otherUserId")
                }

                sendFCMNotification(text)
            }
            .addOnFailureListener { e ->
                Log.e("CHAT", "❌ Error enviando mensaje: ${e.message}")
            }
    }

    private fun updateChatLastMessage(text: String) {
        val chatRef = firestore.document("chats/$chatId")
        val updates = mapOf(
            "lastMessage" to text,
            "lastMessageTime" to System.currentTimeMillis()
        )
        chatRef.set(updates, SetOptions.merge())
    }
    private fun markAsRead() {
        lifecycleScope.launch {
            ChatNotificationManager.markChatAsRead(chatId, currentUserId)
            Log.d("CHAT", "✅ Chat marcado como LEÍDO")
        }
    }
    private fun listenToMessages() {
        messagesListener?.remove()

        messagesListener = firestore.collection("chats/$chatId/messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("CHAT", "❌ Error escuchando mensajes: ${error.message}")
                    return@addSnapshotListener
                }

                snapshots?.let { querySnapshot ->
                    val newMessages = mutableListOf<Message>()

                    querySnapshot.documents.forEach { doc ->
                        val msg = doc.toObject(Message::class.java)
                        msg?.let {
                            newMessages.add(it.copy(messageId = doc.id))

                            // ✅ Mostrar notificación si es de otro usuario y es nuevo
                            if (msg.senderId != currentUserId && !messages.any { it.messageId == doc.id }) {
                                ChatNotificationManager.showMessageNotification(
                                    this@ChatActivity,
                                    chatId,
                                    otherUserName,
                                    msg.text,
                                    otherUserId
                                )
                            }
                        }
                    }

                    messages.clear()
                    messages.addAll(newMessages)
                    adapter.notifyDataSetChanged()

                    // Scroll al final
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }

                    // ✅ Marcar como LEÍDO cuando recibe mensajes
                    markAsRead()
                }
                 }
}
    private fun createChatIfNotExists() {
        val chatRef = firestore.document("chats/$chatId")
        chatRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val chat = hashMapOf(
                    "roomId" to roomId,
                    "participants" to listOf(currentUserId, otherUserId),
                    "lastMessage" to "",
                    "lastMessageTime" to 0L,
                    "unreadCount" to mapOf(
                        currentUserId to 0,
                        otherUserId to 0
                    )
                )
                chatRef.set(chat)
                    .addOnSuccessListener {
                        Log.d("CHAT", "✅ Chat creado: $chatId")
                    }
                    .addOnFailureListener {
                        Log.e("CHAT", "❌ Error creando chat: ${it.message}")
                    }
            }
        }
    }
    private fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }
    private fun sendFCMNotification(message: String) {
        // Buscar primero en renters
        firestore.document("renters/$otherUserId").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val token = doc.getString("fcmToken")
                    if (token != null) {
                        Log.d("FCM", "📱 Token encontrado en renters: $token")
                        // TODO: Llamar a Cloud Function aquí
                    } else {
                        Log.w("FCM", "⚠️ No hay token FCM en renters para $otherUserId, buscando en users...")
                        searchFCMTokenInUsers()
                    }
                } else {
                    searchFCMTokenInUsers()
                }
            }
            .addOnFailureListener {
                Log.e("FCM", "❌ Error obteniendo token de renters: ${it.message}")
                searchFCMTokenInUsers()
            }
    }
    private fun searchFCMTokenInUsers() {
        firestore.document("users/$otherUserId").get()
            .addOnSuccessListener { doc ->
                val token = doc.getString("fcmToken")
                if (token != null) {
                    Log.d("FCM", "📱 Token encontrado en users: $token")
                    // TODO: Llamar a Cloud Function aquí
                } else {
                    Log.w("FCM", "⚠️ No hay token FCM para $otherUserId en ninguna colección")
                }
            }
            .addOnFailureListener {
                Log.e("FCM", "❌ Error obteniendo token de users: ${it.message}")
            }
    }
    private fun loadUserAvatar() {
        firestore.document("users/$otherUserId").get()
            .addOnSuccessListener { userDoc ->
                val photoUrl = userDoc.getString("photoUrl")
                loadAvatarImage(photoUrl)
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Error cargando avatar de users: ${e.message}")
                binding.ivContactAvatar.setImageResource(R.drawable.ic_profile_placeholder)
            }
    }
    private fun loadAvatarImage(photoUrl: String?) {
        if (!photoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(photoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivContactAvatar)
            Log.d("ChatActivity", "✅ Avatar cargado: $photoUrl")
        } else {
            Log.w("ChatActivity", "⚠️ Usuario sin foto de perfil")
            binding.ivContactAvatar.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }
    private fun searchPhoneInUsers() {
        firestore.document("users/$otherUserId").get()
            .addOnSuccessListener { doc ->
                Log.d("ChatActivity", "📄 Documento existe en users: ${doc.exists()}")
                Log.d("ChatActivity", "📄 Documento completo: ${doc.data}")

                val telefono = doc.getString("telefono")
                Log.d("ChatActivity", "📞 Campo 'telefono' extraído: '$telefono'")

                if (!telefono.isNullOrEmpty()) {
                    Log.d("ChatActivity", "✅ Teléfono válido encontrado en users: $telefono")
                    pendingPhoneNumber = telefono
                    checkAndRequestCallPermission()
                } else {
                    Toast.makeText(this, "El usuario no tiene teléfono registrado", Toast.LENGTH_SHORT).show()
                    Log.w("ChatActivity", "⚠️ Usuario sin teléfono en ninguna colección")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener teléfono", Toast.LENGTH_SHORT).show()
                Log.e("ChatActivity", "❌ Error obteniendo teléfono de users: ${e.message}")
                e.printStackTrace()
            }
    }
    private fun checkAndRequestCallPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya concedido, hacer la llamada
                pendingPhoneNumber?.let { makePhoneCall(it) }
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CALL_PHONE
            ) -> {
                // Mostrar explicación antes de pedir permiso
                showPermissionExplanationDialog()
            }

            else -> {
                // Pedir permiso directamente
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    REQUEST_CALL_PERMISSION
                )
            }
        }
    }
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de llamada")
            .setMessage("Esta aplicación necesita acceso a realizar llamadas telefónicas para contactar a $otherUserName.\n\n¿Deseas permitir las llamadas?")
            .setPositiveButton("Permitir") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    REQUEST_CALL_PERMISSION
                )
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CALL_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, hacer la llamada
                    Log.d("ChatActivity", "✅ Permiso de llamada concedido")
                    pendingPhoneNumber?.let { makePhoneCall(it) }
                } else {
                    // Permiso denegado
                    Log.w("ChatActivity", "⚠️ Permiso de llamada denegado")
                    Toast.makeText(this, "No se puede realizar la llamada sin el permiso", Toast.LENGTH_LONG).show()

                    // Ofrecer abrir el marcador en su lugar
                    pendingPhoneNumber?.let { phoneNumber ->
                        AlertDialog.Builder(this)
                            .setTitle("Abrir marcador")
                            .setMessage("¿Deseas abrir el marcador con el número $phoneNumber?")
                            .setPositiveButton("Abrir") { _, _ ->
                                openDialer(phoneNumber)
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun makePhoneCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
            Log.d("ChatActivity", "📞 Llamando directamente a: $phoneNumber")
        } catch (e: SecurityException) {
            Log.e("ChatActivity", "❌ Error de seguridad al llamar: ${e.message}")
            openDialer(phoneNumber)
        } catch (e: Exception) {
            Log.e("ChatActivity", "❌ Error al llamar: ${e.message}")
            Toast.makeText(this, "Error al iniciar llamada", Toast.LENGTH_SHORT).show()
        }
    }
    private fun openDialer(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
            Log.d("ChatActivity", "📞 Abriendo marcador con: $phoneNumber")
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir marcador", Toast.LENGTH_SHORT).show()
            Log.e("ChatActivity", "❌ Error abriendo marcador: ${e.message}")
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
        Log.d("CHAT", "🛑 ChatActivity destruida, listener cancelado")
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            markAsRead()
        }
    }
}