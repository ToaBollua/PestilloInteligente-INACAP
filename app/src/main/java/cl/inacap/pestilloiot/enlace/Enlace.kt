package cl.inacap.pestilloiot.enlace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.crypto.SecretKey

enum class TipoMensaje {
    LECTURA,
    CMD,
    ACK,
    INFO
}

enum class EstadoEnlace {
    DESCONECTADO,
    CONECTANDO,
    ESCUCHANDO,
    CONECTADO,
    ERROR
}

/**
 * Representa una trama estructurada del protocolo: TIPO;CLAVE;VALOR
 */
data class Mensaje(
    val tipo: TipoMensaje,
    val clave: String,
    val valor: String
) {
    fun aLinea(): String = "${tipo.name};${clave.trim()};${valor.trim()}"

    companion object {
        fun desdeLinea(linea: String): Mensaje? {
            val partes = linea.trim().split(";")
            if (partes.size < 3) return null

            val tipo = try {
                TipoMensaje.valueOf(partes[0].trim().uppercase())
            } catch (e: Exception) {
                return null
            }

            val clave = partes[1].trim()
            val valor = partes[2].trim()

            // Guardarraíl: rechazar claves o valores anormalmente largos
            if (clave.length > 32 || valor.length > 64) return null

            return Mensaje(tipo, clave, valor)
        }
    }
}

interface Enlace {
    val estado: StateFlow<EstadoEnlace>
    val mensajesEntrantes: SharedFlow<Mensaje>
    fun iniciarServidor(claveCifrado: SecretKey?)
    fun conectar(destino: String, claveCifrado: SecretKey?)
    fun enviar(mensaje: Mensaje)
    fun cerrar()
}

abstract class EnlaceBase : Enlace {
    protected val scope = CoroutineScope(Dispatchers.IO + Job())

    protected val _estado = MutableStateFlow(EstadoEnlace.DESCONECTADO)
    override val estado: StateFlow<EstadoEnlace> = _estado.asStateFlow()

    protected val _mensajesEntrantes = MutableSharedFlow<Mensaje>(extraBufferCapacity = 64)
    override val mensajesEntrantes: SharedFlow<Mensaje> = _mensajesEntrantes.asSharedFlow()

    protected var claveActiva: SecretKey? = null
    protected var escritor: PrintWriter? = null
    protected var lector: BufferedReader? = null

    override fun enviar(mensaje: Mensaje) {
        val lineaPlana = mensaje.aLinea()
        scope.launch(Dispatchers.IO) {
            try {
                val lineaAEnviar = if (claveActiva != null) {
                    cl.inacap.pestilloiot.seguridad.Cripto.cifrar(lineaPlana, claveActiva!!)
                } else {
                    lineaPlana
                }
                escritor?.println(lineaAEnviar)
                escritor?.flush()
            } catch (e: Exception) {
                _estado.value = EstadoEnlace.ERROR
            }
        }
    }

    protected fun bucleLectura(inputStream: InputStream, outputStream: OutputStream) {
        lector = inputStream.bufferedReader(Charsets.UTF_8)
        escritor = PrintWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), true)
        _estado.value = EstadoEnlace.CONECTADO

        try {
            while (_estado.value == EstadoEnlace.CONECTADO) {
                val linea = lector?.readLine() ?: break
                if (linea.isBlank()) continue

                val lineaPlana = if (claveActiva != null) {
                    cl.inacap.pestilloiot.seguridad.Cripto.descifrar(linea, claveActiva!!)
                } else {
                    linea
                }

                if (lineaPlana != null) {
                    val mensaje = Mensaje.desdeLinea(lineaPlana)
                    if (mensaje != null) {
                        _mensajesEntrantes.tryEmit(mensaje)
                    }
                }
            }
        } catch (e: Exception) {
            // Error de conexión o socket cerrado
        } finally {
            cerrar()
        }
    }
}
