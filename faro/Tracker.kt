package com.taller2.faro

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Lo que la interfaz necesita saber del envío de ubicación. */
data class TrackerStatus(
    val sharing: Boolean = false,          // ¿el servicio está enviando ahora mismo?
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastAccuracy: Double? = null,      // metros
    val lastSentAt: Long? = null,          // momento del último envío con éxito (ms)
    val pending: Int = 0,                  // puntos guardados esperando conexión
    val error: String? = null,
    val sessionExpired: Boolean = false,   // el servidor rechazó el token
)

/**
 * Patrón Singleton + Observer.
 *
 * - Singleton (`object`): hay un único Tracker en toda la app, compartido por el
 *   servicio de ubicación y por las pantallas.
 * - Observer: el estado se publica en un StateFlow. Las pantallas se suscriben
 *   y se redibujan solas cuando el servicio envía un punto nuevo.
 */
object Tracker {

    private val _status = MutableStateFlow(TrackerStatus())
    val status: StateFlow<TrackerStatus> = _status.asStateFlow()

    /** Evita que el servicio y el botón "Enviar ahora" suban puntos a la vez. */
    private val mutex = Mutex()

    private const val MAX_PENDING = 200

    fun setSharing(on: Boolean) {
        _status.update { it.copy(sharing = on) }
    }

    /** Al abrir la app, recupera el último envío y la cola guardados en el teléfono. */
    fun restore(session: Session) {
        var lat: Double? = null
        var lon: Double? = null
        var acc: Double? = null
        var at: Long? = null
        try {
            val saved = session.lastSent
            if (saved != null) {
                val o = JSONObject(saved)
                lat = o.getDouble("lat")
                lon = o.getDouble("lon")
                acc = if (o.has("acc")) o.getDouble("acc") else null
                at = o.getLong("sent")
            }
        } catch (e: Exception) {
            // Datos guardados ilegibles: se ignoran
        }
        val queued = try { JSONArray(session.pending).length() } catch (e: Exception) { 0 }
        _status.update {
            it.copy(lastLat = lat, lastLon = lon, lastAccuracy = acc, lastSentAt = at, pending = queued, error = null, sessionExpired = false)
        }
    }

    /** Al cerrar sesión se olvida todo. */
    fun reset() {
        _status.value = TrackerStatus()
    }

    /**
     * Envía un punto al servidor con "guardar y reenviar" (store-and-forward):
     * el punto entra primero a una cola guardada en el teléfono y solo sale de ella
     * cuando el servidor lo confirma. Si no hay red, queda pendiente y se envía
     * junto con el siguiente. Devuelve true si la cola quedó vacía.
     */
    suspend fun upload(context: Context, location: Location): Boolean = mutex.withLock {
        val session = Session(context)
        val token = session.token ?: return@withLock false

        // 1) Cola actual + punto nuevo, guardada ANTES de usar la red
        val queue = mutableListOf<JSONObject>()
        try {
            val saved = JSONArray(session.pending)
            for (i in 0 until saved.length()) queue.add(saved.getJSONObject(i))
        } catch (e: Exception) {
            // Cola dañada: se empieza de cero
        }
        queue.add(
            JSONObject()
                .put("lat", location.latitude)
                .put("lon", location.longitude)
                .put("acc", location.accuracy.toDouble())
                .put("at", isoUtc(location.time)),
        )
        while (queue.size > MAX_PENDING) queue.removeAt(0)   // límite: se descartan los más viejos
        session.pending = JSONArray(queue).toString()

        // 2) Enviar en orden; al primer fallo de red se detiene y el resto sigue en cola
        val api = Api(session.server, token)
        var sent = 0
        var error: String? = null
        var expired = false
        for (point in queue) {
            try {
                api.sendLocation(point.getDouble("lat"), point.getDouble("lon"), point.getDouble("acc"), point.getString("at"))
                sent++
            } catch (e: ApiException) {
                if (e.code == 422) {
                    sent++          // el servidor lo rechazó por inválido: se descarta para no bloquear la cola
                    continue
                }
                error = e.message
                expired = e.code == 401
                break
            }
        }

        // 3) Guardar lo que quedó y publicar el nuevo estado
        val rest = queue.drop(sent)
        session.pending = JSONArray(rest).toString()
        if (sent > 0) {
            val last = queue[sent - 1]
            val now = System.currentTimeMillis()
            session.lastSent = JSONObject()
                .put("lat", last.getDouble("lat"))
                .put("lon", last.getDouble("lon"))
                .put("acc", last.getDouble("acc"))
                .put("sent", now)
                .toString()
            _status.update {
                it.copy(lastLat = last.getDouble("lat"), lastLon = last.getDouble("lon"), lastAccuracy = last.getDouble("acc"), lastSentAt = now)
            }
        }
        _status.update { it.copy(pending = rest.size, error = error, sessionExpired = expired) }
        rest.isEmpty()
    }
}

/** Fecha en formato ISO 8601 y hora UTC, que es lo que espera el backend. */
fun isoUtc(millis: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(millis))
}

/** Permisos que la app pide en pantalla antes de empezar a compartir. */
fun requiredPermissions(): List<String> {
    val list = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    // Desde Android 13 mostrar notificaciones también requiere permiso
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
    return list
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** ¿Está encendida la ubicación (GPS) del teléfono? */
fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(manager)
}

/**
 * Pide UNA ubicación actual (para el botón "Enviar ahora").
 * Quien la llama ya comprobó el permiso; por eso se silencia el aviso de lint.
 */
@SuppressLint("MissingPermission")
fun requestSingleLocation(context: Context, onResult: (Location?) -> Unit) {
    if (!hasLocationPermission(context)) {
        onResult(null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location: Location? -> onResult(location) }
        .addOnFailureListener { onResult(null) }
}
