package co.mamiferos.colombia.modelo

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Una de las 40 especies, en el mismo orden de las salidas del modelo. */
data class Especie(
    val id: String,
    val nombre: String,
    val cientifico: String,
    val endemica: Boolean,
    val uicn: String,
)

/** Una opción del resultado: especie y su probabilidad (0..1). */
data class Opcion(val especie: Especie, val probabilidad: Float)

/**
 * El modelo estudiante (MobileNetV3-Large destilado, TFLite float16, 6,1 MB).
 * Entrada: foto RGB de 224 × 224 con valores 0..255 (el modelo normaliza por dentro).
 * Salida: 40 probabilidades que suman 1.
 */
class Clasificador(context: Context) {

    val especies: List<Especie> = leerEspecies(context)
    val version = "MobileNetV3-Large destilado · TFLite float16 · v1"

    private val interprete: Interpreter

    init {
        // Se copia el modelo a memoria directa (no depende de que el archivo esté sin comprimir).
        val bytes = context.assets.open(ARCHIVO).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.rewind()
        interprete = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
    }

    /** Devuelve las 40 probabilidades solo con la imagen. */
    @Synchronized
    fun clasificar(foto: Bitmap): FloatArray {
        val pequena = Bitmap.createScaledBitmap(foto, LADO, LADO, true)
        val pixeles = IntArray(LADO * LADO)
        pequena.getPixels(pixeles, 0, LADO, 0, 0, LADO, LADO)
        if (pequena !== foto) pequena.recycle()

        val entrada = ByteBuffer.allocateDirect(LADO * LADO * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in pixeles) {
            entrada.putFloat(((p shr 16) and 0xFF).toFloat()) // rojo
            entrada.putFloat(((p shr 8) and 0xFF).toFloat())  // verde
            entrada.putFloat((p and 0xFF).toFloat())          // azul
        }
        entrada.rewind()

        val salida = Array(1) { FloatArray(especies.size) }
        interprete.run(entrada, salida)
        return salida[0]
    }

    /** Las [n] especies más probables, de mayor a menor. */
    fun mejores(probabilidades: FloatArray, n: Int = 3): List<Opcion> =
        probabilidades.indices
            .sortedByDescending { probabilidades[it] }
            .take(n)
            .map { Opcion(especies[it], probabilidades[it]) }

    fun especiePorId(id: String?): Especie? = especies.firstOrNull { it.id == id }

    companion object {
        const val ARCHIVO = "mamiferos.tflite"
        const val LADO = 224

        private fun leerEspecies(context: Context): List<Especie> {
            val texto = context.assets.open("especies.json").bufferedReader().use { it.readText() }
            val arreglo = JSONArray(texto)
            return (0 until arreglo.length()).map { i ->
                val o = arreglo.getJSONObject(i)
                Especie(
                    id = o.getString("id"),
                    nombre = o.getString("nombre"),
                    cientifico = o.optString("cientifico"),
                    endemica = o.optBoolean("endemica"),
                    uicn = o.optString("uicn"),
                )
            }
        }
    }
}
