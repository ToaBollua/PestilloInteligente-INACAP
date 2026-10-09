package cl.inacap.pestilloiot

import cl.inacap.pestilloiot.seguridad.Cripto
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias nativas sobre el motor criptográfico real [cl.inacap.pestilloiot.seguridad.Cripto]:
 * - Derivación de claves PBKDF2 (HMAC-SHA256, 10.000 iteraciones)
 * - Cifrado y autenticación simétrica AES-256-GCM (128-bit Auth Tag)
 * - Detección estricta de manipulación de integridad (Fail-Closed en descifrado)
 * - Protección Anti-Replay mediante Nonce único y ventana temporal
 */
class CriptoUnitTest {

    @Before
    fun setUp() {
        Cripto.limpiarCacheNoncesParaTests()
    }

    @Test
    fun testDerivacionClavePBKDF2ConCriptoReal() {
        val pin = "849201"
        val clave1 = Cripto.claveDesdeCodigo(pin)
        val clave2 = Cripto.claveDesdeCodigo(pin)

        assertNotNull(clave1)
        assertEquals("AES", clave1.algorithm)
        assertEquals(32, clave1.encoded.size) // 256 bits = 32 bytes
        // Determinismo criptográfico: mismo PIN y Salt generan la misma clave
        assertArrayEquals(clave1.encoded, clave2.encoded)
    }

    @Test
    fun testRechazoDePinMenorA6Digitos() {
        assertThrows(IllegalArgumentException::class.java) {
            Cripto.claveDesdeCodigo("12345") // 5 dígitos
        }
    }

    @Test
    fun testCifradoYDescifradoAESGCMConCriptoReal() {
        val clave = Cripto.claveDesdeCodigo("987654")
        val mensajeOriginal = "CMD;latch;OPEN;OPERADOR;1728500000;a8b7c6d5"

        val payloadCifrado = Cripto.cifrar(mensajeOriginal, clave)
        assertNotNull(payloadCifrado)
        assertNotEquals(mensajeOriginal, payloadCifrado)

        val textoDescifrado = Cripto.descifrar(payloadCifrado, clave)
        assertEquals(mensajeOriginal, textoDescifrado)
    }

    @Test
    fun testDeteccionDeManipulacionIntegridadTagGCM() {
        val clave = Cripto.claveDesdeCodigo("554433")
        val payloadCifrado = Cripto.cifrar("CMD;latch;OPEN", clave)

        // Simular manipulación de bits en tránsito (Man-in-the-Middle)
        val bytes = java.util.Base64.getDecoder().decode(payloadCifrado)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val payloadManipulado = java.util.Base64.getEncoder().encodeToString(bytes)

        // El algoritmo AES-GCM debe rechazar la autenticación del tag
        val resultado = Cripto.descifrar(payloadManipulado, clave)
        assertNull("El descifrado de un mensaje manipulado debe fallar (retornar null)", resultado)
    }

    @Test
    fun testEmpaquetadoYValidacionAntiReplay() {
        val trama = Cripto.empaquetarComandoSeguro("OPEN", "OPERADOR")
        val validacion = Cripto.validarAntiReplay(trama)

        assertNotNull("La trama empaquetada legítima debe ser válida", validacion)
        assertEquals("OPEN", validacion?.comando)
        assertEquals("OPERADOR", validacion?.rol)
        assertNotNull(validacion?.nonce)
        assertTrue(validacion?.nonce!!.length >= 8)
    }

    @Test
    fun testRechazoDeReplayAttackPorNonceDuplicado() {
        val trama = Cripto.empaquetarComandoSeguro("OPEN", "OPERADOR")

        // Primer envío: Debe ser aceptado
        val primerIntento = Cripto.validarAntiReplay(trama)
        assertNotNull("El primer envío con Nonce fresco debe ser válido", primerIntento)

        // Segundo envío idéntico (Ataque de Reenvío / Replay Attack): Debe ser rechazado
        val segundoIntento = Cripto.validarAntiReplay(trama)
        assertNull("El reenvío de un Nonce ya consumido debe ser rechazado inmediatamente", segundoIntento)
    }

    @Test
    fun testRechazoDeTramaExpiradaPorVentanaTemporal() {
        val tiempoPasado = System.currentTimeMillis() - 45000L // 45 segundos atrás (ventana máx: 30s)
        val tramaVieja = "OPEN;OPERADOR;$tiempoPasado;deadbeef010203"

        val validacion = Cripto.validarAntiReplay(tramaVieja)
        assertNull("Una trama con timestamp desfasado debe ser rechazada", validacion)
    }
}
