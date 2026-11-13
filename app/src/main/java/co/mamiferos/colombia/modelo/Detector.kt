package co.mamiferos.colombia.modelo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Qué encontró MegaDetector en la foto. */
enum class Hallazgo { ANIMAL, PERSONA, VEHICULO, NADA }

/**
 * Resultado de MegaDetector.
 * [caja] = rectángulo del animal en la foto original (solo si [hallazgo] es ANIMAL).
 */
data class Deteccion(
    val hallazgo: Hallazgo,
    val animal: Float,
    val persona: Float,
    val vehiculo: Float,
    val caja: Rect?,
)

/**
 * MegaDetector V6 (yolov9-c, TFLite float32).
 * Entrada: [1, 640, 640, 3] RGB 0..1, foto encajada en 640 × 640 con bordes grises (114).
 * Salida: [1, 7, 8400] → filas 0-3 = cx, cy, w, h (0..1 sobre la imagen de 640); filas 4-6 = animal, persona, vehículo.
 * Solo dice SI hay un animal y DÓNDE; la especie la dice el clasificador.
 */
class Detector(context: Context) {

    val version = "MegaDetector V6 (yolov9-c) · TFLite float32"
    private val interprete: Interpreter
    private val transpuesta: Boolean // true si la salida viene como [1, 8400, 7]

    init {
        interprete = Interpreter(cargar(context), Interpreter.Options().setNumThreads(4))
        val forma = interprete.getOutputTensor(0).shape()
        transpuesta = forma.size == 3 && forma[2] == 7
    }

    @Synchronized
    fun detectar(foto: Bitmap): Deteccion {
        // 1. Encajar la foto en 640 × 640 sin deformarla (bordes grises, como en el entrenamiento).
        val escala = min(LADO.toFloat() / foto.width, LADO.toFloat() / foto.height)
        val nw = (foto.width * escala).roundToInt().coerceAtLeast(1)
        val nh = (foto.height * escala).roundToInt().coerceAtLeast(1)
        val padX = (LADO - nw) / 2
        val padY = (LADO - nh) / 2
        val lienzo = Bitmap.createBitmap(LADO, LADO, Bitmap.Config.ARGB_8888)
        Canvas(lienzo).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(foto, null, Rect(padX, padY, padX + nw, padY + nh), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixeles = IntArray(LADO * LADO)
        lienzo.getPixels(pixeles, 0, LADO, 0, 0, LADO, LADO)
        lienzo.recycle()

        val entrada = ByteBuffer.allocateDirect(LADO * LADO * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in pixeles) {
            entrada.putFloat(((p shr 16) and 0xFF) / 255f)
            entrada.putFloat(((p shr 8) and 0xFF) / 255f)
            entrada.putFloat((p and 0xFF) / 255f)
        }
        entrada.rewind()

        // 2. Correr el modelo.
        val salida: Array<FloatArray>
        if (transpuesta) {
            val s = Array(1) { Array(CAJAS) { FloatArray(7) } }
            interprete.run(entrada, s)
            salida = Array(7) { f -> FloatArray(CAJAS) { i -> s[0][i][f] } }
        } else {
            val s = Array(1) { Array(7) { FloatArray(CAJAS) } }
            interprete.run(entrada, s)
            salida = s[0]
        }

        // 3. Mejor puntaje de cada clase (solo interesa si hay algo, no contar cuántos).
        var mejorAnimal = 0f; var iAnimal = -1
        var mejorPersona = 0f
        var mejorVehiculo = 0f
        for (i in 0 until CAJAS) {
            if (salida[4][i] > mejorAnimal) { mejorAnimal = salida[4][i]; iAnimal = i }
            if (salida[5][i] > mejorPersona) mejorPersona = salida[5][i]
            if (salida[6][i] > mejorVehiculo) mejorVehiculo = salida[6][i]
        }

        // 4. Decidir.
        val hallazgo = when {
            mejorPersona >= UMBRAL_OTRO && mejorPersona > mejorAnimal -> Hallazgo.PERSONA
            mejorAnimal >= UMBRAL_ANIMAL -> Hallazgo.ANIMAL
            mejorVehiculo >= UMBRAL_OTRO -> Hallazgo.VEHICULO
            else -> Hallazgo.NADA
        }

        // 5. Pasar la caja del animal a coordenadas de la foto original.
        val caja = if (hallazgo == Hallazgo.ANIMAL && iAnimal >= 0) {
            // Si el modelo diera píxeles (0..640) en vez de 0..1, se ajusta.
            val f = if (max(salida[2][iAnimal], salida[3][iAnimal]) > 2f) 1f else LADO.toFloat()
            val cx = salida[0][iAnimal] * f
            val cy = salida[1][iAnimal] * f
            val w = salida[2][iAnimal] * f
            val h = salida[3][iAnimal] * f
            val x1 = ((cx - w / 2 - padX) / escala)
            val y1 = ((cy - h / 2 - padY) / escala)
            val x2 = ((cx + w / 2 - padX) / escala)
            val y2 = ((cy + h / 2 - padY) / escala)
            Rect(
                x1.roundToInt().coerceIn(0, foto.width - 1),
                y1.roundToInt().coerceIn(0, foto.height - 1),
                x2.roundToInt().coerceIn(1, foto.width),
                y2.roundToInt().coerceIn(1, foto.height),
            )
        } else null

        return Deteccion(hallazgo, mejorAnimal, mejorPersona, mejorVehiculo, caja)
    }

    /** Recorta el animal con un margen del 10 % (como las fotos del dataset). Devuelve null si la caja es muy pequeña. */
    fun recortar(foto: Bitmap, caja: Rect): Bitmap? {
        val mx = (caja.width() * 0.10f).roundToInt()
        val my = (caja.height() * 0.10f).roundToInt()
        val x1 = (caja.left - mx).coerceAtLeast(0)
        val y1 = (caja.top - my).coerceAtLeast(0)
        val x2 = (caja.right + mx).coerceAtMost(foto.width)
        val y2 = (caja.bottom + my).coerceAtMost(foto.height)
        if (x2 - x1 < 32 || y2 - y1 < 32) return null
        return Bitmap.createBitmap(foto, x1, y1, x2 - x1, y2 - y1)
    }

    companion object {
        const val ARCHIVO = "megadetector.tflite"
        const val LADO = 640
        const val CAJAS = 8400
        const val UMBRAL_ANIMAL = 0.20f // bajo a propósito: es mejor preguntar que perder un animal
        const val UMBRAL_OTRO = 0.50f

        /** true si el archivo del detector está dentro de la app. */
        fun existe(context: Context): Boolean =
            runCatching { context.assets.list("")?.contains(ARCHIVO) == true }.getOrDefault(false)

        /** Abre el modelo sin copiarlo a memoria (pesa ~100 MB). Si no se puede, lo copia. */
        private fun cargar(context: Context): ByteBuffer = try {
            context.assets.openFd(ARCHIVO).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { canal ->
                    canal.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (e: java.io.IOException) {
            val bytes = context.assets.open(ARCHIVO).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
        }
    }
}
