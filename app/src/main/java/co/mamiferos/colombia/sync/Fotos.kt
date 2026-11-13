package co.mamiferos.colombia.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepara la foto para guardarla en Firestore (plan gratuito, sin Storage).
 * Firestore admite documentos de hasta 1 MB, así que la foto se reduce a 800 px
 * y se guarda como texto (base64). También se hace una miniatura de 400 px para las listas.
 */
object Fotos {
    private const val LADO_GRANDE = 800
    private const val LADO_MINIATURA = 400
    private const val MAX_TEXTO = 700_000 // caracteres base64 (≈ 525 KB de imagen), lejos del límite de 1 MB

    class Preparada(val grande: String, val miniatura: String, val ancho: Int, val alto: Int)

    fun preparar(archivo: File): Preparada {
        val original = BitmapFactory.decodeFile(archivo.absolutePath) ?: error("No se pudo leer la foto")
        val grande = reducir(original, LADO_GRANDE)
        val mini = reducir(original, LADO_MINIATURA)

        var calidad = 80
        var texto = base64(grande, calidad)
        while (texto.length > MAX_TEXTO && calidad > 30) {
            calidad -= 10
            texto = base64(grande, calidad)
        }
        val resultado = Preparada(texto, base64(mini, 65), grande.width, grande.height)

        if (grande !== original) grande.recycle()
        if (mini !== original) mini.recycle()
        original.recycle()
        return resultado
    }

    fun decodificar(texto: String): Bitmap? = try {
        val bytes = Base64.decode(texto, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }

    private fun reducir(b: Bitmap, lado: Int): Bitmap {
        val escala = lado.toFloat() / max(b.width, b.height)
        if (escala >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * escala).roundToInt(), (b.height * escala).roundToInt(), true)
    }

    private fun base64(b: Bitmap, calidad: Int): String {
        val salida = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
        return Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
    }
}
