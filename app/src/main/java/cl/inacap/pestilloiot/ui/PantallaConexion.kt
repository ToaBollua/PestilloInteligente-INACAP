package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.pestilloiot.MedioConexion
import cl.inacap.pestilloiot.ModoOperacion
import cl.inacap.pestilloiot.Sesion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConexion(
    onNavegarNodo: () -> Unit,
    onNavegarPanel: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    var modoSeleccionado by remember { mutableStateOf(ModoOperacion.PANEL) }
    var medioSeleccionado by remember { mutableStateOf(MedioConexion.BLUETOOTH) }
    var destinoTexto by remember { mutableStateOf("192.168.1.100") } // MAC de BT o IP
    var codigoPin by remember { mutableStateOf("123456") }

    val rol = Sesion.rolUsuario.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración de Enlace IoT", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onCerrarSesion) {
                        Text("Cerrar Sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Perfil Activo: $rol", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Cifrado: AES-256-GCM (ISO/IEC 27400)", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("1. Selecciona el Modo Operativo:", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = (modoSeleccionado == ModoOperacion.NODO),
                    onClick = { modoSeleccionado = ModoOperacion.NODO },
                    label = { Text("Nodo IoT (Cerradura)") },
                    leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null) }
                )
                FilterChip(
                    selected = (modoSeleccionado == ModoOperacion.PANEL),
                    onClick = { modoSeleccionado = ModoOperacion.PANEL },
                    label = { Text("Panel de Control") },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("2. Medio de Comunicación:", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = (medioSeleccionado == MedioConexion.BLUETOOTH),
                    onClick = {
                        medioSeleccionado = MedioConexion.BLUETOOTH
                        destinoTexto = "00:11:22:33:44:55"
                    },
                    label = { Text("Bluetooth RFCOMM") },
                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                )
                FilterChip(
                    selected = (medioSeleccionado == MedioConexion.WIFI),
                    onClick = {
                        medioSeleccionado = MedioConexion.WIFI
                        destinoTexto = "192.168.1.100"
                    },
                    label = { Text("Wi-Fi TCP (5050)") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (modoSeleccionado == ModoOperacion.PANEL) {
                OutlinedTextField(
                    value = destinoTexto,
                    onValueChange = { destinoTexto = it },
                    label = { Text(if (medioSeleccionado == MedioConexion.BLUETOOTH) "Dirección MAC del Nodo" else "Dirección IP del Nodo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = codigoPin,
                onValueChange = { if (it.length <= 6) codigoPin = it },
                label = { Text("Código de Vinculación Cifrada (6 dígitos)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (modoSeleccionado == ModoOperacion.NODO) {
                        Sesion.iniciarNodo(medioSeleccionado, codigoPin)
                        onNavegarNodo()
                    } else {
                        Sesion.conectarPanel(medioSeleccionado, destinoTexto, codigoPin)
                        onNavegarPanel()
                    }
                },
                enabled = codigoPin.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (modoSeleccionado == ModoOperacion.NODO) "Iniciar Servidor del Nodo IoT"
                    else "Conectar Panel al Nodo IoT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
