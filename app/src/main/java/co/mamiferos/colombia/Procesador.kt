package co.mamiferos.colombia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import co.mamiferos.colombia.datos.Registro
import co.mamiferos.colombia.modelo.Departamento
import co.mamiferos.colombia.modelo.Hallazgo
import co.mamiferos.colombia.sync.Sincronizador
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Convierte una foto en un registro guardado: lee, clasifica, aplica la ubicación y guarda. */
object Procesador {

    private const val LADO_MAXIMO = 1600   // la foto guardada no supera 1600 px (ahorra memoria y datos)

    fun carpetaFotos(context: Context): File = File(context.filesDir, "fotos").apply { mkdirs() }

    /** Archivo temporal donde la cámara deja la foto. */
    fun archivoTemporal(context: Context): File = File(carpetaFotos(context), "tmp_${UUID.randomUUID()}.jpg")

    /** Lo que pasó con una foto. */
    sealed class Resultado {
        /** Había un animal (o la persona lo confirmó): se guardó el registro [id]. */
        data class Guardado(val id: String) : Resultado()
        /** MegaDetector no vio un animal: NO se clasifica ni se guarda. [que] = PERSONA, VEHICULO o NADA. */
        data class SinAnimal(val que: Hallazgo, val puntajeAnimal: Float) : Resultado()
    }

    /**
     * Procesa una foto.
     * [deCamara] = true si se acaba de tomar (entonces se usa el GPS actual del celular).
     * [forzar] = true si la persona dijo "Sí hay un animal": se salta la revisión de MegaDetector.
     */
    suspend fun procesar(context: Context, uri: Uri, deCamara: Boolean, forzar: Boolean = false): Resultado =
        withContext(Dispatchers.Default) {
        val (foto, gpsFoto) = leerFoto(context, uri)

        // 1. ¿Hay un animal? (MegaDetector). Si no hay, no se le pone nombre de especie.
        val detector = if (forzar) null else Servicios.detector
        val t0 = System.nanoTime()
        val deteccion = detector?.detectar(foto)
        val msDetector = if (detector != null) (System.nanoTime() - t0) / 1_000_000 else null
        if (deteccion != null && deteccion.hallazgo != Hallazgo.ANIMAL) {
            foto.recycle()
            return@withContext Resultado.SinAnimal(deteccion.hallazgo, deteccion.animal)
        }

        val id = UUID.randomUUID().toString()

        // 2. Guardar una copia reducida de la foto completa dentro de la app.
        val archivo = File(carpetaFotos(context), "$id.jpg")
        FileOutputStream(archivo).use { foto.compress(Bitmap.CompressFormat.JPEG, 85, it) }

        // 3. Clasificar el recorte del animal (como en el dataset) o, si no hay caja, la foto completa.
        val clasificador = Servicios.clasificador
        val recorte = deteccion?.caja?.let { detector?.recortar(foto, it) }
        val t1 = System.nanoTime()
        val pImagen = clasificador.clasificar(recorte ?: foto)
        var nsClasificacion = System.nanoTime() - t1
        if (recorte != null && recorte !== foto) recorte.recycle()
        foto.recycle()

        // 4. Dónde se tomó: GPS guardado en la foto, o GPS actual si se acaba de tomar.
        var lat: Double? = gpsFoto?.get(0)
        var lon: Double? = gpsFoto?.get(1)
        var origenGps: String? = if (gpsFoto != null) "foto" else null
        if (gpsFoto == null && deCamara) {
            ubicacionActual(context)?.let { (a, b) -> lat = a; lon = b; origenGps = "celular" }
        }

        // 5. Departamento y ajuste por ubicación.
        val (dep, origenDep) = elegirDepartamento(context, lat, lon)
        val t2 = System.nanoTime()
        val p = Servicios.ubicacion.aplicar(pImagen, dep)
        val top = clasificador.mejores(p, 3)
        nsClasificacion += System.nanoTime() - t2

        val top3 = JSONArray().apply {
            top.forEach { put(JSONObject().put("id", it.especie.id).put("nombre", it.especie.nombre).put("p", it.probabilidad.toDouble())) }
        }
        val prefs = Servicios.prefs
        val registro = Registro(
            id = id,
            fotoRuta = archivo.absolutePath,
            fecha = System.currentTimeMillis(),
            latitud = lat,
            longitud = lon,
            origenGps = origenGps,
            departamento = dep?.nombre,
            origenDepartamento = origenDep,
            top3 = top3.toString(),
            especieModelo = top[0].especie.id,
            confianzaModelo = top[0].probabilidad,
            observador = prefs.observador.ifBlank { null },
            dispositivo = prefs.dispositivo,
            idInstalacion = prefs.idInstalacion,
            versionModelo = clasificador.version + (detector?.let { " + " + it.version } ?: ""),
            confianzaAnimal = deteccion?.animal,
            animalForzado = forzar,
            recortada = recorte != null,
            tiempoDetectorMs = msDetector,
            tiempoClasificacionMs = nsClasificacion / 1_000_000,
            nombreArchivo = if (deCamara) null else nombreDe(context, uri),
        )
        Servicios.bd.registros().guardar(registro)
        Sincronizador.programar(context)
        Resultado.Guardado(id)
    }

    /** Nombre original del archivo elegido (por ejemplo "07_jaguar.jpg"), o null si no se puede leer. */
    private fun nombreDe(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Lee la foto reducida, la endereza según la cámara y devuelve también el GPS guardado en ella. */
    private fun leerFoto(context: Context, uri: Uri): Pair<Bitmap, DoubleArray?> {
        val cr = context.contentResolver

        // Tamaño original, sin cargar la foto completa en memoria.
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestreo = 1
        while (max(limites.outWidth, limites.outHeight) / (muestreo * 2) >= LADO_MAXIMO) muestreo *= 2

        var bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestreo })
        } ?: error("No se pudo abrir la foto")

        // Orientación y GPS guardados en la foto (EXIF).
        var giro = 0f
        var gps: DoubleArray? = null
        try {
            abrirOriginal(context, uri)?.use { s ->
                val exif = ExifInterface(s)
                giro = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                gps = exif.latLong
            }
        } catch (_: Exception) {
        }

        val escala = minOf(1f, LADO_MAXIMO.toFloat() / max(bmp.width, bmp.height))
        if (giro != 0f || escala < 1f) {
            val m = Matrix().apply { postScale(escala, escala); postRotate(giro) }
            val nueva = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (nueva !== bmp) bmp.recycle()
            bmp = nueva
        }
        return bmp to gps
    }

    /** Pide la versión original de la foto (con GPS) cuando Android lo permite. */
    private fun abrirOriginal(context: Context, uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                // Un documento elegido en la galería se convierte a su dirección de MediaStore para pedir la original.
                val media = if (uri.authority == MediaStore.AUTHORITY) uri
                else if (DocumentsContract.isDocumentUri(context, uri)) MediaStore.getMediaUri(context, uri) else null
                if (media != null) return context.contentResolver.openInputStream(MediaStore.setRequireOriginal(media))
            } catch (_: Exception) {
            }
        }
        return context.contentResolver.openInputStream(uri)
    }

    /** GPS actual del celular. Funciona sin internet (usa los satélites); espera máximo 10 s. */
    @SuppressLint("MissingPermission")
    suspend fun ubicacionActual(context: Context): Pair<Double, Double>? {
        val permitido = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permitido) return null
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        val cancelar = CancellationTokenSource()
        return try {
            val actual = withTimeoutOrNull(10_000) {
                cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelar.token).await()
            } ?: cliente.lastLocation.await()
            actual?.let { it.latitude to it.longitude }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            cancelar.cancel() // apaga el GPS si se acabó el tiempo
        }
    }

    /**
     * Decide qué departamento usar para el ajuste.
     * - Modo "auto": traduce el GPS a departamento (necesita internet); si no se puede,
     *   usa el último departamento que detectó el GPS.
     * - Modo "ninguno": sin ajuste por ubicación.
     * - Otro valor: el departamento que eligió la persona.
     */
    suspend fun elegirDepartamento(context: Context, lat: Double?, lon: Double?): Pair<Departamento?, String?> {
        val prefs = Servicios.prefs
        val ub = Servicios.ubicacion
        return when (val modo = prefs.modoDepartamento) {
            "ninguno" -> null to null
            "auto" -> {
                val desdeGps = if (lat != null && lon != null) ub.desdeTextoGps(nombreRegion(context, lat, lon)) else null
                if (desdeGps != null) {
                    prefs.ultimoDepartamentoGps = desdeGps.nombre
                    desdeGps to "gps"
                } else {
                    // Sin internet para traducir el GPS: último departamento detectado (si alguna vez hubo uno).
                    val ultimo = ub.porNombre(prefs.ultimoDepartamentoGps)
                    ultimo to (if (ultimo != null) "ultimo_conocido" else null)
                }
            }
            else -> ub.porNombre(modo) to "elegido"
        }
    }

    /** Nombre del departamento según el GPS (Geocoder de Android; normalmente necesita internet). */
    @Suppress("DEPRECATION")
    private suspend fun nombreRegion(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val dir = Geocoder(context, Locale("es", "CO")).getFromLocation(lat, lon, 1)?.firstOrNull()
            if (dir?.countryCode != null && dir.countryCode != "CO") null else dir?.adminArea
        } catch (_: Exception) {
            null
        }
    }

    fun porcentaje(p: Float): String = "${(p * 1000).roundToInt() / 10.0} %".replace('.', ',')
}
