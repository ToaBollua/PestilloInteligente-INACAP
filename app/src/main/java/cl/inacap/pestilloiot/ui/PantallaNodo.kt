package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import cl.inacap.pestilloiot.ui.theme.*

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
    val esAbierto = (latchState == "UNLOCKED")

    Scaffold(
        containerColor = CyberBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NODO IOT // CERRADURA PERIFÉRICA",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
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
            // Tarjeta de Estado del Enlace
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        when (estadoEnlace) {
                            EstadoEnlace.CONECTADO -> NeonGreen
                            EstadoEnlace.ESCUCHANDO -> NeonYellow
                            else -> NeonPink
                        },
                        RoundedCornerShape(10.dp)
                    ),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
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
                                    EstadoEnlace.CONECTADO -> NeonGreen
                                    EstadoEnlace.ESCUCHANDO -> NeonYellow
                                    else -> NeonPink
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ESTADO DEL SERVIDOR: $estadoEnlace",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = when (estadoEnlace) {
                            EstadoEnlace.CONECTADO -> NeonGreen
                            EstadoEnlace.ESCUCHANDO -> NeonYellow
                            else -> NeonPink
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Estado del Pasador / Servomotor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, if (esAbierto) NeonGreen else CyberBorderBright, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (esAbierto) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Cerrojo",
                        tint = if (esAbierto) NeonGreen else NeonPink,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (esAbierto) "🔓 PESTILLO DESTRABADO (90°)" else "🔒 PESTILLO BLOQUEADO (0°)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (esAbierto) NeonGreen else NeonPink
                    )
                    Text(
                        text = "NORMA OT: FAIL-SECURE ACTIVO",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sensor Ultrasónico Simulado
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "SENSOR HC-SR04 // TELEMETRÍA",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Distancia detectada: ${String.format(java.util.Locale.US, "%.1f", distancia)} cm",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = sliderDistancia,
                        onValueChange = {
                            sliderDistancia = it
                            Sesion.simularLecturaDistanciaNodo(it.toDouble())
                        },
                        valueRange = 2f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = CyberBorderBright
                        )
                    )
                    Text(
                        text = if (distancia < 8.0) "⚠️ PROXIMIDAD DETECTADA (<8cm): APERTURA AUTOMÁTICA"
                        else "Umbral activación perimetral: < 8.0 cm",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (distancia < 8.0) NeonPink else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botones de acción manual
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { Sesion.accionarAperturaNodo("BOTON_LOCAL") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = TextDark
                    )
                ) {
                    Text("ABRIR (3s)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { Sesion.forzarBloqueoSeguro("Acción manual") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(1.dp, NeonPink, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonPink
                    )
                ) {
                    Text("BLOQUEAR (0°)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Consola de eventos
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, CyberBorder, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "CONSOLA TELEMETRÍA LOCAL // NODO",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ultimoLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
