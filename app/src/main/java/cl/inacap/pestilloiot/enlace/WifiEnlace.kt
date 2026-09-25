package cl.inacap.pestilloiot.enlace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey

class WifiEnlace(
    private val puerto: Int = 5050
) : EnlaceBase() {

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    override fun iniciarServidor(claveCifrado: SecretKey?) {
        cerrar()
        this.claveActiva = claveCifrado
        _estado.value = EstadoEnlace.ESCUCHANDO

        scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(puerto)
                serverSocket = server
                val socketAceptado = server.accept()
                server.close()
                serverSocket = null

                socket = socketAceptado
                bucleLectura(socketAceptado.getInputStream(), socketAceptado.getOutputStream())
            } catch (e: Exception) {
                _estado.value = EstadoEnlace.ERROR
            }
        }
    }

    override fun conectar(ipDestino: String, claveCifrado: SecretKey?) {
        cerrar()
        this.claveActiva = claveCifrado
        _estado.value = EstadoEnlace.CONECTANDO

        scope.launch(Dispatchers.IO) {
            try {
                val nuevoSocket = Socket()
                nuevoSocket.connect(InetSocketAddress(ipDestino, puerto), 5000)
                socket = nuevoSocket

                bucleLectura(nuevoSocket.getInputStream(), nuevoSocket.getOutputStream())
            } catch (e: Exception) {
                _estado.value = EstadoEnlace.ERROR
            }
        }
    }

    override fun cerrar() {
        try {
            escritor?.close()
            lector?.close()
            socket?.close()
            serverSocket?.close()
        } catch (ignored: Exception) {}
        socket = null
        serverSocket = null
        escritor = null
        lector = null
        _estado.value = EstadoEnlace.DESCONECTADO
    }
}
