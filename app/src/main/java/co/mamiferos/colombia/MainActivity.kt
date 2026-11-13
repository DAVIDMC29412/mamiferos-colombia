package co.mamiferos.colombia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.mamiferos.colombia.ui.PantallaAjustes
import co.mamiferos.colombia.ui.PantallaComunidad
import co.mamiferos.colombia.ui.PantallaDetalle
import co.mamiferos.colombia.ui.PantallaHistorial
import co.mamiferos.colombia.ui.PantallaInicio
import co.mamiferos.colombia.ui.Tema

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Tema { Aplicacion() } }
    }
}

private data class Pestana(val ruta: String, val titulo: String, val icono: ImageVector)

private val PESTANAS = listOf(
    Pestana("inicio", "Identificar", Icons.Filled.Search),
    Pestana("historial", "Mis fotos", Icons.AutoMirrored.Filled.List),
    Pestana("comunidad", "Comunidad", Icons.Filled.Share),
    Pestana("ajustes", "Ajustes", Icons.Filled.Settings),
)

@Composable
private fun Aplicacion() {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    fun irA(ruta: String) = nav.navigate(ruta) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                PESTANAS.forEach { p ->
                    NavigationBarItem(
                        selected = rutaActual == p.ruta,
                        onClick = { irA(p.ruta) },
                        icon = { Icon(p.icono, null) },
                        label = { Text(p.titulo) },
                    )
                }
            }
        },
    ) { margen ->
        NavHost(nav, startDestination = "inicio", modifier = Modifier.padding(margen)) {
            composable("inicio") {
                PantallaInicio(
                    alTerminarUna = { id -> nav.navigate("detalle/$id") },
                    alTerminarVarias = { irA("historial") },
                )
            }
            composable("historial") { PantallaHistorial(alAbrir = { id -> nav.navigate("detalle/$id") }) }
            composable("comunidad") { PantallaComunidad() }
            composable("ajustes") { PantallaAjustes() }
            composable("detalle/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                PantallaDetalle(id = e.arguments?.getString("id") ?: "", alVolver = { nav.popBackStack() })
            }
        }
    }
}
