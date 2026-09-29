package cl.inacap.pestilloiot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.inacap.pestilloiot.datos.FirestoreService
import cl.inacap.pestilloiot.ui.*

class MainActivity : ComponentActivity() {

    private val solicitarPermisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permisos procesados
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Sesion.inicializar(this)
        solicitarPermisosEnTiempoDeEjecucion()

        setContent {
            cl.inacap.pestilloiot.ui.theme.PestilloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavegacion()
                }
            }
        }
    }

    private fun solicitarPermisosEnTiempoDeEjecucion() {
        val permisos = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permisos.isNotEmpty()) {
            solicitarPermisosLauncher.launch(permisos.toTypedArray())
        }
    }
}

@Composable
fun AppNavegacion() {
    val navController = rememberNavController()
    val rutaInicial = if (FirestoreService.usuarioActual() != null) "conexion" else "login"

    NavHost(
        navController = navController,
        startDestination = rutaInicial
    ) {
        composable("login") {
            PantallaLogin(
                onLoginExitoso = {
                    navController.navigate("conexion") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("conexion") {
            PantallaConexion(
                onNavegarNodo = { navController.navigate("nodo") },
                onNavegarPanel = { navController.navigate("panel") },
                onCerrarSesion = {
                    FirestoreService.cerrarSesion()
                    navController.navigate("login") {
                        popUpTo("conexion") { inclusive = true }
                    }
                }
            )
        }

        composable("nodo") {
            PantallaNodo(
                onVolver = { navController.popBackStack() }
            )
        }

        composable("panel") {
            PantallaPanel(
                onNavegarHistorial = { navController.navigate("historial") },
                onVolver = { navController.popBackStack() }
            )
        }

        composable("historial") {
            PantallaHistorial(
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
