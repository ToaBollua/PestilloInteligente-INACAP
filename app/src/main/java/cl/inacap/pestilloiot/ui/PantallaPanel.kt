package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
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
fun PantallaPanel(
    onNavegarHistorial: () -> Unit,
    onVolver: () -> Unit
) {
    val estadoEnlace by Sesion.estadoEnlace.collectAsState()
    val distancia by Sesion.distancia.collectAsState()
    val latchState by Sesion.latchState.collectAsState()
    val rolUsuario by Sesion.rolUsuario.collectAsState()
    val ultimoLog by Sesion.ultimoLog.collectAsState()

    val esOperador = (rolUsuario == "OPERADOR")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel de Control — Operador", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        Sesion.cerrarEnlace()
                        onVolver()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onNavegarHistorial) {
                        Icon(Icons.Default.History, contentDescription = "Historial Firestore")
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
            // Estado de Conexión y Rol
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (estadoEnlace) {
                        EstadoEnlace.CONECTADO -> MaterialTheme.colorScheme.primaryContainer
                        EstadoEnlace.CONECTANDO -> MaterialTheme.colorScheme.secondaryContainer
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
                                    EstadoEnlace.CONECTANDO -> Color(0xFFFFB800)
                                    else -> Color(0xFFFF3366)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Enlace: $estadoEnlace", fontWeight = FontWeight.Bold)
                        Text("Rol: $rolUsuario (${if (esOperador) "Control Total" else "Solo Lectura"})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Telemetría en vivo del Pasador
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (latchState == "UNLOCKED") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (latchState == "UNLOCKED") Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Estado Pasador",
                        tint = if (latchState == "UNLOCKED") Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (latchState == "UNLOCKED") "CERROJO ABIERTO" else "CERROJO BLOQUEADO",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (latchState == "UNLOCKED") Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Distancia exterior: ${String.format(java.util.Locale.US, "%.1f", distancia)} cm",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de Apertura Remota
            Button(
                onClick = { Sesion.solicitarAperturaRemota() },
                enabled = esOperador && estadoEnlace == EstadoEnlace.CONECTADO,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DESTRAMPAR PESTILLO (3s)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            if (!esOperador) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Rol Observador: Comandos de apertura deshabilitados por RBAC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Log de telemetría y eventos
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Consola de Comandos y Telemetría:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
