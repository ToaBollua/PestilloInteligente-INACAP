package cl.inacap.pestilloiot.enlace

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.crypto.SecretKey

class BluetoothEnlace(
    private val adapter: BluetoothAdapter?
) : EnlaceBase() {

    companion object {
        val PESTILLO_UUID: UUID = UUID.fromString("7f3c2a10-5b8e-4d21-9c6f-2e8a4b1d9f03")
        const val NOMBRE_SERVICIO = "PestilloIoT"
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    override fun iniciarServidor(claveCifrado: SecretKey?) {
        cerrar()
        this.claveActiva = claveCifrado
        if (adapter == null || !adapter.isEnabled) {
            _estado.value = EstadoEnlace.ERROR
            return
        }

        _estado.value = EstadoEnlace.ESCUCHANDO
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(NOMBRE_SERVICIO, PESTILLO_UUID)
                val socketAceptado = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null

                if (socketAceptado != null) {
                    socket = socketAceptado
                    bucleLectura(socketAceptado.inputStream, socketAceptado.outputStream)
                } else {
                    _estado.value = EstadoEnlace.DESCONECTADO
                }
            } catch (e: Exception) {
                _estado.value = EstadoEnlace.ERROR
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun conectar(destinoMac: String, claveCifrado: SecretKey?) {
        cerrar()
        this.claveActiva = claveCifrado
        if (adapter == null || !adapter.isEnabled) {
            _estado.value = EstadoEnlace.ERROR
            return
        }

        _estado.value = EstadoEnlace.CONECTANDO
        scope.launch(Dispatchers.IO) {
            try {
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(destinoMac)
                val nuevoSocket = device.createRfcommSocketToServiceRecord(PESTILLO_UUID)
                nuevoSocket.connect()
                socket = nuevoSocket

                bucleLectura(nuevoSocket.inputStream, nuevoSocket.outputStream)
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
