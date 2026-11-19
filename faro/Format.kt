package com.taller2.faro

import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** +573001112233 -> +57 300 111 2233 (solo cambia cómo se muestra). */
fun formatPhone(phone: String?): String {
    if (phone == null) return ""
    val match = Regex("""^\+57(\d{3})(\d{3})(\d{4})$""").find(phone) ?: return phone
    val (a, b, c) = match.destructured
    return "+57 $a $b $c"
}

/** "hace 3 min" a partir de dos instantes en milisegundos. */
fun timeAgo(thenMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - thenMillis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 10 -> "ahora mismo"
        seconds < 60 -> "hace $seconds s"
        seconds < 3600 -> "hace ${seconds / 60} min"
        seconds < 86400 -> "hace ${seconds / 3600} h"
        seconds < 172800 -> "hace 1 día"
        else -> "hace ${seconds / 86400} días"
    }
}

/** Convierte la fecha ISO que envía el servidor a milisegundos. null si no se entiende. */
fun parseIsoMillis(iso: String): Long? = try {
    OffsetDateTime.parse(iso).toInstant().toEpochMilli()
} catch (e: Exception) {
    null
}

/** "10 nov, 5:57 p. m." en la hora local del teléfono. */
fun clockText(millis: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.forLanguageTag("es-CO")).format(Date(millis))

fun distanceText(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.forLanguageTag("es-CO"), "%.1f km", meters / 1000)

/** Distancia en metros entre dos coordenadas (fórmula de Haversine). */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val x = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * radius * asin(sqrt(x))
}
