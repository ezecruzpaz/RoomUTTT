Descripción
RoomUTTT es una aplicación Android para la reservación de cuartos en un campus universitario (e.g., UTTT - Universidad Tecnológica de Tula-Tepeji). Permite autenticación con Google y email/password, registro de usuarios, y una pantalla principal con mapa interactivo (Google Maps) para buscar y reservar cuartos. Desarrollada con MVVM, Hilt para DI, Firebase para Auth, y Kotlin.
Características

Autenticación Segura: Login con Google Sign-In y email/password (con verificación de email).
Registro de Usuarios: Formulario con validación (email, nombre, contraseña, términos).
Pantalla Principal: Mapa con marcadores de cuartos, búsqueda por nombre, tarjeta de detalles, y botones para perfil/notificaciones.
Navegación: Flujo Login > MainActivity (con scroll y diseño responsive).
Permisos: Solicita ubicación para centrar el mapa en el usuario.

Tecnologías

Lenguaje: Kotlin
Arquitectura: MVVM con Hilt (DI), Coroutines, Flow
Backend: Firebase Authentication, Firestore (para perfiles futuros)
Mapa: Google Maps SDK
UI: Material Design Components, ConstraintLayout, ScrollView
Otras: ViewBinding, Lifecycle, Navigation Component

Requisitos

Android Studio (Flamingo o superior)
SDK mínimo: API 24 (Android 7.0)
Google Play Services (para Maps y Sign-In)
Firebase Project configurado (ver Setup)

Capturas de Pantalla

Login:
<img width="385" height="664" alt="image" src="https://github.com/user-attachments/assets/9a83336d-1553-4918-884d-203b91f1bc82" />

Registro:
<img width="336" height="596" alt="image" src="https://github.com/user-attachments/assets/747d8da4-9f03-4428-aeeb-a77fece666db" />

