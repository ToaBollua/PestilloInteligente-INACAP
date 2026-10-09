package cl.inacap.pestilloiot

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import java.security.SecureRandom
import java.util.Base64

/**
 * Pruebas unitarias nativas para los algoritmos criptográficos del sistema:
 * - Derivación de claves PBKDF2 (HMAC-SHA256, 10.000 iteraciones)
 * - Cifrado autenticado AES-256-GCM con detección de manipulación (Tag 128-bit)
 */
class CriptoUnitTest {

    private val salt = "Inacap_Pestillo_IoT_2026_Salt".toByteArray(Charsets.UTF_8)

    private fun derivarClave(pin: String): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun cifrarAESGCM(textoPlano: String, clave: SecretKeySpec): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, clave, gcmSpec)
        val bytesTexto = textoPlano.toByteArray(Charsets.UTF_8)
        val textoCifrado = cipher.doFinal(bytesTexto)
        val combinado = ByteArray(iv.size + textoCifrado.size)
        System.arraycopy(iv, 0, combinado, 0, iv.size)
        System.arraycopy(textoCifrado, 0, combinado, iv.size, textoCifrado.size)
        return Base64.getEncoder().encodeToString(combinado)
    }

    private fun descifrarAESGCM(payloadBase64: String, clave: SecretKeySpec): String? {
        return try {
            val bytes = Base64.getDecoder().decode(payloadBase64)
            if (bytes.size < 12 + 16) return null
            val iv = ByteArray(12)
            val longitudCifrado = bytes.size - 12
            val bytesCifrados = ByteArray(longitudCifrado)
            System.arraycopy(bytes, 0, iv, 0, 12)
            System.arraycopy(bytes, 12, bytesCifrados, 0, longitudCifrado)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, clave, gcmSpec)
            String(cipher.doFinal(bytesCifrados), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun testDerivacionPBKDF2Consistente() {
        val clave1 = derivarClave("123456")
        val clave2 = derivarClave("123456")
        val claveDistinta = derivarClave("654321")
        assertArrayEquals("La misma semilla debe generar idéntica llave de 256 bits", clave1.encoded, clave2.encoded)
        assertFalse("Semillas distintas deben producir llaves distintas", clave1.encoded.contentEquals(claveDistinta.encoded))
        assertEquals(32, clave1.encoded.size) // 256 bits = 32 bytes
    }

    @Test
    fun testCifradoYDescifradoAES256GCM() {
        val clave = derivarClave("123456")
        val comandoOriginal = "CMD;latch;OPEN"
        val payloadCifrado = cifrarAESGCM(comandoOriginal, clave)
        assertNotNull(payloadCifrado)
        assertNotEquals(comandoOriginal, payloadCifrado)
        val comandoRecuperado = descifrarAESGCM(payloadCifrado, clave)
        assertEquals(comandoOriginal, comandoRecuperado)
    }

    @Test
    fun testDeteccionManipulacionGCMTagInvalido() {
        val clave = derivarClave("123456")
        val comandoOriginal = "CMD;latch;OPEN"
        val payloadBase64 = cifrarAESGCM(comandoOriginal, clave)
        val bytes = Base64.getDecoder().decode(payloadBase64)
        // Corromper el último byte del Auth Tag (integridad comprometida)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        val payloadAlterado = Base64.getEncoder().encodeToString(bytes)
        val resultado = descifrarAESGCM(payloadAlterado, clave)
        assertNull("Un paquete con Tag GCM corrompido DEBE ser rechazado matemáticamente (null)", resultado)
    }

    @Test
    fun testRechazoClaveIncorrecta() {
        val claveCorrecta = derivarClave("123456")
        val claveErronea = derivarClave("999999")
        val payload = cifrarAESGCM("CMD;latch;LOCK", claveCorrecta)
        val resultado = descifrarAESGCM(payload, claveErronea)
        assertNull("Descifrar con PIN erróneo debe retornar null sin arrojar excepción no capturada", resultado)
    }
}
