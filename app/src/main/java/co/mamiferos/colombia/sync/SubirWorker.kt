package co.mamiferos.colombia.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.mamiferos.colombia.Servicios
import co.mamiferos.colombia.datos.EstadoSubida
import co.mamiferos.colombia.datos.Registro
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

/** Programa la subida. Android la ejecuta apenas haya internet, aunque la app esté cerrada. */
object Sincronizador {
    private const val NOMBRE = "subir_registros"

    fun programar(context: Context) {
        val red = if (Servicios.prefs.soloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
        val trabajo = OneTimeWorkRequestBuilder<SubirWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(red).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.REPLACE, trabajo)
    }

    /** true si la app tiene configurado Firebase (existe google-services.json). */
    fun firebaseListo(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}

/**
 * Sube a Firebase todo lo pendiente:
 *  1. la foto reducida (máx. 800 px) a Firestore, colección "fotos", documento <id> — solo la primera vez;
 *  2. los datos a Firestore, colección "registros", documento <id>, con una miniatura de 400 px.
 * Todo queda en Firestore (plan gratuito Spark, sin Storage ni tarjeta).
 * Si la persona corrige la especie después, el registro vuelve a "pendiente" y solo se actualizan los datos.
 */
class SubirWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Sincronizador.firebaseListo(applicationContext)) return Result.success() // sin Firebase configurado
        val dao = Servicios.bd.registros()
        val pendientes = dao.pendientes()
        if (pendientes.isEmpty()) return Result.success()

        val auth = FirebaseAuth.getInstance()
        val uid = try {
            (auth.currentUser ?: auth.signInAnonymously().await().user)?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return Result.retry()

        var hayErrores = false
        for (r in pendientes) {
            try {
                var datos = documento(r, r.fotoUrl ?: "fotos/${r.id}", uid)
                val url = r.fotoUrl ?: subirFoto(r, uid).let { miniatura ->
                    datos = datos + ("miniatura" to miniatura)
                    "fotos/${r.id}"
                }
                FirebaseFirestore.getInstance().collection("registros").document(r.id)
                    .set(datos, SetOptions.merge()).await()
                // Si la persona cambió el registro mientras se subía, se deja pendiente para subir la versión nueva.
                val actual = dao.porId(r.id) ?: continue
                if (actual == r) {
                    dao.actualizar(actual.copy(fotoUrl = url, estadoSubida = EstadoSubida.SUBIDA, errorSubida = null))
                } else {
                    dao.actualizar(actual.copy(fotoUrl = url))
                    hayErrores = true // vuelve a intentar con la versión nueva
                }
            } catch (e: CancellationException) {
                throw e // Android detuvo la subida (por ejemplo, se fue el internet): se retoma después
            } catch (e: Exception) {
                hayErrores = true
                val actual = dao.porId(r.id) ?: continue
                dao.actualizar(actual.copy(estadoSubida = EstadoSubida.ERROR, errorSubida = e.message?.take(200)))
            }
        }
        return if (hayErrores) Result.retry() else Result.success()
    }

    /** Sube la foto reducida a la colección "fotos" y devuelve la miniatura para el registro. */
    private suspend fun subirFoto(r: Registro, uid: String): String {
        val foto = Fotos.preparar(File(r.fotoRuta))
        FirebaseFirestore.getInstance().collection("fotos").document(r.id).set(
            mapOf(
                "datos" to foto.grande,
                "ancho" to foto.ancho,
                "alto" to foto.alto,
                "uidAutor" to uid,
                "registro" to r.id,
                "creadoEn" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return foto.miniatura
    }

    private fun documento(r: Registro, fotoRef: String, uid: String): Map<String, Any?> {
        val top3 = JSONArray(r.top3).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                mapOf("id" to o.getString("id"), "nombre" to o.getString("nombre"), "p" to o.getDouble("p"))
            }
        }
        val c = Servicios.clasificador
        // Especie final según la persona: la elegida, o la nueva escrita, o la del modelo si no ha respondido.
        val especieFinal = when {
            r.nuevaComun != null -> r.nuevaComun
            r.especieUsuario != null -> c.especiePorId(r.especieUsuario)?.nombre
            else -> null
        }
        return mapOf(
            "id" to r.id,
            "fotoRef" to fotoRef,
            "fecha" to Timestamp(Date(r.fecha)),
            "latitud" to r.latitud,
            "longitud" to r.longitud,
            "origenGps" to r.origenGps,
            "departamento" to r.departamento,
            "origenDepartamento" to r.origenDepartamento,
            "top3" to top3,
            "especieModelo" to r.especieModelo,
            "especieModeloNombre" to c.especiePorId(r.especieModelo)?.nombre,
            "confianzaModelo" to r.confianzaModelo.toDouble(),
            "tipoRespuesta" to r.tipoRespuesta,
            "especieUsuario" to r.especieUsuario,
            "especieUsuarioNombre" to especieFinal,
            "nuevaComun" to r.nuevaComun,
            "nuevaCientifica" to r.nuevaCientifica,
            "observador" to r.observador,
            "dispositivo" to r.dispositivo,
            "idInstalacion" to r.idInstalacion,
            "versionModelo" to r.versionModelo,
            "confianzaAnimal" to r.confianzaAnimal?.toDouble(),
            "animalForzado" to r.animalForzado,
            "recortada" to r.recortada,
            "tiempoDetectorMs" to r.tiempoDetectorMs,
            "tiempoClasificacionMs" to r.tiempoClasificacionMs,
            "nombreArchivo" to r.nombreArchivo,
            "uidAutor" to uid,
            "actualizadoEn" to FieldValue.serverTimestamp(),
        )
    }
}
