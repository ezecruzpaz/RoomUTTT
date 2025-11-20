package com.roomu.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoomUtttApplication : Application() {

    companion object {
        const val CHANNEL_ID = "chat_channel"
        const val CHANNEL_NAME = "Mensajes de Chat"

        // DATADOG CREDENCIALES
        private const val DD_CLIENT_TOKEN = "pubf7b334ba5f7cbfc42970af0d9d639faf"
        private const val DD_APPLICATION_ID = "a48235cf-8d08-4b43-8af6-97ce5d171b77"
        private const val DD_ENVIRONMENT = "prod"
        private const val DD_SERVICE_NAME = "roomu-android"

        private const val TAG = "RoomUtttApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Iniciando aplicación...")

        // 1. Inicialización de Datadog
        initializeDatadog()


        // 2. Inicialización de notificaciones
        createNotificationChannel()

        Log.d(TAG, "✅ Aplicación inicializada completamente")
    }

    private fun initializeDatadog() {
        try {
            Log.d(TAG, "📊 Inicializando Datadog...")

            // Configuración Core de Datadog
            val configuration = Configuration.Builder(
                clientToken = DD_CLIENT_TOKEN,
                env = DD_ENVIRONMENT,
                variant = DD_SERVICE_NAME
            )
                .useSite(DatadogSite.US5)
                // ✅ Batch optimizado según entorno
                .setBatchSize(
                    if (BuildConfig.DEBUG) {
                        com.datadog.android.core.configuration.BatchSize.SMALL
                    } else {
                        com.datadog.android.core.configuration.BatchSize.MEDIUM
                    }
                )
                // ✅ Upload optimizado según entorno
                .setUploadFrequency(
                    if (BuildConfig.DEBUG) {
                        com.datadog.android.core.configuration.UploadFrequency.FREQUENT
                    } else {
                        com.datadog.android.core.configuration.UploadFrequency.AVERAGE
                    }
                )
                .build()

            // Inicializar Datadog Core
            Datadog.initialize(this, configuration, TrackingConsent.GRANTED)

            // ✅ Verbosity ajustado según entorno
            if (BuildConfig.DEBUG) {
                Datadog.setVerbosity(Log.VERBOSE)
            } else {
                Datadog.setVerbosity(Log.WARN) // Solo warnings y errores en producción
            }


            // Configurar RUM
            val rumConfiguration = com.datadog.android.rum.RumConfiguration.Builder(DD_APPLICATION_ID)
                .trackUserInteractions()
                .trackLongTasks(100L)
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .build()

            com.datadog.android.rum.Rum.enable(rumConfiguration)

            // Configurar Logs
            val logsConfiguration = com.datadog.android.log.LogsConfiguration.Builder()
                .build()

            com.datadog.android.log.Logs.enable(logsConfiguration)

            // Configurar Tracing
            val traceConfiguration = com.datadog.android.trace.TraceConfiguration.Builder()
                .build()

            com.datadog.android.trace.Trace.enable(traceConfiguration)

            Log.i(TAG, "✅ Datadog inicializado correctamente")
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "   - Site: US5")
                Log.i(TAG, "   - Environment: $DD_ENVIRONMENT")
                Log.i(TAG, "   - Service: $DD_SERVICE_NAME")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando Datadog", e)
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones de nuevos mensajes en el chat"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)

                Log.d(TAG, "✅ Canal de notificaciones creado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creando canal de notificaciones", e)
            }
        }
    }
}