package co.mamiferos.colombia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.mamiferos.colombia.Servicios
import co.mamiferos.colombia.datos.Registro
import co.mamiferos.colombia.sync.Fotos
import co.mamiferos.colombia.sync.Sincronizador
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

// ======================= MIS FOTOS =======================
@Composable
fun PantallaHistorial(alAbrir: (String) -> Unit) {
    val ctx = LocalContext.current
    val lista by remember { Servicios.bd.registros().todos() }.collectAsStateWithLifecycle(emptyList())
    val pendientes = lista.count { it.estadoSubida != "subida" }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Mis fotos (${lista.size})")
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    !Sincronizador.firebaseListo(ctx) -> "Guardadas solo en este celular (Firebase no configurado)."
                    pendientes == 0 -> "Todo está subido."
                    else -> "$pendientes sin subir."
                },
                color = Gris, modifier = Modifier.weight(1f), fontSize = 14.sp,
            )
            if (pendientes > 0) OutlinedButton(onClick = { Sincronizador.programar(ctx) }) { Text("Subir ahora") }
        }
        if (lista.isEmpty()) {
            Text("Todavía no hay fotos. Ve a Identificar y toma la primera.", Modifier.padding(16.dp), color = Gris)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lista, key = { it.id }) { r -> FilaRegistro(r) { alAbrir(r.id) } }
        }
    }
}

@Composable
private fun FilaRegistro(r: Registro, alTocar: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = alTocar), colors = CardDefaults.cardColors(containerColor = VerdeClaro)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = File(r.fotoRuta), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.nombreMostrado(), fontWeight = FontWeight.Bold, color = Verde)
                Text("${porcentaje(r.confianzaModelo)} · ${fechaTexto(r.fecha)}", fontSize = 13.sp, color = Gris)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (t1, c1) = textoRespuesta(r.tipoRespuesta)
                    val (t2, c2) = textoSubida(r.estadoSubida)
                    Etiqueta(t1, c1)
                    Etiqueta(t2, c2)
                }
            }
        }
    }
}

// ======================= COMUNIDAD =======================
private data class FilaNube(
    val miniatura: String?, val especie: String, val confianza: Double?, val departamento: String?,
    val dispositivo: String?, val observador: String?, val fecha: Long?, val validacion: String,
)

/** Fotos subidas por todas las personas (necesita internet). */
@Composable
fun PantallaComunidad() {
    val ctx = LocalContext.current
    var filas by remember { mutableStateOf<List<FilaNube>>(emptyList()) }
    var mensaje by remember { mutableStateOf<String?>("Cargando…") }
    var recargar by remember { mutableIntStateOf(0) }

    LaunchedEffect(recargar) {
        if (!Sincronizador.firebaseListo(ctx)) {
            mensaje = "Firebase no está configurado. Cuando lo configuren, aquí se verán las fotos de todas las personas."
            return@LaunchedEffect
        }
        mensaje = "Cargando…"
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val docs = FirebaseFirestore.getInstance().collection("registros")
                .orderBy("fecha", Query.Direction.DESCENDING).limit(50).get().await()
            filas = docs.documents.map { d ->
                FilaNube(
                    miniatura = d.getString("miniatura"),
                    especie = d.getString("especieUsuarioNombre") ?: d.getString("especieModeloNombre") ?: "—",
                    confianza = d.getDouble("confianzaModelo"),
                    departamento = d.getString("departamento"),
                    dispositivo = d.getString("dispositivo"),
                    observador = d.getString("observador"),
                    fecha = d.getTimestamp("fecha")?.toDate()?.time,
                    validacion = d.getString("validacion") ?: "pendiente",
                )
            }
            mensaje = if (filas.isEmpty()) "Todavía nadie ha subido fotos." else null
        } catch (e: Exception) {
            mensaje = "No se pudo conectar. Revisa el internet e intenta de nuevo."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Comunidad")
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Últimas 50 fotos subidas desde todos los equipos.", color = Gris, fontSize = 14.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { recargar++ }) { Text("Actualizar") }
        }
        mensaje?.let { Text(it, Modifier.padding(16.dp), color = Gris) }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filas) { f ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = VerdeClaro)) {
                    Column {
                        val imagen = remember(f.miniatura) { f.miniatura?.let { Fotos.decodificar(it)?.asImageBitmap() } }
                        if (imagen != null) {
                            Image(
                                imagen, contentDescription = f.especie, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(190.dp),
                            )
                        }
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(f.especie, fontWeight = FontWeight.Bold, color = Verde, modifier = Modifier.weight(1f))
                                Etiqueta(
                                    when (f.validacion) { "validada" -> "Validada"; "rechazada" -> "Rechazada"; else -> "Por revisar" },
                                    when (f.validacion) { "validada" -> Verde; "rechazada" -> Rojo; else -> Gris },
                                )
                            }
                            val partes = listOfNotNull(
                                f.confianza?.let { porcentaje(it.toFloat()) },
                                f.departamento,
                                f.fecha?.let { fechaTexto(it) },
                            )
                            Text(partes.joinToString(" · "), fontSize = 13.sp, color = Gris)
                            Text("Equipo: ${f.dispositivo ?: "—"}" + (f.observador?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Gris)
                        }
                    }
                }
            }
        }
    }
}

// ======================= AJUSTES =======================
@Composable
fun PantallaAjustes() {
    val ctx = LocalContext.current
    val prefs = Servicios.prefs
    var observador by remember { mutableStateOf(prefs.observador) }
    var soloWifi by remember { mutableStateOf(prefs.soloWifi) }
    var modo by remember { mutableStateOf(prefs.modoDepartamento) }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Ajustes")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                observador, { observador = it; prefs.observador = it },
                label = { Text("Tu nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Se guarda con cada foto para saber quién la tomó.") },
            )
            SelectorDepartamento(modo) { modo = it; prefs.modoDepartamento = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Subir solo con wifi", fontWeight = FontWeight.SemiBold)
                    Text("Para no gastar datos móviles.", color = Gris, fontSize = 13.sp)
                }
                Switch(soloWifi, { soloWifi = it; prefs.soloWifi = it; Sincronizador.programar(ctx) })
            }
            Button(onClick = { Sincronizador.programar(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Subir pendientes ahora") }

            HorizontalDivider()
            Text("Prueba de campo", fontWeight = FontWeight.Bold)
            Text("Crea un ZIP con todas las fotos y un CSV (archivo, clase real, predicción, confianza y tiempo de respuesta). " +
                 "La clase real es la que confirmaste o corregiste en cada foto.", color = Gris, fontSize = 13.sp)
            var exportando by remember { mutableStateOf(false) }
            var mensajeExportar by remember { mutableStateOf<String?>(null) }
            val alcance = androidx.compose.runtime.rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    exportando = true; mensajeExportar = null
                    alcance.launch {
                        mensajeExportar = try {
                            val (zip, n, sinRevisar) = co.mamiferos.colombia.Exportar.pruebaDeCampo(ctx)
                            co.mamiferos.colombia.Exportar.compartir(ctx, zip)
                            "$n fotos exportadas" + if (sinRevisar > 0) " · $sinRevisar sin clase real (confírmalas en Mis fotos)" else ""
                        } catch (e: Exception) {
                            "No se pudo exportar: ${e.message}"
                        }
                        exportando = false
                    }
                },
                enabled = !exportando, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (exportando) "Exportando…" else "Exportar prueba de campo (ZIP)") }
            mensajeExportar?.let { Text(it, color = Gris, fontSize = 13.sp) }

            HorizontalDivider()
            Text("Información", fontWeight = FontWeight.Bold)
            FilaDato("Modelo", Servicios.clasificador.version)
            FilaDato("Especies", "${Servicios.clasificador.especies.size}")
            FilaDato(
                "Detector de animales",
                if (co.mamiferos.colombia.modelo.Detector.existe(ctx)) "MegaDetector V6 activo"
                else "No instalado (falta megadetector.tflite): no se revisa si hay animal",
            )
            FilaDato("Equipo", prefs.dispositivo)
            FilaDato("ID de instalación", prefs.idInstalacion.take(8))
            FilaDato("Base de datos", if (Sincronizador.firebaseListo(ctx)) "Firebase conectado" else "Firebase no configurado")
        }
    }
}
