package co.mamiferos.colombia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.mamiferos.colombia.Procesador
import co.mamiferos.colombia.Servicios
import co.mamiferos.colombia.datos.EstadoSubida
import co.mamiferos.colombia.datos.Registro
import co.mamiferos.colombia.datos.TipoRespuesta
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Colores ----------
val Verde = Color(0xFF24452F)
val VerdeClaro = Color(0xFFEEF3EC)
val Naranja = Color(0xFFC7741B)
val Rojo = Color(0xFFB3261E)
val Gris = Color(0xFF5A6B5F)

@Composable
fun Tema(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Verde, onPrimary = Color.White,
            secondary = Naranja, onSecondary = Color.White,
            surfaceVariant = VerdeClaro, background = Color.White, surface = Color.White,
        ),
        content = contenido,
    )
}

// ---------- Datos de un registro, listos para mostrar ----------
data class OpcionGuardada(val id: String, val nombre: String, val p: Float)

fun Registro.opciones(): List<OpcionGuardada> {
    val a = JSONArray(top3)
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        OpcionGuardada(o.getString("id"), o.getString("nombre"), o.getDouble("p").toFloat())
    }
}

/** Nombre que se muestra: la respuesta de la persona si existe; si no, lo que dijo el modelo. */
fun Registro.nombreMostrado(): String = when {
    nuevaComun != null -> nuevaComun
    especieUsuario != null -> Servicios.clasificador.especiePorId(especieUsuario)?.nombre ?: especieUsuario
    else -> Servicios.clasificador.especiePorId(especieModelo)?.nombre ?: especieModelo
}

fun textoRespuesta(tipo: String): Pair<String, Color> = when (tipo) {
    TipoRespuesta.CONFIRMADA -> "Confirmada" to Verde
    TipoRespuesta.CORREGIDA -> "Corregida" to Naranja
    TipoRespuesta.NUEVA -> "Especie nueva" to Naranja
    else -> "Sin revisar" to Gris
}

fun textoSubida(estado: String): Pair<String, Color> = when (estado) {
    EstadoSubida.SUBIDA -> "Subida" to Verde
    EstadoSubida.ERROR -> "Error al subir (se reintenta)" to Rojo
    else -> "Esperando internet" to Gris
}

/** Mismos niveles de confianza que la app web. */
fun nivelConfianza(p: Float): Triple<String, String, Color> = when {
    p >= 0.85f -> Triple("Confianza alta", "El modelo está seguro de esta especie.", Verde)
    p >= 0.5f -> Triple("Confianza media", "Revisa las otras opciones: si la correcta está en la lista, elígela.", Naranja)
    else -> Triple(
        "Confianza baja",
        "Elige abajo la especie correcta entre las 3 opciones. Si no es ninguna, búscala en la lista de 40 o escribe el nombre del animal.",
        Rojo,
    )
}

fun fechaTexto(ms: Long): String = SimpleDateFormat("d MMM yyyy, h:mm a", Locale("es", "CO")).format(Date(ms))

fun porcentaje(p: Float) = Procesador.porcentaje(p)

// ---------- Piezas de interfaz ----------
@Composable
fun Etiqueta(texto: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Encabezado(titulo: String, alVolver: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Verde)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (alVolver != null) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(titulo, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BarraProbabilidad(p: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(VerdeClaro, RoundedCornerShape(50)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(p.coerceIn(0.02f, 1f))
                .height(8.dp)
                .background(color, RoundedCornerShape(50)),
        )
    }
}

/** Selector del departamento: automático por GPS, sin ubicación, o uno de la lista. */
@Composable
fun SelectorDepartamento(modo: String, alCambiar: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val texto = when (modo) {
        "auto" -> "Automático por GPS"
        "ninguno" -> "Sin ubicación"
        else -> modo
    }
    Column {
        Text("Departamento para el ajuste por ubicación", fontSize = 13.sp, color = Gris)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
                Text(texto, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                DropdownMenuItem(text = { Text("Automático por GPS") }, onClick = { alCambiar("auto"); abierto = false })
                DropdownMenuItem(text = { Text("Sin ubicación") }, onClick = { alCambiar("ninguno"); abierto = false })
                HorizontalDivider()
                Servicios.ubicacion.departamentos.forEach { d ->
                    DropdownMenuItem(text = { Text(d.nombre) }, onClick = { alCambiar(d.nombre); abierto = false })
                }
            }
        }
    }
}

@Composable
fun FilaDato(titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titulo, color = Gris, fontSize = 14.sp, modifier = Modifier.weight(0.42f))
        Text(valor, fontSize = 14.sp, modifier = Modifier.weight(0.58f))
    }
}
