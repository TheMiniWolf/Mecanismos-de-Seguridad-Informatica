package com.taller2.faro

import android.content.Context
import android.content.SharedPreferences

/**
 * Sesión y ajustes de la app.
 *
 * Todo se guarda en SharedPreferences, que es almacenamiento PRIVADO de la app:
 * otras apps no pueden leerlo. Aquí viven el token, el número y la cola de
 * puntos pendientes de enviar.
 */
class Session(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("faro", Context.MODE_PRIVATE)

    /** Dirección del backend, por ejemplo http://192.168.1.20:8000 */
    var server: String
        get() = prefs.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) {
            prefs.edit().putString("server", value.trim().trimEnd('/')).apply()
        }

    /** Token JWT que entrega el backend al verificar el código. null = sin sesión. */
    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().putString("token", value).apply()
        }

    var phone: String?
        get() = prefs.getString("phone", null)
        set(value) {
            prefs.edit().putString("phone", value).apply()
        }

    /** true cuando la persona ya leyó y aceptó la pantalla de bienvenida. */
    var welcomed: Boolean
        get() = prefs.getBoolean("welcomed", false)
        set(value) {
            prefs.edit().putBoolean("welcomed", value).apply()
        }

    /** Cada cuántos minutos se envía la ubicación. */
    var intervalMin: Int
        get() = prefs.getInt("interval", 10)
        set(value) {
            prefs.edit().putInt("interval", value).apply()
        }

    /** Cola de puntos que aún no llegaron al servidor (texto JSON con una lista). */
    var pending: String
        get() = prefs.getString("pending", "[]") ?: "[]"
        set(value) {
            prefs.edit().putString("pending", value).apply()
        }

    /** Último punto enviado con éxito (texto JSON) para mostrarlo al abrir la app. */
    var lastSent: String?
        get() = prefs.getString("last_sent", null)
        set(value) {
            prefs.edit().putString("last_sent", value).apply()
        }

    /** Cierra la sesión: borra los datos personales y conserva solo los ajustes. */
    fun clear() {
        prefs.edit()
            .remove("token")
            .remove("phone")
            .remove("pending")
            .remove("last_sent")
            .apply()
    }

    companion object {
        /** 10.0.2.2 es la forma en que el emulador de Android ve al PC donde corre. */
        const val DEFAULT_SERVER = "http://10.0.2.2:8000"
    }
}
