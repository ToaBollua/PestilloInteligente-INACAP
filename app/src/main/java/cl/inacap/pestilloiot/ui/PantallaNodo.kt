package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.pestilloiot.Sesion
import cl.inacap.pestilloiot.enlace.EstadoEnlace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaNodo(
    onVolver: () -> Unit
) {
    val estadoEnlace by Sesion.estadoEnlace.collectAsState()
    val distancia by Sesion.distancia.collectAsState()
    val latchState by Sesion.latchState.collectAsState()
    val ultimoLog by Sesion.ultimoLog.collectAsState()

    var sliderDistancia by remember { mutableFloatStateOf(50f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nodo IoT — Cerradura Periférica", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        Sesion.cerrarEnlace()
                        onVolver()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta de Estado del Enlace
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (estadoEnlace) {
                        EstadoEnlace.CONECTADO -> MaterialTheme.colorScheme.primaryContainer
                        EstadoEnlace.ESCUCHANDO -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (estadoEnlace) {
                                    EstadoEnlace.CONECTADO -> Color(0xFF00FF66)
                                    EstadoEnlace.ESCUCHANDO -> Color(0xFFFFB800)
                                    else -> Color(0xFFFF3366)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Estado: $estadoEnlace",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Estado del Pasador / Servomotor
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (latchState == "UNLOCKED") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (latchState == "UNLOCKED") Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Cerrojo",
                        tint = if (latchState == "UNLOCKED") Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (latchState == "UNLOCKED") "PESTILLO DESTRIABADO (90°)" else "PESTILLO BLOQUEADO (0°)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (latchState == "UNLOCKED") Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(
                        text = "Lineamiento OT: Fail-Secure activo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sensor Ultrasónico Simulado
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sensor Ultrasónico HC-SR04 (Simulado)", fontWeight = FontWeight.Bold)
                    Text("Distancia detectada: ${String.format(java.util.Locale.US, "%.1f", distancia)} cm")
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderDistancia,
                        onValueChange = {
                            sliderDistancia = it
                            Sesion.simularLecturaDistanciaNodo(it.toDouble())
                        },
                        valueRange = 2f..100f
                    )
                    Text(
                        text = if (distancia < 8.0) "⚠️ Objeto en proximidad (< 8 cm): Apertura automática disparada"
                        else "Umbral de activación: < 8.0 cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (distancia < 8.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción manual
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { Sesion.accionarAperturaNodo("BOTON_LOCAL") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Abrir Manual (3s)")
                }

                OutlinedButton(
                    onClick = { Sesion.forzarBloqueoSeguro("Acción manual") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Bloquear (0°)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Consola de eventos
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Registro de Telemetría Local:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ultimoLog,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
