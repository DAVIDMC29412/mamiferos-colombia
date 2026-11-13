package co.mamiferos.colombia.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.mamiferos.colombia.Procesador
import co.mamiferos.colombia.Servicios
import co.mamiferos.colombia.modelo.Hallazgo
import coil.compose.AsyncImage
import co.mamiferos.colombia.sync.Sincronizador
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Una foto en la que MegaDetector no vio un animal. [temporal] = archivo de la cámara que hay que borrar al final. */
private data class Rechazo(val uri: Uri, val deCamara: Boolean, val temporal: File?, val que: Hallazgo)

/** Los permisos se piden una sola vez cada vez que se abre la app. */
private var permisosPedidos = false

@Composable
fun PantallaInicio(alTerminarUna: (String) -> Unit, alTerminarVarias: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Servicios.prefs
    val pendientes by remember { Servicios.bd.registros().contarPendientes() }.collectAsStateWithLifecycle(0)

    var modo by remember { mutableStateOf(prefs.modoDepartamento) }
    var hechas by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var rutaTemporal by rememberSaveable { mutableStateOf<String?>(null) }
    val rechazadas = remember { mutableStateListOf<Rechazo>() }
    var guardadasLote by remember { mutableIntStateOf(0) }
    val ocupado = total > 0
    // Si se sale de la pantalla con un aviso abierto, se borran las fotos temporales de la cámara.
    DisposableEffect(Unit) { onDispose { rechazadas.forEach { it.temporal?.delete() } } }

    // Permisos de ubicación (se piden una vez al abrir).
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val lista = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) lista += Manifest.permission.ACCESS_MEDIA_LOCATION
        val faltan = lista.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (!permisosPedidos && faltan.isNotEmpty()) {
            permisosPedidos = true
            permisos.launch(faltan.toTypedArray())
        }
        // Deja el modelo cargado en segundo plano para que la primera foto sea rápida.
        launch(kotlinx.coroutines.Dispatchers.Default) { runCatching { Servicios.clasificador; Servicios.detector } }
    }

    /** Borra las fotos temporales de la cámara que quedaron rechazadas y limpia el aviso. */
    fun descartarRechazadas() {
        rechazadas.forEach { it.temporal?.delete() }
        rechazadas.clear()
    }

    /**
     * Analiza las fotos. [temporal] = archivo de la cámara (se borra al terminar, salvo si quedó rechazado).
     * [forzar] = la persona dijo "Sí hay un animal".
     */
    fun procesar(uris: List<Uri>, deCamara: Boolean, temporal: File? = null, forzar: Boolean = false) {
        if (uris.isEmpty()) return
        error = null; hechas = 0; total = uris.size
        scope.launch {
            var ultimo: String? = null
            var guardadas = 0
            for (u in uris) {
                try {
                    when (val r = Procesador.procesar(ctx, u, deCamara, forzar)) {
                        is Procesador.Resultado.Guardado -> { ultimo = r.id; guardadas++ }
                        is Procesador.Resultado.SinAnimal -> rechazadas += Rechazo(u, deCamara, temporal, r.que)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "No se pudo analizar una foto: ${e.message}"
                }
                hechas++
            }
            // La foto de la cámara se borra solo si ya se guardó (la app guarda su propia copia).
            if (temporal != null && rechazadas.none { it.temporal == temporal }) temporal.delete()
            guardadasLote = guardadas
            total = 0
            val id = ultimo
            when {
                rechazadas.isNotEmpty() -> Unit           // se queda aquí mostrando el aviso
                uris.size == 1 && id != null -> alTerminarUna(id)
                id != null -> alTerminarVarias()
            }
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val ruta = rutaTemporal ?: return@rememberLauncherForActivityResult
        val archivo = File(ruta)
        if (ok) procesar(listOf(Uri.fromFile(archivo)), deCamara = true, temporal = archivo) else archivo.delete()
        rutaTemporal = null
    }
    // Se usa el selector de documentos (no el selector de fotos) porque este conserva el GPS guardado en la foto.
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        procesar(uris.take(30), deCamara = false)
    }

    fun tomarFoto() {
        val archivo = Procesador.archivoTemporal(ctx)
        rutaTemporal = archivo.absolutePath
        try {
            camara.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", archivo))
        } catch (e: ActivityNotFoundException) {
            archivo.delete(); rutaTemporal = null
            error = "Este celular no tiene una app de cámara. Usa \"Elegir de la galería\"."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Mamíferos de Colombia")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("¿Qué mamífero es?", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Verde)
            Text(
                "Toma o elige una foto. El modelo funciona sin internet y reconoce 40 mamíferos de Colombia. " +
                    "Las fotos se guardan en el celular y se suben solas cuando haya conexión.",
                color = Gris,
            )

            SelectorDepartamento(modo) { modo = it; prefs.modoDepartamento = it }

            Button(
                onClick = { descartarRechazadas(); tomarFoto() },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Tomar foto", fontSize = 17.sp) }

            OutlinedButton(
                onClick = { descartarRechazadas(); galeria.launch(arrayOf("image/*")) },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Elegir de la galería (una o varias)", fontSize = 16.sp) }

            if (ocupado) {
                Text("Analizando foto ${minOf(hechas + 1, total)} de $total…", color = Verde)
                LinearProgressIndicator(progress = { hechas / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = Rojo) }

            // Aviso cuando MegaDetector no vio un animal: no se le pone nombre de especie.
            if (rechazadas.isNotEmpty() && !ocupado) {
                AvisoSinAnimal(
                    rechazadas = rechazadas.toList(),
                    guardadas = guardadasLote,
                    alTomarOtra = { descartarRechazadas(); tomarFoto() },
                    alElegirOtra = { descartarRechazadas(); galeria.launch(arrayOf("image/*")) },
                    alForzar = {
                        val lista = rechazadas.toList()
                        rechazadas.clear()
                        // Se vuelven a analizar sin MegaDetector (todas vienen del mismo lote).
                        procesar(lista.map { it.uri }, deCamara = lista[0].deCamara, temporal = lista[0].temporal, forzar = true)
                    },
                    alVerGuardadas = { descartarRechazadas(); alTerminarVarias() },
                    alCerrar = { descartarRechazadas() },
                )
            }

            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = VerdeClaro), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Subida a la base de datos", fontWeight = FontWeight.Bold, color = Verde)
                    when {
                        !Sincronizador.firebaseListo(ctx) ->
                            Text("Firebase no está configurado: las fotos se guardan solo en este celular.", color = Gris)
                        pendientes == 0 -> Text("Todo está subido.", color = Gris)
                        else -> Text(
                            "$pendientes foto(s) esperando internet. Se subirán solas" +
                                (if (prefs.soloWifi) " cuando haya wifi." else " cuando haya conexión."),
                            color = Gris,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvisoSinAnimal(
    rechazadas: List<Rechazo>,
    guardadas: Int,
    alTomarOtra: () -> Unit,
    alElegirOtra: () -> Unit,
    alForzar: () -> Unit,
    alVerGuardadas: () -> Unit,
    alCerrar: () -> Unit,
) {
    val una = rechazadas.size == 1 && guardadas == 0
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (una) {
                val r = rechazadas[0]
                AsyncImage(
                    model = r.uri,
                    contentDescription = "Foto analizada",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                )
                Text(
                    when (r.que) {
                        Hallazgo.PERSONA -> "Se detecta una persona, no un animal"
                        Hallazgo.VEHICULO -> "Se detecta un vehículo, no un animal"
                        else -> "No se detecta ningún animal"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Naranja,
                )
                Text(
                    "Por eso no se le pone nombre de especie. Toma o sube otra foto donde se vea el animal.",
                    color = Gris,
                )
            } else {
                Text(
                    "${rechazadas.size} de ${rechazadas.size + guardadas} fotos no tienen un animal",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Naranja,
                )
                val personas = rechazadas.count { it.que == Hallazgo.PERSONA }
                Text(
                    "No se les puso nombre de especie y no se guardaron" +
                        (if (personas > 0) " ($personas con personas)." else ".") +
                        (if (guardadas > 0) " Las otras $guardadas sí se guardaron." else ""),
                    color = Gris,
                )
            }
            Button(onClick = alTomarOtra, modifier = Modifier.fillMaxWidth()) { Text("Tomar otra foto") }
            OutlinedButton(onClick = alElegirOtra, modifier = Modifier.fillMaxWidth()) { Text("Elegir otra de la galería") }
            OutlinedButton(onClick = alForzar, modifier = Modifier.fillMaxWidth()) {
                Text(if (una) "Sí hay un animal: analizarla igual" else "Sí hay animales: analizarlas igual")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (guardadas > 0) TextButton(onClick = alVerGuardadas) { Text("Ver las guardadas") }
                TextButton(onClick = alCerrar) { Text("Cerrar") }
            }
        }
    }
}
