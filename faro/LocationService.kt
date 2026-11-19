package com.taller2.faro

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que envía la ubicación cada cierto intervalo.
 *
 * "En primer plano" significa que Android obliga a mostrar una notificación fija
 * mientras funciona. Esa notificación es la garantía de transparencia: la persona
 * siempre sabe que su ubicación se está compartiendo y puede detenerlo desde ahí.
 */
class LocationService : Service() {

    // Corrutinas del servicio: se cancelan todas juntas en onDestroy
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient

    /** Patrón Observer: Google Play Services llama aquí cada vez que hay una ubicación nueva. */
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch { Tracker.upload(applicationContext, location) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null   // no se usa como servicio enlazado

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Botón "Dejar de compartir" de la notificación
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }

        val session = Session(this)
        try {
            // Android exige mostrar la notificación a los pocos segundos de arrancar
            val notification = buildNotification(session.intervalMin)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: se declara que el servicio es de tipo "ubicación"
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Sin permiso de ubicación (o arranque no permitido): no se comparte nada
            stopSharing()
            return START_NOT_STICKY
        }

        if (session.token == null || !hasLocationPermission(this)) {
            stopSharing()
            return START_NOT_STICKY
        }

        requestUpdates(session.intervalMin)
        Tracker.setSharing(true)
        return START_STICKY
    }

    /** Se comprueba el permiso justo antes (en onStartCommand); por eso se silencia el aviso de lint. */
    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalMin: Int) {
        val millis = intervalMin * 60_000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, millis)
            .setMinUpdateIntervalMillis(millis)   // nunca más seguido que el intervalo elegido
            .setWaitForAccurateLocation(false)
            .build()
        // Pedir de nuevo con el mismo callback reemplaza la petición anterior
        // (así se aplica un cambio de intervalo sin reiniciar el servicio)
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun stopSharing() {
        stopForeground(STOP_FOREGROUND_REMOVE)   // quita la notificación fija
        stopSelf()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        Tracker.setSharing(false)
        super.onDestroy()
    }

    private fun buildNotification(intervalMin: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Ubicación compartida", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Aviso fijo mientras Faro comparte tu ubicación"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).setAction(ACTION_STOP), flags,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Faro está compartiendo tu ubicación")
            .setContentText("Cada $intervalMin min, solo con las personas que aceptaste")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, "Dejar de compartir", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.taller2.faro.STOP"
        private const val CHANNEL_ID = "faro_sharing"
        private const val NOTIFICATION_ID = 1
    }
}
