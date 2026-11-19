package com.taller2.faro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Permisos: quién ha pedido ver mi ubicación y quién puede verla ahora.
 * Aquí está el control de la persona sobre sus datos: aceptar, rechazar o retirar.
 */
@Composable
fun PermissionsScreen(consents: List<Consent>?, error: String?, onAction: (Consent, String) -> Unit) {
    // Solicitud que se está por aceptar: se confirma en un diálogo antes de dar acceso
    var toAccept by remember { mutableStateOf<Consent?>(null) }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Notice(error, isError = true)
        }

        if (consents == null) {
            // Primera carga
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = FaroColors.Magenta)
            }
        } else {
            val pending = consents.filter { it.status == "pending" }
            val accepted = consents.filter { it.status == "accepted" }
            val closed = consents.filter { it.status == "rejected" || it.status == "revoked" }

            SectionTitle("Quieren ver tu ubicación")
            if (pending.isEmpty()) {
                EmptyText("No tienes solicitudes por responder.")
            }
            for (consent in pending) {
                ConsentCard(consent, now) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FaroButton("Aceptar", onClick = { toAccept = consent }, modifier = Modifier.weight(1f))
                        FaroOutlineButton("Rechazar", onClick = { onAction(consent, "reject") }, modifier = Modifier.weight(1f))
                    }
                }
            }

            SectionTitle("Pueden verte ahora")
            if (accepted.isEmpty()) {
                EmptyText("Nadie puede ver tu ubicación.")
            }
            for (consent in accepted) {
                ConsentCard(consent, now) {
                    FaroOutlineButton("Retirar permiso", onClick = { onAction(consent, "revoke") }, modifier = Modifier.fillMaxWidth(), color = FaroColors.Red)
                }
            }

            if (closed.isNotEmpty()) {
                SectionTitle("Ya no tienen acceso")
                for (consent in closed) {
                    ConsentCard(consent, now, actions = null)
                }
            }
        }
    }

    // Confirmación explícita antes de dar acceso a la ubicación
    val asking = toAccept
    if (asking != null) {
        AlertDialog(
            onDismissRequest = { toAccept = null },
            title = { Text("¿Dejar que ${formatPhone(asking.observerPhone)} vea tu ubicación?") },
            text = { Text("Podrá ver dónde estás y tu recorrido mientras compartes. Puedes retirarle el permiso cuando quieras.") },
            confirmButton = {
                TextButton(onClick = {
                    onAction(asking, "accept")
                    toAccept = null
                }) { Text("Sí, puede verme") }
            },
            dismissButton = {
                TextButton(onClick = { toAccept = null }) { Text("Cancelar", color = FaroColors.Ink2) }
            },
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = FaroColors.Ink2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
}

/** Tarjeta de una solicitud: quién, cuándo, en qué estado y las acciones posibles. */
@Composable
private fun ConsentCard(consent: Consent, now: Long, actions: (@Composable () -> Unit)?) {
    Panel(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatPhone(consent.observerPhone),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            when (consent.status) {
                "pending" -> Pill("Pendiente", FaroColors.AmberTint, FaroColors.Amber)
                "accepted" -> Pill("Aceptado", FaroColors.GreenTint, FaroColors.Green)
                "rejected" -> Pill("Rechazado", FaroColors.RedTint, FaroColors.Red)
                else -> Pill("Retirado", FaroColors.RedTint, FaroColors.Red)
            }
        }
        val created = parseIsoMillis(consent.createdAt)
        if (created != null) {
            Text("Lo pidió ${timeAgo(created, now)}", color = FaroColors.Ink2, fontSize = 14.sp)
        }
        if (actions != null) {
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}
