package co.mamiferos.colombia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.mamiferos.colombia.Servicios
import co.mamiferos.colombia.datos.Registro
import co.mamiferos.colombia.datos.TipoRespuesta
import co.mamiferos.colombia.modelo.Ubicacion
import co.mamiferos.colombia.sync.Sincronizador
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun PantallaDetalle(id: String, alVolver: () -> Unit) {
    val dao = Servicios.bd.registros()
    val registro by remember(id) { dao.observar(id) }.collectAsStateWithLifecycle(null)
    val r = registro

    Column(Modifier.fillMaxSize()) {
        Encabezado("Resultado", alVolver)
        if (r == null) {
            Text("Cargando…", Modifier.padding(20.dp), color = Gris)
        } else {
            ContenidoDetalle(r, alVolver)
        }
    }
}

@Composable
private fun ContenidoDetalle(r: Registro, alVolver: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = Servicios.bd.registros()
    var dialogoLista by remember { mutableStateOf(false) }
    var dialogoNueva by remember { mutableStateOf(false) }
    var dialogoBorrar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        /** Guarda la respuesta de la persona y programa la subida. */
        fun responder(tipo: String, especie: String?, comun: String? = null, cientifica: String? = null) {
            scope.launch {
                dao.responder(r.id, tipo, especie, comun, cientifica)
                Sincronizador.programar(ctx)
            }
        }

        val opciones = r.opciones()
        val primera = opciones.first()
        val especie = Servicios.clasificador.especiePorId(primera.id)
        val (nivel, consejo, colorNivel) = nivelConfianza(primera.p)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = File(r.fotoRuta),
                contentDescription = "Foto analizada",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)),
            )

            // ---- Lo que dijo el modelo ----
            Column {
                Text(
                    if (primera.p < 0.5f) "Hay un animal, pero no estoy seguro de la especie. Puede ser:" else "El modelo dice",
                    color = if (primera.p < 0.5f) Rojo else Gris, fontSize = 13.sp,
                )
                Text(primera.nombre, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Verde)
                especie?.let { Text(it.cientifico, fontStyle = FontStyle.Italic, color = Gris) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Etiqueta(porcentaje(primera.p), colorNivel)
                    if (especie?.endemica == true) Etiqueta("Endémica", Naranja)
                    especie?.uicn?.takeIf { it in listOf("CR", "EN", "VU", "NT", "DD") }?.let { Etiqueta("UICN $it", Rojo) }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = colorNivel.copy(alpha = 0.10f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(nivel, fontWeight = FontWeight.Bold, color = colorNivel)
                    Text(consejo, fontSize = 14.sp)
                }
            }
            if (r.animalForzado) {
                Text("El detector no vio un animal; se analizó porque indicaste que sí hay uno.", color = Naranja, fontSize = 13.sp)
            } else if (r.recortada) {
                Text("Se analizó el recorte donde el detector encontró el animal.", color = Gris, fontSize = 13.sp)
            }
            r.departamento?.let { d ->
                Text("Ajustado con la ubicación: $d (${origenDep(r.origenDepartamento)})", color = Gris, fontSize = 13.sp)
            }

            // ---- La persona confirma o corrige ----
            HorizontalDivider()
            Text("¿Cuál es el animal de la foto?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Toca la especie correcta. Tu respuesta ayuda a que el modelo aprenda.", color = Gris, fontSize = 14.sp)
            opciones.forEachIndexed { i, o ->
                val elegida = (r.tipoRespuesta == TipoRespuesta.CONFIRMADA || r.tipoRespuesta == TipoRespuesta.CORREGIDA) && r.especieUsuario == o.id
                OpcionTop(o, i + 1, elegida) {
                    responder(if (i == 0) TipoRespuesta.CONFIRMADA else TipoRespuesta.CORREGIDA, o.id)
                }
            }
            OutlinedButton(onClick = { dialogoLista = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Es otra especie de la lista de 40")
            }
            OutlinedButton(onClick = { dialogoNueva = true }, modifier = Modifier.fillMaxWidth()) {
                Text("No está en la lista: escribir su nombre")
            }
            RespuestaActual(r)

            // ---- Datos del registro ----
            HorizontalDivider()
            Text("Datos del registro", fontWeight = FontWeight.Bold)
            FilaDato("Fecha", fechaTexto(r.fecha))
            FilaDato(
                "GPS",
                if (r.latitud != null && r.longitud != null)
                    String.format(Locale.US, "%.5f, %.5f", r.latitud, r.longitud) + (if (r.origenGps == "foto") " (de la foto)" else "")
                else "Sin GPS",
            )
            FilaDato("Departamento", r.departamento ?: "Sin ubicación")
            FilaDato(
                "Detector",
                when {
                    r.animalForzado -> "Confirmado por la persona"
                    r.confianzaAnimal != null -> "Animal detectado (${porcentaje(r.confianzaAnimal)})"
                    else -> "No se usó"
                },
            )
            r.tiempoClasificacionMs?.let { ms ->
                FilaDato("Tiempo de respuesta", "${ms + (r.tiempoDetectorMs ?: 0)} ms" +
                    (r.tiempoDetectorMs?.let { " (detector $it ms · especie $ms ms)" } ?: ""))
            }
            FilaDato("Equipo", r.dispositivo)
            r.observador?.let { FilaDato("Observador", it) }
            val (sub, colorSub) = textoSubida(r.estadoSubida)
            FilaDato("Estado", sub)
            r.errorSubida?.let { Text(it, color = colorSub, fontSize = 12.sp) }

            TextButton(onClick = { dialogoBorrar = true }) { Text("Borrar de este celular", color = Rojo) }
        }

        if (dialogoLista) {
            DialogoLista(
                alElegir = { e ->
                    dialogoLista = false
                    responder(if (e == primera.id) TipoRespuesta.CONFIRMADA else TipoRespuesta.CORREGIDA, e)
                },
                alCerrar = { dialogoLista = false },
            )
        }
        if (dialogoNueva) {
            DialogoNueva(
                inicialComun = r.nuevaComun ?: "", inicialCientifica = r.nuevaCientifica ?: "",
                alGuardar = { comun, cientifica ->
                    dialogoNueva = false
                    responder(TipoRespuesta.NUEVA, null, comun, cientifica.ifBlank { null })
                },
                alCerrar = { dialogoNueva = false },
            )
        }
        if (dialogoBorrar) {
            AlertDialog(
                onDismissRequest = { dialogoBorrar = false },
                title = { Text("¿Borrar este registro?") },
                text = { Text("Se borra solo de este celular. Si ya se subió, sigue en la base de datos.") },
                confirmButton = {
                    TextButton(onClick = {
                        dialogoBorrar = false
                        scope.launch {
                            File(r.fotoRuta).delete()
                            dao.borrar(r.id)
                            alVolver()
                        }
                    }) { Text("Borrar", color = Rojo) }
                },
                dismissButton = { TextButton(onClick = { dialogoBorrar = false }) { Text("Cancelar") } },
            )
        }
    }
}

private fun origenDep(o: String?) = when (o) {
    "gps" -> "detectado por GPS"
    "elegido" -> "elegido a mano"
    "ultimo_conocido" -> "último detectado; sin internet para leer el GPS"
    else -> "—"
}

@Composable
private fun OpcionTop(o: OpcionGuardada, puesto: Int, elegida: Boolean, alTocar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = alTocar),
        colors = CardDefaults.cardColors(containerColor = if (elegida) VerdeClaro else Color.White),
        border = BorderStroke(if (elegida) 2.dp else 1.dp, if (elegida) Verde else Color(0xFFD5DED5)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$puesto. ${o.nombre}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(porcentaje(o.p), color = Gris)
                if (elegida) Icon(Icons.Filled.Check, "Elegida", tint = Verde, modifier = Modifier.padding(start = 6.dp))
            }
            BarraProbabilidad(o.p, if (puesto == 1) Verde else Naranja)
        }
    }
}

@Composable
private fun RespuestaActual(r: Registro) {
    val (texto, color) = textoRespuesta(r.tipoRespuesta)
    val detalle = when (r.tipoRespuesta) {
        TipoRespuesta.NUEVA -> r.nuevaComun.orEmpty() + (r.nuevaCientifica?.let { " ($it)" } ?: "")
        TipoRespuesta.CONFIRMADA, TipoRespuesta.CORREGIDA -> r.nombreMostrado()
        else -> "Todavía no has respondido."
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tu respuesta:", color = Gris)
        Etiqueta(texto, color)
    }
    Text(detalle, fontWeight = FontWeight.SemiBold)
}

/** Lista de las 40 especies con buscador. */
@Composable
private fun DialogoLista(alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val lista = Servicios.clasificador.especies
        .sortedBy { it.nombre }
        .filter {
            buscar.isBlank() || Ubicacion.normalizar(it.nombre).contains(Ubicacion.normalizar(buscar)) ||
                it.cientifico.lowercase().contains(buscar.lowercase())
        }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Elige la especie") },
        text = {
            Column {
                OutlinedTextField(buscar, { buscar = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(lista, key = { it.id }) { e ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { alElegir(e.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(e.nombre, fontWeight = FontWeight.SemiBold)
                            Text(e.cientifico, fontStyle = FontStyle.Italic, color = Gris, fontSize = 13.sp)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** Animal que no está entre las 40 especies: nombre común (obligatorio) y científico (opcional). */
@Composable
private fun DialogoNueva(inicialComun: String, inicialCientifica: String, alGuardar: (String, String) -> Unit, alCerrar: () -> Unit) {
    var comun by remember { mutableStateOf(inicialComun) }
    var cientifica by remember { mutableStateOf(inicialCientifica) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Especie que no está en la lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("La foto quedará pendiente de revisión. Cuando haya suficientes fotos de esta especie, se podrá agregar al modelo.", fontSize = 14.sp, color = Gris)
                OutlinedTextField(comun, { comun = it }, label = { Text("Nombre común *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cientifica, { cientifica = it }, label = { Text("Nombre científico (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(comun.trim(), cientifica.trim()) }, enabled = comun.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
