package co.mamiferos.colombia.modelo

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.pow

data class Departamento(val nombre: String, val clave: String?)

/**
 * Ajuste por ubicación, igual que en el notebook (celda 9.5) y en la app web:
 *   p_final ∝ p_modelo × factor[departamento] ^ λ
 * Los factores se calcularon con las fotos de ENTRENAMIENTO (α = 1, λ = 1).
 */
class Ubicacion(context: Context) {

    val departamentos: List<Departamento>
    private val factores: Map<String, FloatArray>
    private val lambda: Double

    init {
        val texto = context.assets.open("ubicacion.json").bufferedReader().use { it.readText() }
        val json = JSONObject(texto)
        lambda = json.getDouble("lambda")
        val deps = json.getJSONArray("departamentos")
        departamentos = (0 until deps.length()).map { i ->
            val d = deps.getJSONObject(i)
            Departamento(d.getString("nombre"), if (d.isNull("clave")) null else d.getString("clave"))
        }
        val f = json.getJSONObject("factores")
        factores = f.keys().asSequence().associateWith { k ->
            val arr = f.getJSONArray(k)
            FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }
    }

    fun tieneDatos(dep: Departamento?): Boolean = dep?.clave != null && factores.containsKey(dep.clave)

    /** Aplica el factor del departamento. Si no hay datos, devuelve las mismas probabilidades. */
    fun aplicar(p: FloatArray, dep: Departamento?): FloatArray {
        val f = dep?.clave?.let { factores[it] } ?: return p
        val q = FloatArray(p.size) { k -> (p[k] * f[k].toDouble().pow(lambda)).toFloat() }
        val suma = q.sum()
        if (suma <= 0f) return p
        for (k in q.indices) q[k] /= suma
        return q
    }

    fun porNombre(nombre: String?): Departamento? = departamentos.firstOrNull { it.nombre == nombre }

    /**
     * Convierte el nombre que da el GPS (por ejemplo "Tolima", "Departamento del Tolima",
     * "Bogotá") en uno de nuestros departamentos.
     */
    fun desdeTextoGps(texto: String?): Departamento? {
        if (texto.isNullOrBlank()) return null
        val t = normalizar(texto)
        if (t.contains("bogota")) return porNombre("Bogotá D.C.")
        if (t.contains("san andres")) return porNombre("San Andrés y Providencia")
        return departamentos.firstOrNull { normalizar(it.nombre) == t }
            ?: departamentos.firstOrNull { t.contains(normalizar(it.nombre)) }
    }

    companion object {
        fun normalizar(s: String): String {
            var x = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            for (q in listOf("departamento del ", "departamento de ", "depto. de ", "depto ", "departamento ")) {
                x = x.replace(q, "")
            }
            return x.trim()
        }
    }
}
