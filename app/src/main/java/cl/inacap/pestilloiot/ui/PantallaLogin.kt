package cl.inacap.pestilloiot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.pestilloiot.Sesion
import cl.inacap.pestilloiot.datos.FirestoreService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLogin(
    onLoginExitoso: () -> Unit
) {
    var email by remember { mutableStateOf("operador@inacap.cl") }
    var password by remember { mutableStateOf("123456") }
    var esModoRegistro by remember { mutableStateOf(false) }
    var rolSeleccionado by remember { mutableStateOf("OPERADOR") } // OPERADOR o OBSERVADOR
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pestillo IoT — Autenticación", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Seguridad",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (esModoRegistro) "Crear Nueva Cuenta" else "Acceso al Sistema",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Control de Acceso Seguro ISO/IEC 27400",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; mensajeError = null },
                label = { Text("Correo Electrónico") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; mensajeError = null },
                label = { Text("Contraseña (mínimo 6 caracteres)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (esModoRegistro) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Rol de Usuario en Sistema:", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (rolSeleccionado == "OPERADOR"),
                            onClick = { rolSeleccionado = "OPERADOR" }
                        )
                        Text("Operador (Apertura)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (rolSeleccionado == "OBSERVADOR"),
                            onClick = { rolSeleccionado = "OBSERVADOR" }
                        )
                        Text("Observador (Solo Lectura)")
                    }
                }
            }

            if (mensajeError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = mensajeError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
            ) {
                if (cargando) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (esModoRegistro) "Registrar Cuenta" else "Iniciar Sesión", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    esModoRegistro = !esModoRegistro
                    mensajeError = null
                }
            ) {
                Text(
                    if (esModoRegistro) "¿Ya tienes cuenta? Inicia sesión"
                    else "¿No tienes cuenta? Regístrate aquí"
                )
            }
        }
    }
}
