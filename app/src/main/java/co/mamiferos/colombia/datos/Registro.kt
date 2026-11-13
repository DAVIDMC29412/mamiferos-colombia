package co.mamiferos.colombia.datos

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Respuesta de la persona sobre el resultado del modelo. */
object TipoRespuesta {
    const val SIN_REVISAR = "sin_revisar"   // la persona todavía no dijo nada
    const val CONFIRMADA = "confirmada"     // la primera opción era correcta
    const val CORREGIDA = "corregida"       // eligió otra especie de la lista (top-3 u otra)
    const val NUEVA = "nueva"               // el animal no está entre las 40 especies
}

/** Estado de la subida a internet. */
object EstadoSubida {
    const val PENDIENTE = "pendiente"
    const val SUBIDA = "subida"
    const val ERROR = "error"
}

/**
 * Una foto con su resultado. Se guarda en el celular (funciona sin internet)
 * y se sube a Firebase cuando hay conexión.
 */
@Entity(tableName = "registros")
data class Registro(
    @PrimaryKey val id: String,
    val fotoRuta: String,                 // archivo .jpg dentro de la app
    val fecha: Long,                      // milisegundos desde 1970
    val latitud: Double?,
    val longitud: Double?,
    val origenGps: String?,               // "foto" (GPS guardado en la foto) o "celular"
    val departamento: String?,            // nombre del departamento usado en el ajuste
    val origenDepartamento: String?,      // "gps", "elegido", "ultimo_conocido" o null
    val top3: String,                     // JSON: [{"id":..,"nombre":..,"p":..}, ...]
    val especieModelo: String,            // id de la primera opción del modelo
    val confianzaModelo: Float,           // probabilidad de la primera opción
    val tipoRespuesta: String = TipoRespuesta.SIN_REVISAR,
    val especieUsuario: String? = null,   // id elegido por la persona (si confirmó o corrigió)
    val nuevaComun: String? = null,       // nombre común escrito (si no está en la lista)
    val nuevaCientifica: String? = null,  // nombre científico escrito (opcional)
    val observador: String?,              // nombre de quien tomó la foto (opcional)
    val dispositivo: String,              // marca y modelo del celular
    val idInstalacion: String,            // identificador de esta instalación de la app
    val versionModelo: String,
    val estadoSubida: String = EstadoSubida.PENDIENTE,
    val fotoUrl: String? = null,          // dirección de la foto en Firebase (cuando ya subió)
    val errorSubida: String? = null,
    val confianzaAnimal: Float? = null,   // puntaje de MegaDetector para "animal" (null si no se usó)
    @ColumnInfo(defaultValue = "0")
    val animalForzado: Boolean = false,   // true si el detector no vio animal y la persona dijo "Sí hay un animal"
    @ColumnInfo(defaultValue = "0")
    val recortada: Boolean = false,       // true si se clasificó el recorte del animal y no la foto completa
    val tiempoDetectorMs: Long? = null,       // cuánto tardó MegaDetector en el celular
    val tiempoClasificacionMs: Long? = null,  // cuánto tardó el clasificador (+ ajuste por ubicación)
    val nombreArchivo: String? = null,        // nombre original de la foto elegida de la galería
)

@Dao
interface RegistroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(r: Registro)

    @Update
    suspend fun actualizar(r: Registro)

    @Query("SELECT * FROM registros ORDER BY fecha DESC")
    fun todos(): Flow<List<Registro>>

    @Query("SELECT * FROM registros WHERE id = :id")
    fun observar(id: String): Flow<Registro?>

    @Query("SELECT * FROM registros WHERE id = :id")
    suspend fun porId(id: String): Registro?

    @Query("SELECT * FROM registros WHERE estadoSubida != 'subida' ORDER BY fecha")
    suspend fun pendientes(): List<Registro>

    @Query("SELECT COUNT(*) FROM registros WHERE estadoSubida != 'subida'")
    fun contarPendientes(): Flow<Int>

    /** Guarda la respuesta de la persona sin tocar los datos de la subida (fotoUrl). */
    @Query(
        "UPDATE registros SET tipoRespuesta = :tipo, especieUsuario = :especie, nuevaComun = :comun, " +
            "nuevaCientifica = :cientifica, estadoSubida = 'pendiente', errorSubida = NULL WHERE id = :id",
    )
    suspend fun responder(id: String, tipo: String, especie: String?, comun: String?, cientifica: String?)

    @Query("DELETE FROM registros WHERE id = :id")
    suspend fun borrar(id: String)
}

@Database(entities = [Registro::class], version = 3, exportSchema = false)
abstract class BaseDatos : RoomDatabase() {
    abstract fun registros(): RegistroDao
}

/** De la versión 1 a la 2: columnas de MegaDetector (no se pierden las fotos guardadas). */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE registros ADD COLUMN confianzaAnimal REAL")
        db.execSQL("ALTER TABLE registros ADD COLUMN animalForzado INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE registros ADD COLUMN recortada INTEGER NOT NULL DEFAULT 0")
    }
}

/** De la versión 2 a la 3: tiempos de respuesta para la prueba de campo. */
val MIGRACION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE registros ADD COLUMN tiempoDetectorMs INTEGER")
        db.execSQL("ALTER TABLE registros ADD COLUMN tiempoClasificacionMs INTEGER")
        db.execSQL("ALTER TABLE registros ADD COLUMN nombreArchivo TEXT")
    }
}
