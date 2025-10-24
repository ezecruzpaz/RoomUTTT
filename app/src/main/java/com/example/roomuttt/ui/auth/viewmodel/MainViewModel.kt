package com.example.roomuttt.ui.home.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomuttt.data.api.RoomApiService
import com.example.roomuttt.domain.model.RoomData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val roomApiService: RoomApiService
) : ViewModel() {

    private val TAG = "MainViewModel"

    // 🔥 Lista completa de cuartos
    private val _allRooms = MutableStateFlow<List<RoomData>>(emptyList())

    // 🔥 Cuartos filtrados para mostrar (siempre solo 2 primeros)
    private val _rooms = MutableStateFlow<List<RoomData>>(emptyList())
    val rooms: StateFlow<List<RoomData>> = _rooms.asStateFlow()

    // 🔥 Estado de carga
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 🔥 Ubicación actual del usuario o búsqueda
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    // 🔥 Mostrar botón "Ver más" (siempre activado si hay más de 2 cuartos)
    private val _showViewMoreButton = MutableStateFlow(false)
    val showViewMoreButton: StateFlow<Boolean> = _showViewMoreButton.asStateFlow()

    // 🔥 Radio de búsqueda en kilómetros
    private val searchRadiusKm = 20.0

    private var googleMap: GoogleMap? = null
    private var isSearchActive = false // Controla si hay búsqueda activa

    // 🔥 Cargar todos los cuartos desde la API
    fun loadRooms() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "📥 Cargando cuartos desde API...")

                val response = roomApiService.getRooms()

                if (response.isSuccessful && response.body() != null) {
                    val roomsResponse = response.body()!!

                    if (roomsResponse.isSuccess && !roomsResponse.result.isNullOrEmpty()) {
                        _allRooms.value = roomsResponse.result

                        // Mostrar siempre solo los primeros 2 cuartos
                        val roomsToShow = _allRooms.value.take(2)
                        _rooms.value = roomsToShow

                        // Activar botón "Ver más" si hay más de 2 cuartos
                        _showViewMoreButton.value = _allRooms.value.size > 2

                        Log.d(TAG, "✅ ${roomsResponse.result.size} cuartos cargados, mostrando ${roomsToShow.size}")
                        Log.d(TAG, "🔘 Botón Ver Más: ${_showViewMoreButton.value}")

                        // Actualizar marcadores en el mapa
                        updateMapMarkers()
                    } else {
                        Log.w(TAG, "⚠️ No hay cuartos disponibles")
                        _allRooms.value = emptyList()
                        _rooms.value = emptyList()
                        _showViewMoreButton.value = false
                    }
                } else {
                    Log.e(TAG, "❌ Error en respuesta: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando cuartos: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 🔥 Buscar cuartos por nombre o ubicación
    fun searchRooms(query: String) {
        viewModelScope.launch {
            if (query.isEmpty()) {
                isSearchActive = false

                val roomsToShow = _allRooms.value.take(2)
                _rooms.value = roomsToShow
                _showViewMoreButton.value = _allRooms.value.size > 2

                Log.d(TAG, "🔍 Búsqueda vacía - Mostrando ${roomsToShow.size} cuartos")
                Log.d(TAG, "🔘 Botón Ver Más: ${_showViewMoreButton.value}")

                googleMap?.let { map ->
                    map.clear()
                    _currentLocation.value?.let { location ->
                        drawSearchRadius(map, location)
                    }
                    updateMapMarkers(_allRooms.value)
                }
                return@launch
            }

            isSearchActive = true

            val filtered = _allRooms.value.filter { room ->
                room.nombre.contains(query, ignoreCase = true) ||
                        room.descripcion?.contains(query, ignoreCase = true) == true
            }

            val roomsToShow = filtered.take(2)
            _rooms.value = roomsToShow
            _showViewMoreButton.value = filtered.size > 2

            Log.d(TAG, "🔍 Búsqueda: '$query' - ${filtered.size} resultados totales")
            Log.d(TAG, "📦 Mostrando ${roomsToShow.size} cuartos")
            Log.d(TAG, "🔘 Botón Ver Más: ${_showViewMoreButton.value}")

            if (filtered.isNotEmpty()) {
                filtered.first().getLatLng()?.let { (lat, lng) ->
                    Log.d(TAG, "📍 Centrando mapa en: $lat, $lng")
                    updateLocationAndFilter(lat, lng, filtered)
                }
            } else {
                Log.w(TAG, "⚠️ No se encontraron cuartos con '$query'")
                googleMap?.clear()
            }
        }
    }

    // 🔥 Actualizar ubicación y filtrar cuartos cercanos
    fun updateLocationAndFilter(lat: Double, lng: Double, preFilteredRooms: List<RoomData>? = null) {
        val location = LatLng(lat, lng)
        _currentLocation.value = location

        val roomsToFilter = preFilteredRooms ?: _allRooms.value

        val nearbyRooms = roomsToFilter.filter { room ->
            val distance = room.distanceFrom(lat, lng)
            distance != null && distance <= searchRadiusKm
        }.sortedBy { it.distanceFrom(lat, lng) }

        val roomsToShow = nearbyRooms.take(2)
        _rooms.value = roomsToShow
        _showViewMoreButton.value = nearbyRooms.size > 2

        Log.d(TAG, "📍 Ubicación actualizada: $lat, $lng")
        Log.d(TAG, "🏠 ${nearbyRooms.size} cuartos en radio de ${searchRadiusKm}km")
        Log.d(TAG, "📦 Mostrando ${roomsToShow.size} cuartos")
        Log.d(TAG, "🔘 Botón Ver Más: ${_showViewMoreButton.value}")

        googleMap?.let { map ->
            map.clear()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 13f), 1000, null)
            drawSearchRadius(map, location)
            updateMapMarkers(nearbyRooms)
        }
    }

    // 🔥 Dibujar círculo de radio de búsqueda
    private fun drawSearchRadius(map: GoogleMap, center: LatLng) {
        try {
            map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(searchRadiusKm * 1000)
                    .strokeColor(0x880000FF.toInt())
                    .fillColor(0x220000FF.toInt())
                    .strokeWidth(3f)
                    .visible(true)
                    .clickable(false)
            )
            Log.d(TAG, "⭕ Círculo dibujado: center=${center.latitude},${center.longitude}, radius=${searchRadiusKm}km")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error dibujando círculo: ${e.message}", e)
        }
    }

    // 🔥 Actualizar marcadores en el mapa
    private fun updateMapMarkers(roomsToShow: List<RoomData>? = null) {
        googleMap?.let { map ->
            val rooms = roomsToShow ?: _allRooms.value
            rooms.forEach { room ->
                room.getLatLng()?.let { (lat, lng) ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(room.nombre)
                            .snippet("$${room.precio} - ${room.capacidad} personas")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                }
            }
            Log.d(TAG, "🗺️ ${rooms.size} marcadores agregados al mapa")
        }
    }

    // 🔥 Inicializar mapa
    fun initMap(googleMap: GoogleMap) {
        this.googleMap = googleMap
        googleMap.uiSettings.apply {
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }

        if (_allRooms.value.isNotEmpty()) {
            updateMapMarkers()
        }

        _currentLocation.value?.let { location ->
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 13f))
            drawSearchRadius(googleMap, location)
        } ?: run {
            val tula = LatLng(20.0910, -98.7624)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tula, 12f))
        }
    }

    // 🔥 Cuando se concede permiso de ubicación
    fun onLocationPermissionGranted(lat: Double, lng: Double) {
        if (!isSearchActive) {
            updateLocationAndFilter(lat, lng)
        } else {
            _currentLocation.value = LatLng(lat, lng)
            Log.d(TAG, "📍 Ubicación guardada pero búsqueda activa")
        }
    }

    // 🔥 Obtener todos los cuartos (para pantalla "Ver más")
    fun getAllRooms(): List<RoomData> = _allRooms.value
}