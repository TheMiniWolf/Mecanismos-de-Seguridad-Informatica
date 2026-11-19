package com.taller2.faro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Error de la API. code = código HTTP (0 cuando ni siquiera hubo conexión). */
class ApiException(val code: Int, message: String) : Exception(message)

/** Una solicitud de permiso: alguien (observer) quiere ver a otra persona (target). */
data class Consent(
    val id: Int,
    val observerPhone: String,
    val targetPhone: String,
    val status: String,      // pending | accepted | rejected | revoked
    val createdAt: String,
)

/** Un punto de ubicación guardado en el servidor. */
data class Point(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val recordedAt: String,
)

/**
 * Patrón Facade: esta clase es la ÚNICA puerta de la app hacia el backend.
 * Las pantallas llaman a métodos con nombre (verify, sendLocation...) y no saben
 * nada de URLs, cabeceras ni JSON. Usa solo clases que ya trae Android
 * (HttpURLConnection y org.json), sin librerías externas.
 */
class Api(val baseUrl: String, private val token: String? = null) {

    // ---------- Autenticación ----------

    /** Pide el código. Devuelve el código solo en modo desarrollo (SMS simulado). */
    suspend fun requestCode(phone: String): String? {
        val text = call("POST", "/auth/request-code", JSONObject().put("phone", phone))
        return parse {
            val json = JSONObject(text)
            if (json.isNull("dev_code")) null else json.getString("dev_code")
        }
    }

    /** Verifica el código y devuelve el token de sesión. */
    suspend fun verify(phone: String, code: String): String {
        val text = call("POST", "/auth/verify", JSONObject().put("phone", phone).put("code", code))
        return parse { JSONObject(text).getString("access_token") }
    }

    // ---------- Ubicaciones ----------

    suspend fun sendLocation(latitude: Double, longitude: Double, accuracy: Double?, recordedAtIso: String) {
        val body = JSONObject()
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("recorded_at", recordedAtIso)
        if (accuracy != null) body.put("accuracy", accuracy)
        call("POST", "/locations", body)
    }

    /** Mi propio historial, del punto más reciente al más antiguo. */
    suspend fun myHistory(limit: Int): List<Point> {
        val text = call("GET", "/locations/me?limit=$limit")
        return parse {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Point(
                    id = o.getLong("id"),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    accuracy = if (o.isNull("accuracy")) null else o.getDouble("accuracy"),
                    recordedAt = o.getString("recorded_at"),
                )
            }
        }
    }

    /** Borra todo mi historial. Devuelve cuántos puntos se borraron. */
    suspend fun deleteHistory(): Int {
        val text = call("DELETE", "/locations/me")
        return parse { JSONObject(text).optInt("deleted", 0) }
    }

    // ---------- Permisos ----------

    /** Solicitudes que otras personas me hicieron para ver mi ubicación. */
    suspend fun incoming(): List<Consent> {
        val text = call("GET", "/consents/incoming")
        return parse {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Consent(
                    id = o.getInt("id"),
                    observerPhone = o.getString("observer_phone"),
                    targetPhone = o.getString("target_phone"),
                    status = o.getString("status"),
                    createdAt = o.getString("created_at"),
                )
            }
        }
    }

    /** action = accept | reject | revoke */
    suspend fun consentAction(id: Int, action: String) {
        call("POST", "/consents/$id/$action", JSONObject())
    }

    // ---------- Transporte ----------

    /** Si la respuesta no es el JSON esperado (por ejemplo, la dirección apunta a otro servidor), avisa en vez de fallar. */
    private fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw ApiException(0, "El servidor respondió algo inesperado. Revisa la dirección del servidor.")
    }

    /**
     * Hace la petición HTTP. withContext(Dispatchers.IO) la saca del hilo de la
     * interfaz: Android prohíbe usar la red en el hilo principal.
     */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val connection = try {
                URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw ApiException(0, "La dirección del servidor no es válida.")
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")
                // La identidad viaja en la cabecera Authorization, nunca en la URL
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw ApiException(code, errorDetail(text, code))
                text
            } catch (e: IOException) {
                throw ApiException(
                    0,
                    "No se pudo conectar con el servidor. Revisa la dirección y que el teléfono esté en la misma red que el PC.",
                )
            } catch (e: IllegalArgumentException) {
                throw ApiException(0, "La dirección del servidor no es válida.")
            } finally {
                connection.disconnect()
            }
        }

    /** FastAPI responde los errores como {"detail": "..."} o {"detail": [{"msg": "..."}]}. */
    private fun errorDetail(text: String, code: Int): String = try {
        when (val detail = JSONObject(text).opt("detail")) {
            is String -> detail
            is JSONArray -> (0 until detail.length()).joinToString("; ") { detail.getJSONObject(it).optString("msg") }
            else -> "Error $code"
        }
    } catch (e: Exception) {
        "Error $code"
    }
}
