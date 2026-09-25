package cl.inacap.pestilloiot

import cl.inacap.pestilloiot.enlace.Mensaje
import cl.inacap.pestilloiot.enlace.TipoMensaje
import org.junit.Assert.*
import org.junit.Test

class ProtocoloUnitTest {

    @Test
    fun testParseoMensajeValido() {
        val linea = "CMD;latch;OPEN"
        val msg = Mensaje.desdeLinea(linea)
        assertNotNull(msg)
        assertEquals(TipoMensaje.CMD, msg?.tipo)
        assertEquals("latch", msg?.clave)
        assertEquals("OPEN", msg?.valor)
        assertEquals(linea, msg?.aLinea())
    }

    @Test
    fun testParseoMensajeTelemetria() {
        val linea = "LECTURA;dist;4.2"
        val msg = Mensaje.desdeLinea(linea)
        assertNotNull(msg)
        assertEquals(TipoMensaje.LECTURA, msg?.tipo)
        assertEquals("dist", msg?.clave)
        assertEquals("4.2", msg?.valor)
    }

    @Test
    fun testParseoMensajeInvalido() {
        val lineaInvalida = "TIPO_DESCONOCIDO;clave"
        val msg = Mensaje.desdeLinea(lineaInvalida)
        assertNull(msg)
    }
}
