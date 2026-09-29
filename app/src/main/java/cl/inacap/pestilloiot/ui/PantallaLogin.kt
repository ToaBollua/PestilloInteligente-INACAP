package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.pestilloiot.Sesion
import cl.inacap.pestilloiot.datos.FirestoreService
import cl.inacap.pestilloiot.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLogin(
    onLoginExitoso: () -> Unit
) {
    var email by remember { mutableStateOf("operador@inacap.cl") }
    var password by remember { mutableStateOf("Inacap2026!") }
    var esModoRegistro by remember { mutableStateOf(false) }
    var rolSeleccionado by remember { mutableStateOf("OPERADOR") } // OPERADOR o OBSERVADOR
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

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
                        "PESTILLO IOT // AUTH KERNEL",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        color = NeonGreen
                    )
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorderBright, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Seguridad",
                        modifier = Modifier.size(54.dp),
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (esModoRegistro) "CREAR IDENTIDAD OT" else "ACCESO AL CLÚSTER",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = "CONTROL DE ACCESO SEGURO // ISO/IEC 27400",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NeonGreen
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; mensajeError = null },
                        label = { Text("USUARIO / CORREO") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; mensajeError = null },
                        label = { Text("CLAVE DE ACCESO") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (esModoRegistro) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "ROL RBAC EN SISTEMA:",
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = NeonCyan,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (rolSeleccionado == "OPERADOR"),
                                    onClick = { rolSeleccionado = "OPERADOR" },
                                    colors = RadioButtonDefaults.colors(selectedColor = NeonGreen, unselectedColor = TextMuted)
                                )
                                Text("Operador", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (rolSeleccionado == "OBSERVADOR"),
                                    onClick = { rolSeleccionado = "OBSERVADOR" },
                                    colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = TextMuted)
                                )
                                Text("Observador", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }

                    if (mensajeError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "[ERROR] ${mensajeError!!}",
                            color = NeonPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            cargando = true
                            mensajeError = null
                            if (esModoRegistro) {
                                FirestoreService.registrarUsuario(
                                    email = email,
                                    clave = password,
                                    rol = rolSeleccionado,
                                    onSuccess = { _, rol ->
                                        cargando = false
                                        Sesion.rolUsuario.value = rol
                                        onLoginExitoso()
                                    },
                                    onError = { err ->
                                        cargando = false
                                        mensajeError = err
                                    }
                                )
                            } else {
                                FirestoreService.iniciarSesion(
                                    email = email,
                                    clave = password,
                                    onSuccess = { _, rol ->
                                        cargando = false
                                        Sesion.rolUsuario.value = rol
                                        onLoginExitoso()
                                    },
                                    onError = { err ->
                                        cargando = false
                                        mensajeError = err
                                    }
                                )
                            }
                        },
                        enabled = !cargando && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .border(1.dp, if (!cargando && email.isNotBlank() && password.isNotBlank()) NeonGreen else Color.Transparent, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = TextDark,
                            disabledContainerColor = CyberPanel,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        if (cargando) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TextDark, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (esModoRegistro) "REGISTRAR CUENTA" else "INICIAR SESIÓN",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            esModoRegistro = !esModoRegistro
                            mensajeError = null
                        }
                    ) {
                        Text(
                            if (esModoRegistro) "<- VOLVER A INICIO DE SESIÓN"
                            else "[ CREAR NUEVA CUENTA ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                    }
                }
            }
        }
    }
}
