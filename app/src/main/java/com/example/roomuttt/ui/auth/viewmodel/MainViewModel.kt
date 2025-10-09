package com.example.roomuttt.ui.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    fun loadRooms() {
        viewModelScope.launch {
            // Simulado; cambia a llamada a Firebase/Firestore
            val sampleRooms = listOf(
                Room("Sala de Conferencias A", 20, 20.1234, -99.5678),  // Lat/Lng de Tula-Tepeji
                Room("Cuarto Estudiantil B", 4, 20.1250, -99.5700)
            )
            _rooms.value = sampleRooms
        }
    }

    fun searchRooms(query: String) {
        viewModelScope.launch {
            val filtered = _rooms.value.filter { room ->
                room.title.contains(query, ignoreCase = true)
            }
            _rooms.value = filtered
        }
    }

    fun onLocationPermissionGranted() {
        // Actualiza mapa con ubicación del usuario si tienes FusedLocationProvider
    }
    fun initMap(googleMap: GoogleMap) {
        googleMap.uiSettings.isZoomGesturesEnabled = true
        // Centra en Tula-Tepeji (ajusta coordenadas)
        val tula = LatLng(20.1234, -99.5678)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tula, 12f))
        // Agrega marcadores
        rooms.value.forEach { room ->
            googleMap.addMarker(MarkerOptions().position(LatLng(room.latitude, room.longitude)).title(room.title))
        }
    }


    data class Room(
        val title: String,
        val capacity: Int,
        val latitude: Double,
        val longitude: Double
    )
}