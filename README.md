# RoomUTTT

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Descripción

RoomUTTT es una aplicación móvil Android para la reserva de cuartos y salas de conferencias en un campus universitario. Utiliza Firebase para autenticación (email/password y Google Sign-In), Google Maps para visualización de ubicaciones, y MVVM con Hilt para arquitectura limpia. La app permite registro/login, búsqueda de cuartos en un mapa, y reserva básica.

### Características Principales
- **Autenticación Segura**: Login con email/contraseña y Google Sign-In.
- **Registro de Usuarios**: Formulario con validación y verificación de email.
- **Mapa Interactivo**: Muestra cuartos con marcadores, búsqueda y ubicación en tiempo real.
- **Pantalla Principal**: Barra de búsqueda, notificaciones, perfil, y tarjeta de cuarto con botón de reserva.
- **Arquitectura MVVM**: Use cases, repositories, ViewModels con coroutines y Flow.
- **Diseño Material**: Temas modernos con scroll fluido y UI responsiva.

### Capturas de Pantalla
- **Login**:
<img width="576" height="1280" alt="image" src="https://github.com/user-attachments/assets/e1064c5d-6192-4558-9a36-ce3955ceae9c" />

- **Registro**:
 <img width="576" height="1280" alt="image" src="https://github.com/user-attachments/assets/ce7e6374-d340-43fe-b76b-d57dce636507" />

- 


## Tecnologías Utilizadas
- **Lenguaje**: Kotlin
- **Arquitectura**: MVVM + Hilt (DI)
- **Backend**: Firebase Authentication, Firestore (para usuarios/cuartos)
- **Mapa**: Google Maps SDK for Android
- **UI**: Material Components, ConstraintLayout, Coroutines, Lifecycle
- **Otras**: Google Play Services (Auth & Location)

## Requisitos
- **Android Studio**: Hedgehog (2023.1.1) o superior.
- **SDK**: Min SDK 24, Target SDK 34.
- **Firebase**: Proyecto configurado con `google-services.json`.
- **Google Maps API Key**: Obtén una en [Google Cloud Console](https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com) y agrégala en `AndroidManifest.xml`.

## Instalación y Configuración

### 1. Clona el Repositorio
```bash
git clone https://github.com/tu-usuario/roomuttt.git
cd roomuttt
