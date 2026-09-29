package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import cl.inacap.pestilloiot.ui.theme.*

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
    val esAbierto = (latchState == "UNLOCKED")

    Scaffold(
        containerColor = CyberBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PANEL // OPERADOR DE ACCESO",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        color = NeonGreen
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        Sesion.cerrarEnlace()
                        onVolver()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = NeonGreen)
                    }
                },
                actions = {
                    IconButton(onClick = onNavegarHistorial) {
                        Icon(Icons.Default.History, contentDescription = "Historial Firestore", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberPanel)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (estadoEnlace == EstadoEnlace.CONECTADO) NeonGreen else NeonPink, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when (estadoEnlace) {
                                    EstadoEnlace.CONECTADO -> NeonGreen
                                    EstadoEnlace.CONECTANDO -> NeonYellow
                                    else -> NeonPink
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "ESTADO ENLACE: $estadoEnlace",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = when (estadoEnlace) {
                                EstadoEnlace.CONECTADO -> NeonGreen
                                EstadoEnlace.CONECTANDO -> NeonYellow
                                else -> NeonPink
                            }
                        )
                        Text(
                            "PERFIL RBAC: $rolUsuario (${if (esOperador) "CONTROL TOTAL" else "SOLO LECTURA"})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Telemetría en vivo del Pasador
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, if (esAbierto) NeonGreen else CyberBorderBright, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (esAbierto) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Estado Pasador",
                        tint = if (esAbierto) NeonGreen else NeonPink,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (esAbierto) "🔓 PESTILLO DESTRABADO (90°)" else "🔒 PESTILLO BLOQUEADO (0°)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (esAbierto) NeonGreen else NeonPink
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "DISTANCIA SENSOR: ${String.format(java.util.Locale.US, "%.1f", distancia)} cm",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (distancia < 8.0) "⚡ UMBRAL PROXIMIDAD ACTIVO (<8cm)" else "ENLACE PERIMETRAL SEGURO",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (distancia < 8.0) NeonPink else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón de Apertura Remota
            Button(
                onClick = { Sesion.solicitarAperturaRemota() },
                enabled = esOperador && estadoEnlace == EstadoEnlace.CONECTADO,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(
                        1.dp,
                        if (esOperador && estadoEnlace == EstadoEnlace.CONECTADO) NeonGreen else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = TextDark,
                    disabledContainerColor = CyberCard,
                    disabledContentColor = TextMuted
                )
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "DESTRABAR PESTILLO (3s)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!esOperador) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Rol Observador: Comandos deshabilitados por RBAC.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NeonYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Consola de telemetría y logs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, CyberBorder, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "CONSOLA TELEMETRÍA // H0P3 OT-KERNEL",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ultimoLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
