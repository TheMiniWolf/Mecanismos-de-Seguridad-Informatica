package com.taller2.faro

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Intervalos de envío que se ofrecen, en minutos. */
private val INTERVALS = listOf(1, 5, 10, 15, 30)

/**
 * Pantalla principal de quien comparte: encender o apagar el envío,
 * elegir cada cuánto se envía y ver qué fue lo último que salió del teléfono.
 */
@Composable
fun ShareScreen(session: Session) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Patrón Observer: la pantalla se redibuja sola cuando el Tracker publica un estado nuevo
    val status by Tracker.status.collectAsState()

    var interval by remember { mutableStateOf(session.intervalMin) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    var sendingNow by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf("share") }   // qué hacer cuando se concedan los permisos

    // Reloj que avanza cada 15 s para mantener al día los "hace 3 min"
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    fun say(text: String, isError: Boolean) {
        notice = text
        noticeIsError = isError
    }

    fun startSharing() {
        if (!isLocationEnabled(context)) {
            say("Enciende la ubicación (GPS) del teléfono para que Faro pueda enviar puntos.", true)
        } else {
            notice = null
        }
        // Arranca (o actualiza) el servicio en primer plano
        ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
    }

    fun stopSharing() {
        context.stopService(Intent(context, LocationService::class.java))
        notice = null
    }

    fun sendNow() {
        sendingNow = true
        notice = null
        requestSingleLocation(context) { location ->
            if (location == null) {
                sendingNow = false
                say("No se pudo obtener la ubicación. Revisa que el GPS esté encendido e inténtalo de nuevo.", true)
            } else {
                scope.launch {
                    val delivered = Tracker.upload(context.applicationContext, location)
                    sendingNow = false
                    if (delivered) {
                        say("Ubicación enviada", false)
                    } else {
                        say(Tracker.status.value.error ?: "No se pudo enviar. Queda guardada y se enviará cuando haya conexión.", true)
                    }
                }
            }
        }
    }

    fun perform(action: String) {
        if (action == "share") startSharing() else sendNow()
    }

    // Diálogo del sistema para pedir permisos. Al cerrarse, se continúa solo si hay permiso de ubicación.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (hasLocationPermission(context)) {
            perform(pendingAction)
        } else {
            say("Sin el permiso de ubicación Faro no puede compartir dónde estás. Puedes darlo en los ajustes del teléfono.", true)
        }
    }

    /** Ejecuta la acción si ya están los permisos; si falta alguno, lo pide primero. */
    fun withPermissions(action: String) {
        pendingAction = action
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) perform(action) else permissionLauncher.launch(missing.toTypedArray())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ---------- Bloque oscuro: estado y botón principal ----------
        Column(
            Modifier
                .fillMaxWidth()
                .background(FaroColors.Ink, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Beacon(active = status.sharing, modifier = Modifier.size(180.dp))
            Text(
                if (status.sharing) "Compartiendo tu ubicación" else "No estás compartiendo",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (status.sharing) "Se envía cada $interval min. Solo la ven las personas que aceptaste."
                else "Nadie puede ver dónde estás ahora.",
                color = FaroColors.Mist,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (status.sharing) {
                FaroOutlineButton("Dejar de compartir", onClick = { stopSharing() }, modifier = Modifier.fillMaxWidth(), color = Color.White)
            } else {
                FaroButton("Empezar a compartir", onClick = { withPermissions("share") }, modifier = Modifier.fillMaxWidth())
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            val currentNotice = notice
            if (currentNotice != null) {
                Spacer(Modifier.height(16.dp))
                Notice(currentNotice, isError = noticeIsError)
            }

            // ---------- Intervalo ----------
            SectionTitle("Cada cuánto se envía")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in INTERVALS) {
                    val selected = minutes == interval
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) FaroColors.Ink else Color.White)
                            .border(1.dp, if (selected) FaroColors.Ink else FaroColors.Line, RoundedCornerShape(10.dp))
                            .clickable {
                                interval = minutes
                                session.intervalMin = minutes
                                // Si ya se está compartiendo, el servicio toma el nuevo intervalo
                                if (status.sharing) startSharing()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$minutes min",
                            color = if (selected) Color.White else FaroColors.Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // ---------- Último envío ----------
            SectionTitle("Lo último que salió de este teléfono")
            Panel {
                val lat = status.lastLat
                val lon = status.lastLon
                val sentAt = status.lastSentAt
                if (lat == null || lon == null || sentAt == null) {
                    Text("Todavía no se ha enviado ninguna ubicación.", color = FaroColors.Ink2)
                } else {
                    Text("Enviada ${timeAgo(sentAt, now)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    DataRow("Hora", clockText(sentAt))
                    DataRow("Coordenadas", String.format(Locale.US, "%.5f, %.5f", lat, lon))
                    val accuracy = status.lastAccuracy
                    if (accuracy != null) DataRow("Precisión", "±${accuracy.toInt()} m")
                }
                if (status.pending > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (status.pending == 1) "1 punto guardado, se enviará cuando haya conexión."
                        else "${status.pending} puntos guardados, se enviarán cuando haya conexión.",
                        color = FaroColors.Amber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FaroOutlineButton(
                if (sendingNow) "Obteniendo ubicación…" else "Enviar mi ubicación ahora",
                onClick = { withPermissions("now") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sendingNow,
            )
        }
    }
}

/** Fila "etiqueta + valor" para los datos del último envío. */
@Composable
private fun DataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(110.dp), color = FaroColors.Ink2, fontSize = 14.sp)
        Text(value, fontSize = 14.sp)
    }
}
