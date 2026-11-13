package co.mamiferos.colombia

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.room.Room
import co.mamiferos.colombia.datos.BaseDatos
import co.mamiferos.colombia.datos.MIGRACION_1_2
import co.mamiferos.colombia.datos.MIGRACION_2_3
import co.mamiferos.colombia.modelo.Clasificador
import co.mamiferos.colombia.modelo.Detector
import co.mamiferos.colombia.modelo.Ubicacion
import co.mamiferos.colombia.sync.Sincronizador
import java.util.UUID

/** Arranque de la app: deja listos el modelo, la base de datos y las preferencias. */
class MamiferosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Servicios.iniciar(this)
        // Si quedaron fotos sin subir de una sesión anterior, se programa la subida.
        Sincronizador.programar(this)
    }
}

/** Objetos compartidos por toda la app. */
object Servicios {
    private lateinit var app: Context

    fun iniciar(context: Context) {
        app = context.applicationContext
    }

    val clasificador: Clasificador by lazy { Clasificador(app) }
    val ubicacion: Ubicacion by lazy { Ubicacion(app) }

    /** MegaDetector. Es null si megadetector.tflite no está en assets (la app sigue funcionando sin revisar si hay animal). */
    val detector: Detector? by lazy { if (Detector.existe(app)) runCatching { Detector(app) }.getOrNull() else null }

    val bd: BaseDatos by lazy {
        Room.databaseBuilder(app, BaseDatos::class.java, "mamiferos.db")
            .addMigrations(MIGRACION_1_2, MIGRACION_2_3)
            .build()
    }
    val prefs: Preferencias by lazy { Preferencias(app) }
}

/** Ajustes que se guardan en el celular. */
class Preferencias(context: Context) {
    private val sp = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Identificador único de esta instalación (para saber desde qué equipo se subió cada foto). */
    val idInstalacion: String
        get() = sp.getString("id_instalacion", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("id_instalacion", it).apply()
        }

    val dispositivo: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

    var observador: String
        get() = sp.getString("observador", "") ?: ""
        set(v) = sp.edit().putString("observador", v.trim()).apply()

    /** "auto" = por GPS, "ninguno" = sin ubicación, o el nombre de un departamento. */
    var modoDepartamento: String
        get() = sp.getString("modo_departamento", "auto") ?: "auto"
        set(v) = sp.edit().putString("modo_departamento", v).apply()

    /** Último departamento detectado por GPS (se usa si no hay internet para traducir el GPS). */
    var ultimoDepartamentoGps: String
        get() = sp.getString("ultimo_dep_gps", "") ?: ""
        set(v) = sp.edit().putString("ultimo_dep_gps", v).apply()

    var soloWifi: Boolean
        get() = sp.getBoolean("solo_wifi", false)
        set(v) = sp.edit().putBoolean("solo_wifi", v).apply()
}
