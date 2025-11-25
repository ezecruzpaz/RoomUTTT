package com.roomu.app.ui.home.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.roomu.app.data.api.RoomApiService
import com.roomu.app.data.preferences.LocationPreferences
import com.roomu.app.domain.model.RoomData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    // 🔥 Cuartos filtrados para mostrar
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

    // 🗺️ Referencias del mapa
    private var googleMap: GoogleMap? = null
    private val markers = mutableListOf<Marker>()
    private var radiusCircle: Circle? = null

    private var isSearchActive = false
    private var hasLocationPermission = false

    init {
        // ✅ Cargar ubicación guardada al iniciar
        locationPreferences.getLocation()?.let { location ->
            _currentLocation.value = location
            hasLocationPermission = true
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
                        // ✅ FILTRAR SOLO CUARTOS DISPONIBLES
                        val availableRooms = roomsResponse.result.filter { room ->
                            room.disponible == true
                        }

                        _allRooms.value = availableRooms
                        Log.d(TAG, "✅ ${availableRooms.size} cuartos disponibles cargados (de ${roomsResponse.result.size} totales)")

                        // ✅ Si tenemos ubicación, filtrar
                        if (hasLocationPermission && _currentLocation.value != null) {
                            filterByCurrentLocation()

                            // 🔥 DIBUJAR RADIO Y MARCADORES EN BACKGROUND
                            delay(200) // Dar tiempo a que se rendericen los datos
                            googleMap?.let { map ->
                                _currentLocation.value?.let { location ->
                                    drawSearchRadius(map, location)
                                    updateMapMarkers()
                                    Log.d(TAG, "🗺️ Mapa actualizado con radio y marcadores")
                                }
                            }
                        } else {
                            Log.d(TAG, "⏳ Esperando ubicación del usuario...")
                            _rooms.value = emptyList()
                            _showViewMoreButton.value = false
                        }
                    } else {
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

        // ✅ Mostrar TODOS los cuartos disponibles cercanos
        _rooms.value = nearbyRooms
        _showViewMoreButton.value = false

        if (nearbyRooms.isEmpty()) {
            Log.w(TAG, "⚠️ No hay cuartos disponibles cerca de tu ubicación")
        } else {
            Log.d(TAG, "✅ ${nearbyRooms.size} cuartos disponibles encontrados cerca")
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
                    clearMapMarkers()
                    filterByCurrentLocation()

                    val nearbyRooms = _allRooms.value.filter { room ->
                        val distance = room.distanceFrom(currentLocation.latitude, currentLocation.longitude)
                        distance != null && distance <= searchRadiusKm
                    }

                    googleMap?.let { map ->
                        drawSearchRadius(map, currentLocation)
                        updateMapMarkers(nearbyRooms)
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(currentLocation, 11f),
                            1000,
                            null
                        )
                    }

                    Log.d(TAG, "✅ Regresado a ubicación actual: ${nearbyRooms.size} cuartos disponibles cercanos")
                } else {
                    _rooms.value = emptyList()
                    _showViewMoreButton.value = false
                    clearMapMarkers()
                }

                return@launch
            }

            isSearchActive = true

            // ✅ Buscar solo en cuartos disponibles
            val filtered = _allRooms.value.filter { room ->
                room.nombre.contains(query, ignoreCase = true) ||
                        room.descripcion?.contains(query, ignoreCase = true) == true ||
                        room.ubicacion.contains(query, ignoreCase = true)
            }

            // ✅ Mostrar TODOS los resultados disponibles
            _rooms.value = filtered
            _showViewMoreButton.value = false

            Log.d(TAG, "🔍 Búsqueda: '$query' - ${filtered.size} cuartos disponibles encontrados")

            if (filtered.isNotEmpty()) {
                filtered.first().getLatLng()?.let { (lat, lng) ->
                    Log.d(TAG, "📍 Centrando mapa en primer resultado: $lat, $lng")
                    updateLocationAndFilter(lat, lng, filtered)
                }
            } else {
                Log.w(TAG, "⚠️ No se encontraron cuartos disponibles con '$query'")
                clearMapMarkers()
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

        // ✅ Mostrar TODOS los cuartos disponibles
        _rooms.value = nearbyRooms
        _showViewMoreButton.value = false

        Log.d(TAG, "📍 Ubicación actualizada: $lat, $lng")
        Log.d(TAG, "🏠 ${nearbyRooms.size} cuartos disponibles en radio de ${searchRadiusKm}km")

        googleMap?.let { map ->
            clearMapMarkers()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 11f), 1000, null)
            drawSearchRadius(map, location)
            updateMapMarkers(nearbyRooms)
        }
    }

    // 🗺️ NUEVA FUNCIÓN: Actualizar marcadores con cuartos filtrados (para filtros de MainActivity)
    fun updateMapMarkersWithFilteredRooms(filteredRooms: List<RoomData>) {
        googleMap?.let { map ->
            // Limpiar solo los marcadores, NO el círculo
            markers.forEach { it.remove() }
            markers.clear()

            // 🔥 MANTENER el círculo de radio visible
            // Si no existe y tenemos ubicación, dibujarlo
            if (radiusCircle == null && _currentLocation.value != null) {
                drawSearchRadius(map, _currentLocation.value!!)
            }

            // Agregar marcadores solo para cuartos filtrados - TODOS ROJOS
            filteredRooms.forEach { room ->
                room.getLatLng()?.let { (lat, lng) ->
                    val position = LatLng(lat, lng)

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(room.nombre)
                            .snippet("${room.precio}/mes - ${room.capacidad} persona(s)")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )

                    marker?.let { markers.add(it) }
                }
            }

            Log.d(TAG, "🗺️ Mapa actualizado con ${filteredRooms.size} marcadores filtrados (radio visible)")
        }
    }

    // 🗺️ Limpiar todos los marcadores y círculo del mapa
    private fun clearMapMarkers() {
        markers.forEach { it.remove() }
        markers.clear()
        radiusCircle?.remove()
        radiusCircle = null
    }

    // 🔥 Dibujar círculo de radio de búsqueda
    private fun drawSearchRadius(map: GoogleMap, center: LatLng) {
        try {
            // Remover círculo anterior si existe
            radiusCircle?.remove()

            radiusCircle = map.addCircle(
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

    // 🔥 Actualizar marcadores en el mapa - TODOS ROJOS
    private fun updateMapMarkers(roomsToShow: List<RoomData>? = null) {
        googleMap?.let { map ->
            // Limpiar marcadores anteriores
            markers.forEach { it.remove() }
            markers.clear()

            // ✅ Solo mostrar marcadores de cuartos disponibles cercanos
            val rooms = roomsToShow ?: (_currentLocation.value?.let { location ->
                _allRooms.value.filter { room ->
                    val distance = room.distanceFrom(location.latitude, location.longitude)
                    distance != null && distance <= searchRadiusKm
                }
            } ?: emptyList())

            // ✅ TODOS LOS MARCADORES EN ROJO
            rooms.forEach { room ->
                room.getLatLng()?.let { (lat, lng) ->
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(room.nombre)
                            .snippet("$${room.precio} - ${room.capacidad} personas")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )

                    marker?.let { markers.add(it) }
                }
            }
            Log.d(TAG, "🗺️ ${rooms.size} marcadores de cuartos disponibles agregados al mapa")
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
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 11f))
            // 🔥 DIBUJAR RADIO AL INICIALIZAR EL MAPA SI YA TENEMOS UBICACIÓN
            drawSearchRadius(googleMap, location)
            updateMapMarkers()
        } ?: run {
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

    // 🔥 Obtener todos los cuartos disponibles cercanos (para pantalla "Ver más")
    fun getAllRooms(): List<RoomData> {
        val location = _currentLocation.value ?: return emptyList()

        // ✅ Solo devolver cuartos disponibles dentro del radio
        return _allRooms.value.filter { room ->
            val distance = room.distanceFrom(location.latitude, location.longitude)
            distance != null && distance <= searchRadiusKm
        }.sortedBy { it.distanceFrom(location.latitude, location.longitude) }
    }

    // ✅ Obtener TODOS los cuartos disponibles sin filtrar por ubicación
    fun getAllRoomsUnfiltered(): List<RoomData> {
        return _allRooms.value
    }
}