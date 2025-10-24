package com.example.roomuttt.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.roomuttt.R
import com.example.roomuttt.domain.model.RoomData

class RoomAdapter(
    private val onRoomClick: (RoomData) -> Unit
) : ListAdapter<RoomData, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_card, parent, false)
        return RoomViewHolder(view, onRoomClick)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RoomViewHolder(
        itemView: View,
        private val onRoomClick: (RoomData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivRoomImage: ImageView = itemView.findViewById(R.id.iv_room_image)
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvRoomPrice: TextView = itemView.findViewById(R.id.tv_room_price)
        private val tvRoomCapacity: TextView = itemView.findViewById(R.id.tv_room_capacity)
        private val tvRoomDescription: TextView = itemView.findViewById(R.id.tv_room_description)
        //private val tvRoomDistance: TextView = itemView.findViewById(R.id.tv_room_distance)

        fun bind(room: RoomData) {
            // Actualizar nombre dinámicamente
            tvRoomName.text = room.nombre ?: "Sin nombre"

            // Actualizar precio dinámicamente
            tvRoomPrice.text = if (room.precio != null) "$${room.precio}/mes" else "Precio no disponible"

            // Actualizar capacidad dinámicamente
            tvRoomCapacity.text = if (room.capacidad != null) "${room.capacidad} personas" else "Capacidad no disponible"

            // Actualizar descripción dinámicamente
            tvRoomDescription.text = room.descripcion ?: "Sin descripción"

            // Cargar imagen con Glide
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

            // Calcular distancia (si está disponible)
            // Por ahora ocultamos, pero puedes agregar lógica aquí
            //tvRoomDistance.visibility = View.GONE

            itemView.setOnClickListener {
                onRoomClick(room)
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