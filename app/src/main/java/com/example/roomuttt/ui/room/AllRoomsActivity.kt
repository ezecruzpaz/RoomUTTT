package com.example.roomuttt.ui.room

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.roomuttt.R
import com.example.roomuttt.ui.home.adapter.RoomAdapter
import com.example.roomuttt.domain.model.RoomData

class AllRoomsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_rooms)

        val rooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData> ?: arrayListOf()
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_all_rooms)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RoomAdapter { room ->
            // Lógica para clic en cuarto (opcional)
        }.apply { submitList(rooms) }
    }
}