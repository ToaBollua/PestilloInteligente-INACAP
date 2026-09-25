package cl.inacap.pestilloiot

import android.content.Context
import cl.inacap.pestilloiot.datos.FirestoreService
import cl.inacap.pestilloiot.enlace.BluetoothEnlace
import cl.inacap.pestilloiot.enlace.Enlace
import cl.inacap.pestilloiot.enlace.EstadoEnlace
import cl.inacap.pestilloiot.enlace.Mensaje
import cl.inacap.pestilloiot.enlace.TipoMensaje
import cl.inacap.pestilloiot.enlace.WifiEnlace
import cl.inacap.pestilloiot.seguridad.Cripto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

enum class ModoOperacion {
    NINGUNO,
    NODO,
    PANEL
}

enum class MedioConexion {
    BLUETOOTH,
    WIFI
}

object Sesion {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var jobLectura: Job? = null
    private var jobTemporizadorAutoLock: Job? = null

    private var appContext: Context? = null
    var enlaceActivo: Enlace? = null
        private set

    val modo = MutableStateFlow(ModoOperacion.NINGUNO)
    val medio = MutableStateFlow(MedioConexion.BLUETOOTH)
    val rolUsuario = MutableStateFlow("OBSERVADOR") // "OPERADOR" o "OBSERVADOR"

    private val _estadoEnlace = MutableStateFlow(EstadoEnlace.DESCONECTADO)
    val estadoEnlace: StateFlow<EstadoEnlace> = _estadoEnlace.asStateFlow()

    private val _distancia = MutableStateFlow(50.0) // cm
    val distancia: StateFlow<Double> = _distancia.asStateFlow()

    private val _latchState = MutableStateFlow("LOCKED") // "LOCKED" o "UNLOCKED"
    val latchState: StateFlow<String> = _latchState.asStateFlow()

    private val _ultimoLog = MutableStateFlow("Sistema inicializado.")
    val ultimoLog: StateFlow<String> = _ultimoLog.asStateFlow()

    private var claveCifrado: SecretKey? = null

    fun inicializar(context: Context) {
        this.appContext = context.applicationContext
        Notificaciones.inicializarCanal(context)
    }

    fun configurarCifrado(codigo6Digitos: String) {
        if (codigo6Digitos.length >= 6) {
            claveCifrado = Cripto.claveDesdeCodigo(codigo6Digitos)
        } else {
            claveCifrado = null
        }
    }

    fun iniciarNodo(medioSel: MedioConexion, codigoPin: String) {
        modo.value = ModoOperacion.NODO
        medio.value = medioSel
        configurarCifrado(codigoPin)

        cerrarEnlace()
        val nuevoEnlace: Enlace = if (medioSel == MedioConexion.BLUETOOTH) {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            BluetoothEnlace(adapter)
        } else {
            WifiEnlace(5050)
        }
        enlaceActivo = nuevoEnlace
        vincularObservadores(nuevoEnlace)
        nuevoEnlace.iniciarServidor(claveCifrado)
        _ultimoLog.value = "Nodo iniciado en modo Servidor ($medioSel)"
    }

    fun conectarPanel(medioSel: MedioConexion, destino: String, codigoPin: String) {
        modo.value = ModoOperacion.PANEL
        medio.value = medioSel
        configurarCifrado(codigoPin)

        cerrarEnlace()
        val nuevoEnlace: Enlace = if (medioSel == MedioConexion.BLUETOOTH) {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            BluetoothEnlace(adapter)
        } else {
            WifiEnlace(5050)
        }
        enlaceActivo = nuevoEnlace
        vincularObservadores(nuevoEnlace)
        nuevoEnlace.conectar(destino, claveCifrado)
        _ultimoLog.value = "Panel conectando a $destino vía $medioSel..."
    }

    private fun vincularObservadores(enlace: Enlace) {
        jobLectura?.cancel()
        jobLectura = scope.launch {
            launch {
                enlace.estado.collectLatest { est ->
                    _estadoEnlace.value = est
                    if (est == EstadoEnlace.DESCONECTADO || est == EstadoEnlace.ERROR) {
                        // OT Fail-Secure: Retorno inmediato a bloqueo si se corta el enlace
                        if (modo.value == ModoOperacion.NODO && _latchState.value != "LOCKED") {
                            forzarBloqueoSeguro("Enlace interrumpido - Fail-Secure activado")
                        }
                    }
                }
            }

            launch {
                enlace.mensajesEntrantes.collectLatest { msg ->
                    procesarMensajeEntrante(msg)
                }
            }
        }
    }

    private fun procesarMensajeEntrante(msg: Mensaje) {
        when (modo.value) {
            ModoOperacion.NODO -> {
                when (msg.tipo) {
                    TipoMensaje.CMD -> {
                        if (msg.clave == "latch") {
                            if (msg.valor == "OPEN") {
                                accionarAperturaNodo(fuente = "COMANDO_REMOTO")
                            } else if (msg.valor == "LOCK") {
                                forzarBloqueoSeguro("Bloqueo remoto solicitado")
                            }
                        }
                    }
                    else -> {}
                }
            }
            ModoOperacion.PANEL -> {
                when (msg.tipo) {
                    TipoMensaje.LECTURA -> {
                        if (msg.clave == "dist") {
                            val d = msg.valor.toDoubleOrNull() ?: 50.0
                            _distancia.value = d
                        } else if (msg.clave == "latch") {
                            _latchState.value = msg.valor
                        }
                    }
                    TipoMensaje.ACK -> {
                        if (msg.clave == "latch") {
                            _latchState.value = msg.valor
                            _ultimoLog.value = "Confirmación de actuador: ${msg.valor}"
                            appContext?.let { ctx ->
                                if (msg.valor == "OPEN") {
                                    Notificaciones.mostrarNotificacion(
                                        ctx,
                                        "Pestillo Abierto",
                                        "El pasador se ha retraído exitosamente."
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            ModoOperacion.NINGUNO -> {}
        }
    }

    /**
     * Lógica de apertura del Nodo IoT (Física o Remota)
     */
    fun accionarAperturaNodo(fuente: String) {
        if (modo.value != ModoOperacion.NODO) return

        _latchState.value = "UNLOCKED"
        _ultimoLog.value = "Pestillo ABIERTO ($fuente)"

        // Notificar por enlace
        enlaceActivo?.enviar(Mensaje(TipoMensaje.ACK, "latch", "OPEN"))
        enlaceActivo?.enviar(Mensaje(TipoMensaje.LECTURA, "latch", "UNLOCKED"))

        // Alerta local
        appContext?.let { ctx ->
            Notificaciones.mostrarNotificacion(
                ctx,
                "Acceso Concedido",
                "Pestillo destrabado por $fuente. Cierre automático en 3s."
            )
        }

        // Registrar en Firestore
        FirestoreService.registrarEventoAcceso(
            accion = "APERTURA_$fuente",
            estado = "UNLOCKED",
            distancia = _distancia.value
        )

        // Temporizador de auto-cierre tras 3 segundos (IEC 62443 Fail-Secure)
        jobTemporizadorAutoLock?.cancel()
        jobTemporizadorAutoLock = scope.launch {
            delay(3000)
            forzarBloqueoSeguro("Auto-cierre tras 3s")
        }
    }

    /**
     * Simulación de lectura de distancia ultrasónica desde la UI del Nodo
     */
    fun simularLecturaDistanciaNodo(distanciaCm: Double) {
        _distancia.value = distanciaCm
        if (modo.value == ModoOperacion.NODO) {
            enlaceActivo?.enviar(Mensaje(TipoMensaje.LECTURA, "dist", String.format(java.util.Locale.US, "%.1f", distanciaCm)))

            // Si detecta objeto < 8 cm (proximidad / request-to-exit)
            if (distanciaCm < 8.0 && _latchState.value == "LOCKED") {
                accionarAperturaNodo("SENSOR_PROXIMIDAD")
            }
        }
    }

    /**
     * Enviar orden de apertura desde el Panel Operador
     */
    fun solicitarAperturaRemota(): Boolean {
        if (rolUsuario.value != "OPERADOR") {
            _ultimoLog.value = "ACCESO DENEGADO: Rol de Observador sin permiso de apertura."
            return false
        }
        if (_estadoEnlace.value != EstadoEnlace.CONECTADO) {
            _ultimoLog.value = "ERROR: Enlace no conectado."
            return false
        }

        enlaceActivo?.enviar(Mensaje(TipoMensaje.CMD, "latch", "OPEN"))
        _ultimoLog.value = "Comando de apertura enviado..."
        return true
    }

    fun forzarBloqueoSeguro(razon: String) {
        _latchState.value = "LOCKED"
        _ultimoLog.value = "Pestillo BLOQUEADO ($razon)"

        enlaceActivo?.enviar(Mensaje(TipoMensaje.ACK, "latch", "LOCKED"))
        enlaceActivo?.enviar(Mensaje(TipoMensaje.LECTURA, "latch", "LOCKED"))

        FirestoreService.registrarEventoAcceso(
            accion = "BLOQUEO_FAILSAFE",
            estado = "LOCKED",
            distancia = _distancia.value
        )
    }

    fun cerrarEnlace() {
        jobLectura?.cancel()
        jobTemporizadorAutoLock?.cancel()
        enlaceActivo?.cerrar()
        enlaceActivo = null
        _estadoEnlace.value = EstadoEnlace.DESCONECTADO
    }
}
