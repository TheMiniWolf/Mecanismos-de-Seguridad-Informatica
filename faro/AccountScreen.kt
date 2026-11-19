package com.taller2.faro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Cuenta: datos de la sesión, borrado del historial y cierre de sesión. */
@Composable
fun AccountScreen(session: Session, api: Api, onLogout: () -> Unit, onSessionExpired: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        val currentNotice = notice
        if (currentNotice != null) {
            Spacer(Modifier.height(16.dp))
            Notice(currentNotice, isError = noticeIsError)
        }

        SectionTitle("Tu cuenta")
        Panel {
            Text("Número", color = FaroColors.Ink2, fontSize = 13.sp)
            Text(formatPhone(session.phone), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("Servidor", color = FaroColors.Ink2, fontSize = 13.sp)
            Text(session.server, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            Text("Panel web para ver ubicaciones", color = FaroColors.Ink2, fontSize = 13.sp)
            Text(session.server + "/panel/", fontSize = 15.sp)
        }

        SectionTitle("Tus datos")
        Text(
            "Faro guarda en el servidor los puntos que envías. Puedes borrarlos todos; quienes tenían tu permiso dejarán de ver ese recorrido.",
            color = FaroColors.Ink2,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        FaroOutlineButton("Borrar todo mi historial", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), color = FaroColors.Red)

        SectionTitle("Sesión")
        Text(
            "Al cerrar sesión se detiene el envío de ubicación y se borran de este teléfono el token y los puntos pendientes.",
            color = FaroColors.Ink2,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        FaroOutlineButton("Cerrar sesión", onClick = onLogout, modifier = Modifier.fillMaxWidth())
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar todo tu historial?") },
            text = { Text("Se eliminan del servidor todas tus ubicaciones guardadas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            val deleted = api.deleteHistory()
                            // También se limpia lo que la app recordaba del último envío
                            session.lastSent = null
                            Tracker.restore(session)
                            noticeIsError = false
                            notice = if (deleted == 1) "Historial borrado: 1 ubicación." else "Historial borrado: $deleted ubicaciones."
                        } catch (e: ApiException) {
                            if (e.code == 401) {
                                onSessionExpired()
                            } else {
                                noticeIsError = true
                                notice = e.message
                            }
                        }
                    }
                }) { Text("Borrar historial", color = FaroColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = FaroColors.Ink2) }
            },
        )
    }
}
