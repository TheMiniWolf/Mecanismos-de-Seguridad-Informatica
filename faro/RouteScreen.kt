package com.taller2.faro

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/**
 * Recorrido: lo que el servidor tiene guardado de MÍ.
 * Es la parte de transparencia de la app: la persona ve exactamente los mismos
 * puntos que verán quienes tienen su permiso.
 */
@Composable
fun RouteScreen(api: Api, onSessionExpired: () -> Unit) {
    var points by remember { mutableStateOf<List<Point>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    // Se vuelve a ejecutar cada vez que cambia 'reload' (botón Actualizar)
    LaunchedEffect(reload) {
        try {
            points = api.myHistory(300)
            error = null
        } catch (e: ApiException) {
            if (e.code == 401) onSessionExpired() else error = e.message
        }
    }

    val list = points
    Column(Modifier.fillMaxSize()) {
        val currentError = error
        if (currentError != null) {
            Notice(currentError, isError = true, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }

        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (currentError == null) CircularProgressIndicator(color = FaroColors.Magenta)
            }
        } else if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Beacon(active = false, modifier = Modifier.size(120.dp))
                Spacer(Modifier.height(16.dp))
                Text("Aún no hay recorrido", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Cuando compartas tu ubicación, aquí verás los puntos que guarda el servidor.", color = FaroColors.Ink2)
                Spacer(Modifier.height(18.dp))
                FaroOutlineButton("Actualizar", onClick = { reload++ })
            }
        } else {
            // Mapa arriba, lista abajo. No hay desplazamiento común: así el dedo mueve el mapa sin mover la página.
            RouteMap(points = list, modifier = Modifier.fillMaxWidth().weight(1.15f))

            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (list.size == 1) "1 punto guardado" else "${list.size} puntos guardados",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("${distanceText(routeMeters(list))} recorridos", color = FaroColors.Ink2, fontSize = 14.sp)
                }
                TextButton(onClick = { reload++ }) { Text("Actualizar") }
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                items(list) { point ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        val millis = parseIsoMillis(point.recordedAt)
                        Text(if (millis != null) clockText(millis) else point.recordedAt, fontWeight = FontWeight.SemiBold)
                        val accuracy = point.accuracy
                        Text(
                            String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude) +
                                if (accuracy != null) "   ±${accuracy.toInt()} m" else "",
                            color = FaroColors.Ink2,
                            fontSize = 14.sp,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(FaroColors.Line))
                }
            }
        }
    }
}

/** Suma de las distancias entre puntos consecutivos. */
private fun routeMeters(points: List<Point>): Double {
    var total = 0.0
    for (i in 1 until points.size) {
        total += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
    }
    return total
}

/**
 * Mapa del recorrido: un WebView con Leaflet (la misma librería del panel web).
 *
 * Leaflet viene DENTRO de la app (carpeta assets/leaflet). El WebView la pide a una
 * dirección https "virtual" (APP_ORIGIN) y la app la responde desde sus assets.
 * Así la página tiene un origen https real y el navegador envía la cabecera Referer,
 * que OpenStreetMap exige para entregar las teselas (el fondo con calles).
 * Si no hay internet, el recorrido igual se dibuja sobre un fondo liso y se avisa.
 */
private const val APP_ORIGIN = "https://appassets.androidplatform.net/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RouteMap(points: List<Point>, modifier: Modifier = Modifier) {
    // La página solo se vuelve a generar cuando cambian los puntos
    val html = remember(points) { routeMapHtml(points) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // El WebView ocupa todo el espacio que Compose le da
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true   // Leaflet necesita JavaScript; la página es fija y propia
                settings.domStorageEnabled = true
                // Identifica la app ante el servidor de mapas, como pide la política de OpenStreetMap
                settings.userAgentString = settings.userAgentString + " FaroTaller2/1.0"
                // Las peticiones a APP_ORIGIN/leaflet/... se responden con los archivos de assets
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (!url.startsWith(APP_ORIGIN + "leaflet/")) return null   // lo demás (teselas) va a internet
                        val file = url.removePrefix(APP_ORIGIN).substringBefore('?')
                        val mime = if (file.endsWith(".css")) "text/css" else "application/javascript"
                        return try {
                            WebResourceResponse(mime, "utf-8", context.assets.open(file))
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                // Los mensajes y errores de la página salen en Logcat con la etiqueta "FaroMapa"
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("FaroMapa", message.message() + " (línea " + message.lineNumber() + ")")
                        return true
                    }
                }
                setBackgroundColor(0xFFDCE9EF.toInt())
            }
        },
        update = { webView ->
            // 'tag' recuerda qué página está cargada para no recargar el mapa sin necesidad
            if (webView.tag != html) {
                webView.tag = html
                // Origen https virtual: "leaflet/leaflet.js" se resuelve contra él y lo atiende shouldInterceptRequest
                webView.loadDataWithBaseURL(APP_ORIGIN, html, "text/html", "utf-8", null)
            }
        },
    )
}

/** Página HTML mínima con el mapa. Las coordenadas son números, así que no hay riesgo de inyección. */
private fun routeMapHtml(points: List<Point>): String {
    // El servidor entrega del más reciente al más antiguo; para dibujar la ruta se invierte
    val coords = points.reversed().joinToString(",") {
        String.format(Locale.US, "[%.6f,%.6f]", it.latitude, it.longitude)   // siempre con punto decimal
    }
    return """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<link rel="stylesheet" href="leaflet/leaflet.css">
<style>
  html, body { height: 100%; margin: 0; background: #DCE9EF; }
  /* El mapa se ancla a los 4 bordes: no depende de alturas en porcentaje */
  #map { position: absolute; top: 0; right: 0; bottom: 0; left: 0; background: #DCE9EF; }
  .beacon { width: 20px; height: 20px; border-radius: 50%; background: #C2187A; border: 3px solid #fff; box-shadow: 0 0 0 6px rgba(194,24,122,.25), 0 1px 5px rgba(0,0,0,.4); box-sizing: border-box; }
  #status { position: absolute; left: 12px; right: 12px; bottom: 12px; z-index: 1000; padding: 8px 12px; border-radius: 8px; background: rgba(15,42,61,.88); color: #fff; font: 13px sans-serif; display: none; }
</style>
</head>
<body>
<div id="map"></div>
<div id="status"></div>
<script>
  // Cualquier error de JavaScript se muestra en el mapa en vez de dejarlo en blanco
  function say(text) { var s = document.getElementById('status'); s.textContent = text; s.style.display = text ? 'block' : 'none'; }
  window.onerror = function (msg) { say('Error del mapa: ' + msg); };
</script>
<script src="leaflet/leaflet.js"></script>
<script>
  var pts = [$coords];
  if (!window.L) {
    say('No se encontró Leaflet dentro de la app (carpeta assets/leaflet).');
  } else {
    var map = L.map('map', { zoomControl: false });
    var last = pts[pts.length - 1];

    // Fondo del mapa: OpenStreetMap, el mismo del panel web (gratis, sin clave)
    var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      { maxZoom: 19, attribution: '&copy; OpenStreetMap' });
    var loaded = 0, failed = 0;
    tiles.on('tileload', function () { loaded++; if (failed) say(''); failed = 0; });
    tiles.on('tileerror', function () {
      failed++;
      if (!loaded) say('No se pudo descargar el fondo del mapa. Revisa que el teléfono tenga internet; el recorrido sí se dibuja.');
    });
    tiles.addTo(map);

    L.polyline(pts, { color: '#ffffff', weight: 8, opacity: 0.9 }).addTo(map);
    L.polyline(pts, { color: '#C2187A', weight: 4 }).addTo(map);
    for (var i = 0; i < pts.length - 1; i++) {
      L.circleMarker(pts[i], { radius: 4, color: '#C2187A', weight: 2, fillColor: '#ffffff', fillOpacity: 1 }).addTo(map);
    }
    L.marker(last, { icon: L.divIcon({ className: '', html: '<div class="beacon"></div>', iconSize: [20, 20], iconAnchor: [10, 10] }) }).addTo(map);

    // Encuadre: si todos los puntos son el mismo lugar (área cero) se centra en él
    var moved = false;
    map.on('dragstart', function () { moved = true; });
    function fit() {
      map.invalidateSize();   // vuelve a medir el contenedor (el WebView puede crecer después de cargar)
      var b = L.latLngBounds(pts);
      if (pts.length > 1 && !b.getNorthEast().equals(b.getSouthWest())) {
        map.fitBounds(b.pad(0.2), { maxZoom: 17 });
      } else {
        map.setView(last, 16);
      }
    }
    fit();
    // Si el WebView cambia de tamaño (al abrir la pestaña, girar el teléfono...) se encuadra de nuevo
    window.addEventListener('resize', function () { if (!moved) fit(); else map.invalidateSize(); });
    setTimeout(function () { if (!moved) fit(); }, 400);
  }
</script>
</body>
</html>
""".trimIndent()
}
