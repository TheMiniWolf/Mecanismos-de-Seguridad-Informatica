package com.taller2.faro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app dibuja también detrás de la barra de estado. Como la parte de arriba
        // de todas las pantallas es azul oscuro, se piden iconos claros en esa barra.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            FaroAppTheme {
                FaroApp()
            }
        }
    }
}

/**
 * Raíz de la interfaz. Decide qué pantalla toca según el estado (patrón State):
 *   1. Bienvenida: explica qué hace la app y pide aceptarlo (consentimiento informado).
 *   2. Entrada: número de teléfono + código.
 *   3. App principal.
 */
@Composable
fun FaroApp() {
    val context = LocalContext.current
    val session = remember { Session(context) }
    var welcomed by remember { mutableStateOf(session.welcomed) }
    var token by remember { mutableStateOf(session.token) }
    var loginNotice by remember { mutableStateOf<String?>(null) }
    val status by Tracker.status.collectAsState()

    fun logout(notice: String?) {
        // Cerrar sesión detiene el envío, borra los datos guardados y vuelve a la entrada
        context.stopService(Intent(context, LocationService::class.java))
        session.clear()
        Tracker.reset()
        loginNotice = notice
        token = null
    }

    // Al abrir la app se recupera el último envío guardado
    LaunchedEffect(Unit) { Tracker.restore(session) }

    // Si el servicio detecta que el servidor ya no acepta el token, se cierra la sesión
    LaunchedEffect(status.sessionExpired) {
        if (status.sessionExpired) logout("Tu sesión venció. Entra de nuevo.")
    }

    val currentToken = token
    if (!welcomed) {
        WelcomeScreen(onContinue = {
            session.welcomed = true
            welcomed = true
        })
    } else if (currentToken == null) {
        LoginScreen(
            session = session,
            notice = loginNotice,
            onLoggedIn = {
                loginNotice = null
                Tracker.restore(session)
                token = session.token
            },
        )
    } else {
        MainScreen(
            session = session,
            token = currentToken,
            onLogout = { logout(null) },
            onSessionExpired = { logout("Tu sesión venció. Entra de nuevo.") },
        )
    }
}

/** Pantalla principal: cabecera, cuatro secciones y barra inferior para cambiar entre ellas. */
@Composable
fun MainScreen(session: Session, token: String, onLogout: () -> Unit, onSessionExpired: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val api = remember(token) { Api(session.server, token) }

    // Las solicitudes de permiso se cargan aquí (y no dentro de su pantalla) para que
    // el punto de aviso de la barra inferior esté al día aunque se mire otra sección.
    var consents by remember { mutableStateOf<List<Consent>?>(null) }
    var consentsError by remember { mutableStateOf<String?>(null) }

    suspend fun reloadConsents() {
        try {
            consents = api.incoming()
            consentsError = null
        } catch (e: ApiException) {
            if (e.code == 401) onSessionExpired() else consentsError = e.message
        }
    }

    // Sondeo: cada 15 s se pregunta al servidor si hay solicitudes nuevas
    LaunchedEffect(api) {
        while (true) {
            reloadConsents()
            delay(15_000)
        }
    }

    val pendingCount = consents?.count { it.status == "pending" } ?: 0

    Scaffold(
        containerColor = FaroColors.Paper,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(FaroColors.Ink)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandRow(Modifier.weight(1f))
                Text(formatPhone(session.phone), color = FaroColors.Mist, fontSize = 14.sp)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FaroColors.Magenta,
                    selectedTextColor = FaroColors.Ink,
                    indicatorColor = Color(0xFFFBE6F2),
                    unselectedIconColor = FaroColors.Ink2,
                    unselectedTextColor = FaroColors.Ink2,
                )
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { IconShare(LocalContentColor.current) },
                    label = { Text("Compartir") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        Box {
                            IconPermissions(LocalContentColor.current)
                            // Punto magenta cuando alguien espera respuesta
                            if (pendingCount > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                        .size(9.dp)
                                        .background(FaroColors.Magenta, CircleShape),
                                )
                            }
                        }
                    },
                    label = { Text("Permisos") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { IconRoute(LocalContentColor.current) },
                    label = { Text("Recorrido") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { IconAccount(LocalContentColor.current) },
                    label = { Text("Cuenta") },
                    colors = colors,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (tab) {
                0 -> ShareScreen(session = session)
                1 -> PermissionsScreen(
                    consents = consents,
                    error = consentsError,
                    onAction = { consent, action ->
                        scope.launch {
                            try {
                                api.consentAction(consent.id, action)
                            } catch (e: ApiException) {
                                if (e.code == 401) onSessionExpired() else consentsError = e.message
                            }
                            reloadConsents()
                        }
                    },
                )
                2 -> RouteScreen(api = api, onSessionExpired = onSessionExpired)
                else -> AccountScreen(session = session, api = api, onLogout = onLogout, onSessionExpired = onSessionExpired)
            }
        }
    }
}
