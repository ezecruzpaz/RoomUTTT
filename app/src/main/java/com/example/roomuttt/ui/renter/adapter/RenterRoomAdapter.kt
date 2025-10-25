package com.example.roomuttt.ui.renter.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.roomuttt.R
import com.example.roomuttt.domain.model.RoomData

class RenterRoomAdapter(
    private val rooms: MutableList<RoomData>,
    private val onRoomClick: ((RoomData) -> Unit)? = null
) : RecyclerView.Adapter<RenterRoomAdapter.RoomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_card, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = rooms[position]
        holder.bind(room)
        holder.itemView.setOnClickListener {
            onRoomClick?.invoke(room)
        }
    }

    override fun getItemCount(): Int = rooms.size

    fun updateRooms(newRooms: List<RoomData>) {
        rooms.clear()
        rooms.addAll(newRooms)
        notifyDataSetChanged()
    }

    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivRoomImage: ImageView? = itemView.findViewById(R.id.iv_room_image)
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvRoomPrice: TextView? = itemView.findViewById(R.id.tv_room_price)
        private val tvRoomStatus: TextView? = itemView.findViewById(R.id.tv_room_status)

        fun bind(room: RoomData) {
            // Nombre del cuarto
            tvRoomName.text = room.nombre

            // Precio
            tvRoomPrice?.text = "$${String.format("%.0f", room.precio)} MXN"

            // Estado
            tvRoomStatus?.text = if (room.disponible) "Disponible" else "Ocupado"

            // Imagen (primera imagen de la lista)
            ivRoomImage?.let { imageView ->
                if (room.imagenes.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(room.imagenes[0])
                        .placeholder(R.drawable.ic_placeholder_room)
                        .error(R.drawable.ic_placeholder_room)
                        .centerCrop()
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.ic_placeholder_room)
                }
            }
        }
    }
}