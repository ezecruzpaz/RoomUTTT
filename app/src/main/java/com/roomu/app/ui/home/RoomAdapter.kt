package com.roomu.app.ui.home.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.roomu.app.R
import androidx.appcompat.view.ContextThemeWrapper
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.room.RoomDetailActivity

class RoomAdapter(
    private val onRoomClick: (RoomData) -> Unit,
    private val allRooms: List<RoomData> = emptyList(),
    private val isRenterView: Boolean = false, // ✅ Nuevo: indica si es vista de arrendatario
    private val onEditRoom: ((RoomData) -> Unit)? = null, // ✅ Callback para editar
    private val onDeleteRoom: ((RoomData) -> Unit)? = null, // ✅ Callback para eliminar
    private val onToggleAvailability: ((RoomData) -> Unit)? = null // ✅ Callback para cambiar disponibilidad
) : ListAdapter<RoomData, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_card, parent, false)
        return RoomViewHolder(
            view,
            onRoomClick,
            allRooms,
            isRenterView,
            onEditRoom,
            onDeleteRoom,
            onToggleAvailability
        )
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RoomViewHolder(
        itemView: View,
        private val onRoomClick: (RoomData) -> Unit,
        private val allRooms: List<RoomData>,
        private val isRenterView: Boolean,
        private val onEditRoom: ((RoomData) -> Unit)?,
        private val onDeleteRoom: ((RoomData) -> Unit)?,
        private val onToggleAvailability: ((RoomData) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivRoomImage: ImageView = itemView.findViewById(R.id.iv_room_image)
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvRoomPrice: TextView = itemView.findViewById(R.id.tv_room_price)
        private val tvRoomCapacity: TextView = itemView.findViewById(R.id.tv_room_capacity)
        private val tvRoomDescription: TextView = itemView.findViewById(R.id.tv_room_description)
        private val btnReserve: Button = itemView.findViewById(R.id.btn_reserve)
        private val ivMenuOptions: ImageView = itemView.findViewById(R.id.iv_menu_options)

        fun bind(room: RoomData) {
            tvRoomName.text = room.nombre
            tvRoomPrice.text = "Precio: $${room.precio} MXN/mes"
            tvRoomCapacity.text = "Capacidad: ${room.capacidad} personas"
            tvRoomDescription.text = room.descripcion ?: "Sin descripción"

            // ✅ Mostrar estado de disponibilidad
            if (!room.disponible) {
                tvRoomName.text = "${room.nombre} (Rentado)"
                tvRoomName.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            } else {
                tvRoomName.setTextColor(itemView.context.getColor(android.R.color.black))
            }

            if (room.imagenes.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(room.imagenes.first())
                    .placeholder(R.drawable.room_placeholder)
                    .error(R.drawable.room_placeholder)
                    .centerCrop()
                    .into(ivRoomImage)
            } else {
                ivRoomImage.setImageResource(R.drawable.room_placeholder)
            }

            itemView.setOnClickListener {
                onRoomClick(room)
            }

            btnReserve.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, RoomDetailActivity::class.java)
                intent.putExtra("room_id", room.id)
                intent.putExtra("allRooms", ArrayList(allRooms))
                context.startActivity(intent)
            }

            // ✅ Mostrar u ocultar menú de opciones
            if (isRenterView && isOwner(room)) {
                ivMenuOptions.visibility = View.VISIBLE
                ivMenuOptions.setOnClickListener {
                    showPopupMenu(it, room)
                }
            } else {
                ivMenuOptions.visibility = View.GONE
            }
        }

        // ✅ Función isOwner actualizada
        private fun isOwner(room: RoomData): Boolean {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            return currentUserId == room.userId
        }

        private fun showPopupMenu(view: View, room: RoomData) {
            val context = view.context
            val wrapper = ContextThemeWrapper(context, R.style.MyPopupMenu)
            val popup = PopupMenu(wrapper, view)

            popup.menuInflater.inflate(R.menu.menu_room_options, popup.menu)

            // Cambiar texto según disponibilidad
            val toggleItem = popup.menu.findItem(R.id.action_toggle_availability)
            toggleItem?.title = if (room.disponible) "Marcar como rentado" else "Marcar como disponible"

            // FORZAR MOSTRAR ICONOS (REFLEXIÓN)
            try {
                val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
                popupField.isAccessible = true
                val popupWindow = popupField.get(popup)
                popupWindow.javaClass
                    .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(popupWindow, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> { onEditRoom?.invoke(room); true }
                    R.id.action_delete -> { onDeleteRoom?.invoke(room); true }
                    R.id.action_toggle_availability -> { onToggleAvailability?.invoke(room); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class RoomDiffCallback : DiffUtil.ItemCallback<RoomData>() {
        override fun areItemsTheSame(oldItem: RoomData, newItem: RoomData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RoomData, newItem: RoomData): Boolean {
            return oldItem == newItem
        }
    }
}