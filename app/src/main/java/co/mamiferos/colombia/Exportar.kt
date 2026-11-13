package co.mamiferos.colombia

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import co.mamiferos.colombia.datos.TipoRespuesta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exporta la prueba de campo: un ZIP con el CSV y las fotos. */
object Exportar {
    const val NOMBRE = "S15_PruebaCampo_Ortega_Munoz"

    /** Devuelve (archivo ZIP, número de fotos, fotos sin clase real). */
    suspend fun pruebaDeCampo(context: Context): Triple<File, Int, Int> = withContext(Dispatchers.IO) {
        val registros = Servicios.bd.registros().todos().first().sortedBy { it.fecha }
        require(registros.isNotEmpty()) { "todavía no hay fotos en Mis fotos" }
        val c = Servicios.clasificador
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val q = { s: String? -> if (s == null) "" else "\"" + s.replace("\"", "\"\"") + "\"" }
        val n2 = { x: Float? -> if (x == null) "" else String.format(Locale.US, "%.4f", x) }
        var sinRevisar = 0
        val csv = StringBuilder()
        csv.append("archivo,archivo_original,clase_real,prediccion,confianza,tiempo_respuesta_ms,tiempo_detector_ms,tiempo_clasificador_ms,")
        csv.append("acierto,top2,top3,acierto_top3,tipo_respuesta,detector_animal,recortada,departamento,latitud,longitud,fecha,observador,equipo\n")
        for (r in registros) {
            val pred = c.especiePorId(r.especieModelo)?.nombre ?: r.especieModelo
            val real = when (r.tipoRespuesta) {
                TipoRespuesta.CONFIRMADA -> pred
                TipoRespuesta.CORREGIDA -> r.especieUsuario?.let { c.especiePorId(it)?.nombre ?: it }
                TipoRespuesta.NUEVA -> r.nuevaComun
                else -> null
            } ?: r.nombreArchivo?.substringBeforeLast('.')?.substringAfter('_')?.let { c.especiePorId(it)?.nombre }  // "07_jaguar.jpg"
            if (real == null) sinRevisar++
            val top = runCatching { JSONArray(r.top3) }.getOrNull()
            val nombreTop = { i: Int -> top?.optJSONObject(i)?.optString("nombre") }
            val total = r.tiempoClasificacionMs?.let { it + (r.tiempoDetectorMs ?: 0) }
            val enTop3 = real != null && (0 until (top?.length() ?: 0)).any { nombreTop(it) == real }
            csv.append(listOf(
                q("fotos/${r.id}.jpg"), q(r.nombreArchivo), q(real), q(pred), n2(r.confianzaModelo), total?.toString() ?: "",
                r.tiempoDetectorMs?.toString() ?: "", r.tiempoClasificacionMs?.toString() ?: "",
                if (real == null) "" else if (real == pred) "1" else "0",
                q(nombreTop(1)), q(nombreTop(2)), if (real == null) "" else if (enTop3) "1" else "0",
                r.tipoRespuesta, n2(r.confianzaAnimal), if (r.recortada) "1" else "0", q(r.departamento),
                r.latitud?.toString() ?: "", r.longitud?.toString() ?: "", fmt.format(Date(r.fecha)), q(r.observador), q(r.dispositivo),
            ).joinToString(",")).append('\n')
        }
        val carpeta = File(context.cacheDir, "exportar").apply { deleteRecursively(); mkdirs() }
        val zip = File(carpeta, "$NOMBRE.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("$NOMBRE.csv")); z.write(("﻿" + csv).toByteArray(Charsets.UTF_8)); z.closeEntry()
            for (r in registros) {
                val f = File(r.fotoRuta); if (!f.exists()) continue
                z.putNextEntry(ZipEntry("fotos/${r.id}.jpg")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        Triple(zip, registros.size, sinRevisar)
    }

    /** Abre "Compartir" para guardar el ZIP en Drive, enviarlo por correo, etc. */
    fun compartir(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", zip)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(zip.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "Guardar prueba de campo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
