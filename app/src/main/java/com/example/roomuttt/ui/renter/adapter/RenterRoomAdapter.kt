package com.example.roomuttt.ui.renter.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.roomuttt.R

class RenterRoomAdapter(private val rooms: MutableList<com.example.roomuttt.ui.renter.Room>) :
    RecyclerView.Adapter<RenterRoomAdapter.RoomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_card, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = rooms[position]
        holder.bind(room)
    }

    override fun getItemCount(): Int = rooms.size

    fun updateRooms(newRooms: List<com.example.roomuttt.ui.renter.Room>) {
        rooms.clear()
        rooms.addAll(newRooms)
        notifyDataSetChanged()
    }

    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvRoomPrice: TextView = itemView.findViewById(R.id.tv_room_price)

        fun bind(room: com.example.roomuttt.ui.renter.Room) {
            tvRoomName.text = room.name
            tvRoomPrice.text = "MXN ${String.format("%.2f", room.price)}"
        }
    }
}