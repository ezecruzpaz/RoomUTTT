package com.example.roomuttt.ui.renter

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomuttt.R
import com.example.roomuttt.ui.renter.adapter.RenterRoomAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RenterDashboardActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: RenterRoomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renter_dashboard)

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_rooms)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RenterRoomAdapter(mutableListOf())
        recyclerView.adapter = adapter

        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                // Filtra la lista según newText si implementas búsqueda
                return true
            }
        })

        loadRooms()
    }

    private fun loadRooms() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.uid?.let { uid ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val snapshot = firestore.collection("rooms")
                        .whereEqualTo("renterId", uid)
                        .limit(2) // Limita a 2 cuartos inicialmente
                        .get()
                        .await()
                    val rooms = snapshot.toObjects(com.example.roomuttt.ui.renter.Room::class.java) // Especifica el paquete correcto
                    if (rooms.isEmpty()) {
                        findViewById<TextView>(R.id.tv_no_rooms).visibility = android.view.View.VISIBLE
                        findViewById<Button>(R.id.btn_view_more).visibility = android.view.View.GONE
                    } else {
                        findViewById<TextView>(R.id.tv_no_rooms).visibility = android.view.View.GONE
                        adapter.updateRooms(rooms)
                        findViewById<Button>(R.id.btn_view_more).visibility = android.view.View.VISIBLE
                    }
                } catch (e: Exception) {
                    findViewById<TextView>(R.id.tv_no_rooms).visibility = android.view.View.VISIBLE
                    findViewById<Button>(R.id.btn_view_more).visibility = android.view.View.GONE
                }
            }
        }
    }
}