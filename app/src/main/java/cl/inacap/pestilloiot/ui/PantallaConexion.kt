package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.pestilloiot.MedioConexion
import cl.inacap.pestilloiot.ModoOperacion
import cl.inacap.pestilloiot.Sesion
import cl.inacap.pestilloiot.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConexion(
    onNavegarNodo: () -> Unit,
    onNavegarPanel: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    var modoSeleccionado by remember { mutableStateOf(ModoOperacion.PANEL) }
    var medioSeleccionado by remember { mutableStateOf(MedioConexion.WIFI) }
    var destinoTexto by remember { mutableStateOf("192.168.240.1") } // Default a Waydroid host IP
    var codigoPin by remember { mutableStateOf("123456") }

    val rol = Sesion.rolUsuario.collectAsState().value

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = CyberPanel,
        unfocusedContainerColor = CyberPanel,
        focusedBorderColor = NeonCyan,
        unfocusedBorderColor = CyberBorderBright,
        focusedLabelColor = NeonCyan,
        unfocusedLabelColor = TextMuted,
        focusedLeadingIconColor = NeonCyan,
        unfocusedLeadingIconColor = TextMuted,
        cursorColor = NeonGreen
    )

    Scaffold(
        containerColor = CyberBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ENLACE IOT // CONFIG",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        color = NeonGreen
                    )
                },
                actions = {
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Salir", tint = NeonPink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberPanel
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Perfil RBAC & Seguridad Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorderBright, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "PERFIL RBAC ACTIVO: $rol",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = NeonCyan
                        )
                        Text(
                            "CANAL SEGURO: AES-256-GCM (ISO/IEC 27400)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Modo Operativo
            Text(
                "1. SELECCIONA EL MODO OPERATIVO:",
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NeonGreen,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = (modoSeleccionado == ModoOperacion.PANEL),
                    onClick = { modoSeleccionado = ModoOperacion.PANEL },
                    label = { Text("Panel Control", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = if (modoSeleccionado == ModoOperacion.PANEL) NeonGreen else TextMuted) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBorderBright,
                        selectedLabelColor = NeonGreen,
                        containerColor = CyberCard,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = (modoSeleccionado == ModoOperacion.NODO),
                    onClick = { modoSeleccionado = ModoOperacion.NODO },
                    label = { Text("Nodo IoT", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null, tint = if (modoSeleccionado == ModoOperacion.NODO) NeonCyan else TextMuted) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBorderBright,
                        selectedLabelColor = NeonCyan,
                        containerColor = CyberCard,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Medio de Transporte
            Text(
                "2. MEDIO DE COMUNICACIÓN:",
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NeonGreen,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = (medioSeleccionado == MedioConexion.WIFI),
                    onClick = {
                        medioSeleccionado = MedioConexion.WIFI
                        destinoTexto = "192.168.240.1"
                    },
                    label = { Text("Wi-Fi TCP (5050)", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, tint = if (medioSeleccionado == MedioConexion.WIFI) NeonGreen else TextMuted) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBorderBright,
                        selectedLabelColor = NeonGreen,
                        containerColor = CyberCard,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = (medioSeleccionado == MedioConexion.BLUETOOTH),
                    onClick = {
                        medioSeleccionado = MedioConexion.BLUETOOTH
                        destinoTexto = "00:11:22:33:44:55"
                    },
                    label = { Text("Bluetooth RF", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = if (medioSeleccionado == MedioConexion.BLUETOOTH) NeonCyan else TextMuted) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBorderBright,
                        selectedLabelColor = NeonCyan,
                        containerColor = CyberCard,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (medioSeleccionado == MedioConexion.BLUETOOTH) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "⚠️ RFCOMM requiere hardware físico real. En contenedores Waydroid o emuladores sin chip Bluetooth, el socket devuelve ERROR. Usa Wi-Fi TCP.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = NeonYellow
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (modoSeleccionado == ModoOperacion.PANEL) {
                OutlinedTextField(
                    value = destinoTexto,
                    onValueChange = { destinoTexto = it },
                    label = { Text(if (medioSeleccionado == MedioConexion.BLUETOOTH) "DIRECCIÓN MAC BLUETOOTH" else "DIRECCIÓN IP GATEWAY (TCP)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                if (medioSeleccionado == MedioConexion.WIFI) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { destinoTexto = "192.168.240.1" },
                            label = { Text("Waydroid", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonGreen) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = CyberCard),
                            border = SuggestionChipDefaults.suggestionChipBorder(true, borderColor = CyberBorderBright)
                        )
                        SuggestionChip(
                            onClick = { destinoTexto = "100.99.225.123" },
                            label = { Text("Tailscale", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = CyberCard),
                            border = SuggestionChipDefaults.suggestionChipBorder(true, borderColor = CyberBorderBright)
                        )
                        SuggestionChip(
                            onClick = { destinoTexto = "127.0.0.1" },
                            label = { Text("Localhost", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = CyberCard),
                            border = SuggestionChipDefaults.suggestionChipBorder(true, borderColor = CyberBorder)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = codigoPin,
                onValueChange = { if (it.length <= 6) codigoPin = it },
                label = { Text("PIN CIFRADO AES (6 DÍGITOS)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                    .border(1.dp, if (codigoPin.length == 6) NeonGreen else Color.Transparent, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = TextDark,
                    disabledContainerColor = CyberPanel,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    if (modoSeleccionado == ModoOperacion.NODO) "INICIAR SERVIDOR NODO IOT"
                    else "CONECTAR PANEL AL ENLACE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
