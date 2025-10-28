package com.example.roomuttt.ui.home.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.roomuttt.R
import com.example.roomuttt.domain.model.RoomData
import com.example.roomuttt.ui.room.RoomDetailActivity

class RoomAdapter(
    private val onRoomClick: (RoomData) -> Unit,
    private val allRooms: List<RoomData> = emptyList() // ✅ Recibir lista completa
) : ListAdapter<RoomData, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_card, parent, false)
        return RoomViewHolder(view, onRoomClick, allRooms) // ✅ Pasar la lista
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RoomViewHolder(
        itemView: View,
        private val onRoomClick: (RoomData) -> Unit,
        private val allRooms: List<RoomData> // ✅ Recibir lista completa
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivRoomImage: ImageView = itemView.findViewById(R.id.iv_room_image)
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvRoomPrice: TextView = itemView.findViewById(R.id.tv_room_price)
        private val tvRoomCapacity: TextView = itemView.findViewById(R.id.tv_room_capacity)
        private val tvRoomDescription: TextView = itemView.findViewById(R.id.tv_room_description)
        private val btnReserve: Button = itemView.findViewById(R.id.btn_reserve)

        fun bind(room: RoomData) {
            tvRoomName.text = room.nombre
            tvRoomPrice.text = "Precio: $${room.precio} MXN/mes"
            tvRoomCapacity.text = "Capacidad: ${room.capacidad} personas"
            tvRoomDescription.text = room.descripcion ?: "Sin descripción"

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

            // ✅ Click en botón "Ver Detalles"
            btnReserve.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, RoomDetailActivity::class.java)
                intent.putExtra("room_id", room.id)
                intent.putExtra("allRooms", ArrayList(allRooms)) // ✅ Pasar lista completa
                context.startActivity(intent)
            }
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