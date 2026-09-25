package cl.inacap.pestilloiot.seguridad

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Motor Criptográfico del Sistema Pestillo IoT.
 * Implementa ISO/IEC 27400:2022 mediante cifrado simétrico autenticado AES-256-GCM
 * y derivación de claves mediante PBKDF2 con HMAC-SHA256.
 */
object Cripto {

    private const val ALGORITMO_CIPHER = "AES/GCM/NoPadding"
    private const val TAMANO_TAG_BITS = 128
    private const val TAMANO_IV_BYTES = 12
    private const val ITERACIONES_PBKDF2 = 10000
    private const val TAMANO_CLAVE_BITS = 256

    // Salt fijo pre-compartido para la derivación del código de 6 dígitos del clúster
    private val SALT_PRECOMPARTIDO = "Inacap_Pestillo_IoT_2026_Salt".toByteArray(Charsets.UTF_8)

    /**
     * Deriva una clave secreta AES-256 a partir de un código numérico de 6 dígitos (o passphrase).
     */
    fun claveDesdeCodigo(codigo: String): SecretKey {
        val spec = PBEKeySpec(codigo.toCharArray(), SALT_PRECOMPARTIDO, ITERACIONES_PBKDF2, TAMANO_CLAVE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytesClave = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytesClave, "AES")
    }

    /**
     * Cifra un texto plano usando AES-256-GCM.
     * Retorna una cadena codificada en Base64 con la estructura: [IV (12 bytes) + Ciphertext + AuthTag (16 bytes)]
     */
    fun cifrar(textoPlano: String, clave: SecretKey): String {
        val iv = ByteArray(TAMANO_IV_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITMO_CIPHER)
        val gcmSpec = GCMParameterSpec(TAMANO_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, clave, gcmSpec)

        val bytesTexto = textoPlano.toByteArray(Charsets.UTF_8)
        val textoCifrado = cipher.doFinal(bytesTexto)

        // Concatenar IV + Texto Cifrado (incluye Tag GCM al final)
        val resultado = ByteArray(iv.size + textoCifrado.size)
        System.arraycopy(iv, 0, resultado, 0, iv.size)
        System.arraycopy(textoCifrado, 0, resultado, iv.size, textoCifrado.size)

        return Base64.encodeToString(resultado, Base64.NO_WRAP)
    }

    /**
     * Descifra una cadena en Base64 usando AES-256-GCM y la clave provista.
     * Retorna el texto plano o null si la autenticación GCM falla o la clave es incorrecta.
     */
    fun descifrar(payloadBase64: String, clave: SecretKey): String? {
        return try {
            val bytesTotales = Base64.decode(payloadBase64, Base64.NO_WRAP)
            if (bytesTotales.size < TAMANO_IV_BYTES + (TAMANO_TAG_BITS / 8)) {
                return null
            }

            val iv = ByteArray(TAMANO_IV_BYTES)
            val longitudCifrado = bytesTotales.size - TAMANO_IV_BYTES
            val bytesCifrados = ByteArray(longitudCifrado)

            System.arraycopy(bytesTotales, 0, iv, 0, TAMANO_IV_BYTES)
            System.arraycopy(bytesTotales, TAMANO_IV_BYTES, bytesCifrados, 0, longitudCifrado)

            val cipher = Cipher.getInstance(ALGORITMO_CIPHER)
            val gcmSpec = GCMParameterSpec(TAMANO_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, clave, gcmSpec)

            val bytesDescifrados = cipher.doFinal(bytesCifrados)
            String(bytesDescifrados, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
