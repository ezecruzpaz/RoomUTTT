package com.example.roomuttt.ui.home.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomuttt.data.api.RoomApiService
import com.example.roomuttt.data.preferences.LocationPreferences
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
    private val roomApiService: RoomApiService,
    private val locationPreferences: LocationPreferences

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

    // 🔥 Mostrar botón "Ver más"
    private val _showViewMoreButton = MutableStateFlow(false)
    val showViewMoreButton: StateFlow<Boolean> = _showViewMoreButton.asStateFlow()

    // ✅ Exponer todos los cuartos como StateFlow
    val allRooms: StateFlow<List<RoomData>> = _allRooms.asStateFlow()


    // 🔥 Radio de búsqueda en kilómetros
    private val searchRadiusKm = 10.0

    private var googleMap: GoogleMap? = null
    private var isSearchActive = false
    private var hasLocationPermission = false // ✅ Nueva bandera

    init {
        // ✅ Cargar ubicación guardada al iniciar
        locationPreferences.getLocation()?.let { location ->
            _currentLocation.value = location
            hasLocationPermission = true
            Log.d(TAG, "📍 Ubicación restaurada: ${location.latitude}, ${location.longitude}")
        }
    }

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
                        Log.d(TAG, "✅ ${roomsResponse.result.size} cuartos cargados totales")

                        // ✅ NO MOSTRAR NADA HASTA TENER UBICACIÓN
                        if (hasLocationPermission && _currentLocation.value != null) {
                            filterByCurrentLocation()
                        } else {
                            Log.d(TAG, "⏳ Esperando ubicación del usuario...")
                            _rooms.value = emptyList()
                            _showViewMoreButton.value = false
                        }

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

    // ✅ Filtrar por ubicación actual
    private fun filterByCurrentLocation() {
        val location = _currentLocation.value ?: return

        val nearbyRooms = _allRooms.value.filter { room ->
            val distance = room.distanceFrom(location.latitude, location.longitude)
            distance != null && distance <= searchRadiusKm
        }.sortedBy { it.distanceFrom(location.latitude, location.longitude) }

        val roomsToShow = nearbyRooms.take(2)
        _rooms.value = roomsToShow
        _showViewMoreButton.value = nearbyRooms.size > 2

        Log.d(TAG, "📍 Ubicación: ${location.latitude}, ${location.longitude}")
        Log.d(TAG, "🏠 ${nearbyRooms.size} cuartos en radio de ${searchRadiusKm}km")
        Log.d(TAG, "📦 Mostrando ${roomsToShow.size} cuartos")
        Log.d(TAG, "🔘 Botón Ver Más: ${_showViewMoreButton.value}")

        if (nearbyRooms.isEmpty()) {
            Log.w(TAG, "⚠️ No hay cuartos cerca de tu ubicación")
        }
    }

    // 🔥 Buscar cuartos por nombre o ubicación
    fun searchRooms(query: String) {
        viewModelScope.launch {
            if (query.isEmpty()) {
                isSearchActive = false
                Log.d(TAG, "🔍 Búsqueda vacía - Volviendo a ubicación actual")

                if (hasLocationPermission && _currentLocation.value != null) {
                    val currentLocation = _currentLocation.value!!

                    // ✅ PRIMERO: Limpiar el mapa completamente
                    googleMap?.clear()

                    // ✅ SEGUNDO: Filtrar cuartos por ubicación actual
                    filterByCurrentLocation()

                    // ✅ TERCERO: Obtener cuartos cercanos
                    val nearbyRooms = _allRooms.value.filter { room ->
                        val distance = room.distanceFrom(currentLocation.latitude, currentLocation.longitude)
                        distance != null && distance <= searchRadiusKm
                    }

                    // ✅ CUARTO: Redibujar todo en el mapa
                    googleMap?.let { map ->
                        // Dibujar círculo de búsqueda
                        drawSearchRadius(map, currentLocation)

                        // Actualizar marcadores de cuartos cercanos
                        updateMapMarkers(nearbyRooms)

                        // Mover cámara de vuelta a ubicación actual
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(currentLocation, 11f),
                            1000,
                            null
                        )
                    }

                    Log.d(TAG, "✅ Regresado a ubicación actual: ${nearbyRooms.size} cuartos cercanos")

                } else {
                    // Sin permiso de ubicación o sin ubicación
                    _rooms.value = emptyList()
                    _showViewMoreButton.value = false
                    googleMap?.clear()
                }

                return@launch
            }

            // ✅ Si hay búsqueda activa
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

            if (filtered.isNotEmpty()) {
                filtered.first().getLatLng()?.let { (lat, lng) ->
                    Log.d(TAG, "📍 Centrando mapa en primer resultado: $lat, $lng")
                    updateLocationAndFilter(lat, lng, filtered)
                }
            } else {
                Log.w(TAG, "⚠️ No se encontraron cuartos con '$query'")
                googleMap?.clear()
                _rooms.value = emptyList()
                _showViewMoreButton.value = false
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

        googleMap?.let { map ->
            map.clear()
            // ✅ ZOOM 11 PARA VER TODO EL RADIO
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 11f), 1000, null)
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
            // ✅ Solo mostrar marcadores de cuartos cercanos, no todos
            val rooms = roomsToShow ?: (_currentLocation.value?.let { location ->
                _allRooms.value.filter { room ->
                    val distance = room.distanceFrom(location.latitude, location.longitude)
                    distance != null && distance <= searchRadiusKm
                }
            } ?: emptyList())

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

        _currentLocation.value?.let { location ->
            // ✅ ZOOM 11 INICIAL
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 11f))
            drawSearchRadius(googleMap, location)
            updateMapMarkers()
        } ?: run {
            // ✅ Ubicación predeterminada con ZOOM 11
            val tula = LatLng(20.0910, -98.7624)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tula, 11f))
        }
    }

    fun onLocationPermissionGranted(lat: Double, lng: Double) {
        hasLocationPermission = true

        // ✅ Guardar en SharedPreferences
        locationPreferences.saveLocation(lat, lng)
        Log.d(TAG, "💾 Ubicación guardada: $lat, $lng")

        if (!isSearchActive) {
            updateLocationAndFilter(lat, lng)
        } else {
            _currentLocation.value = LatLng(lat, lng)
            Log.d(TAG, "📍 Ubicación guardada pero búsqueda activa")
        }
    }

    // 🔥 Obtener todos los cuartos cercanos (para pantalla "Ver más")
    // 🔥 Obtener todos los cuartos cercanos (para pantalla "Ver más")
    fun getAllRooms(): List<RoomData> {
        val location = _currentLocation.value ?: return emptyList()

        // ✅ Solo devolver cuartos dentro del radio
        return _allRooms.value.filter { room ->
            val distance = room.distanceFrom(location.latitude, location.longitude)
            distance != null && distance <= searchRadiusKm
        }.sortedBy { it.distanceFrom(location.latitude, location.longitude) }
    }

    // ✅ NUEVO: Obtener TODOS los cuartos sin filtrar por ubicación
    fun getAllRoomsUnfiltered(): List<RoomData> {
        return _allRooms.value
    }
}