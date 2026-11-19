package com.taller2.faro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ======================================================================
// Bienvenida: consentimiento informado antes de usar la app
// ======================================================================
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FaroColors.Ink)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        BrandRow()
        // El faro ocupa el espacio que sobre: en pantallas pequeñas se encoge solo
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Beacon(active = true, modifier = Modifier.size(210.dp))
        }
        Text(
            "Tú decides quién ve dónde estás",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(20.dp))
        Promise("Nadie te ve sin tu permiso", "Cada persona debe pedírtelo, y tú lo aceptas o lo rechazas.")
        Promise("Siempre sabes cuándo se comparte", "Mientras Faro envía tu ubicación verás un aviso fijo en las notificaciones.")
        Promise("Puedes parar cuando quieras", "Detén el envío, retira un permiso o borra todo tu historial desde la app.")
        Spacer(Modifier.height(20.dp))
        FaroButton("Entendido, continuar", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Promise(title: String, detail: String) {
    Row(Modifier.padding(bottom = 14.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .background(FaroColors.Pink, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(detail, color = FaroColors.Mist, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

// ======================================================================
// Entrada: número de teléfono y código de verificación
// ======================================================================
@Composable
fun LoginScreen(session: Session, notice: String?, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(session.server) }
    var editServer by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeStep by remember { mutableStateOf(false) }     // false = pedir código, true = escribirlo
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(notice) }

    fun friendly(e: ApiException): String =
        if (e.code == 422) "Escribe el número con + y el código del país, por ejemplo +573001111111." else (e.message ?: "Error")

    fun requestCode() {
        scope.launch {
            loading = true
            error = null
            try {
                session.server = server
                val devCode = Api(session.server).requestCode(phone.trim())
                code = devCode ?: ""        // en el laboratorio el SMS es simulado y el código llega aquí
                codeStep = true
            } catch (e: ApiException) {
                error = friendly(e)
            }
            loading = false
        }
    }

    fun verify() {
        scope.launch {
            loading = true
            error = null
            try {
                val token = Api(session.server).verify(phone.trim(), code.trim())
                session.phone = phone.trim()
                session.token = token
                onLoggedIn()
            } catch (e: ApiException) {
                error = friendly(e)
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(FaroColors.Ink)) {
        // Parte superior oscura con la marca y el mensaje
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            Beacon(
                active = true,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-30).dp).size(260.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 24.dp)) {
                BrandRow()
                Spacer(Modifier.height(18.dp))
                Text(
                    "Solo te ve quien tú aceptas",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
            }
        }

        // Hoja blanca con el formulario; sube con el teclado
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                val currentError = error
                if (currentError != null) {
                    Notice(currentError, isError = true)
                    Spacer(Modifier.height(14.dp))
                }

                if (!codeStep) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tu número de teléfono") },
                        placeholder = { Text("+573001111111") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    Spacer(Modifier.height(14.dp))
                    FaroButton("Pedir código", onClick = { requestCode() }, modifier = Modifier.fillMaxWidth(), enabled = phone.isNotBlank(), loading = loading)
                } else {
                    Text("Código enviado a ${formatPhone(phone.trim())}", color = FaroColors.Ink2, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { new -> code = new.filter { it.isDigit() }.take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código de 6 dígitos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(14.dp))
                    FaroButton("Entrar", onClick = { verify() }, modifier = Modifier.fillMaxWidth(), enabled = code.length == 6, loading = loading)
                    TextButton(onClick = { codeStep = false; code = ""; error = null }) {
                        Text("Usar otro número")
                    }
                }

                // Dirección del backend: cambia entre emulador y teléfono real
                Spacer(Modifier.height(6.dp))
                if (editServer) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dirección del servidor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = { Text("Emulador: http://10.0.2.2:8000. Teléfono real: http://IP-de-tu-PC:8000") },
                    )
                } else {
                    TextButton(onClick = { editServer = true }) {
                        Text("Servidor: $server (cambiar)", color = FaroColors.Ink2, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
