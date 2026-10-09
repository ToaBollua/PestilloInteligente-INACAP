package cl.inacap.pestilloiot.seguridad

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Motor Criptográfico del Sistema Pestillo IoT.
 * Implementa ISO/IEC 27400:2022 mediante:
 * - Cifrado simétrico autenticado AES-256-GCM (128-bit Auth Tag).
 * - Derivación robusta de claves mediante PBKDF2 con HMAC-SHA256 (10.000 iteraciones).
 * - Prevención estricta de ataques de repetición (Anti-Replay) mediante Nonce criptográfico y Timestamp.
 */
object Cripto {

    private const val ALGORITMO_CIPHER = "AES/GCM/NoPadding"
    private const val TAMANO_TAG_BITS = 128
    private const val TAMANO_IV_BYTES = 12
    private const val ITERACIONES_PBKDF2 = 10000
    private const val TAMANO_CLAVE_BITS = 256

    // Sal de dominio pre-compartida (Domain Separation Salt)
    private val SALT_PRECOMPARTIDO = "Inacap_Pestillo_IoT_2026_Salt_Domain".toByteArray(Charsets.UTF_8)

    // Memoria caché de Nonces utilizados con marca temporal para protección Anti-Replay
    private val noncesConsumidos = ConcurrentHashMap<String, Long>()
    private const val VENTANA_MAX_REPLAY_MS = 30000L // 30 segundos de vigencia

    data class TramaValidada(
        val comando: String,
        val rol: String,
        val timestamp: Long,
        val nonce: String
    )

    /**
     * Deriva una clave secreta AES-256 a partir de un código numérico de 6 dígitos.
     * Permite especificar una sal personalizada opcional para aislamiento de dispositivo.
     */
    fun claveDesdeCodigo(codigo: String, salt: ByteArray = SALT_PRECOMPARTIDO): SecretKey {
        require(codigo.length >= 6) { "El PIN de cifrado debe contener al menos 6 dígitos" }
        val spec = PBEKeySpec(codigo.toCharArray(), salt, ITERACIONES_PBKDF2, TAMANO_CLAVE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytesClave = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytesClave, "AES")
    }

    /**
     * Cifra un texto plano usando AES-256-GCM.
     * Retorna una cadena Base64 con estructura: [IV (12 bytes) + Ciphertext + AuthTag (16 bytes)].
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

        return Base64.getEncoder().encodeToString(resultado)
    }

    /**
     * Descifra una cadena en Base64 usando AES-256-GCM y la clave provista.
     * Retorna el texto plano o null si la autenticación GCM falla o la clave es incorrecta.
     */
    fun descifrar(payloadBase64: String, clave: SecretKey): String? {
        return try {
            val bytesTotales = Base64.getDecoder().decode(payloadBase64)
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

    /**
     * Construye una trama de comando con protección Anti-Replay:
     * Estructura: "<COMANDO>;<ROL>;<TIMESTAMP_MS>;<NONCE_HEX>"
     */
    fun empaquetarComandoSeguro(comando: String, rol: String): String {
        val timestamp = System.currentTimeMillis()
        val randomBytes = ByteArray(8)
        SecureRandom().nextBytes(randomBytes)
        val nonce = randomBytes.joinToString("") { "%02x".format(it) }
        return "$comando;$rol;$timestamp;$nonce"
    }

    /**
     * Valida una trama deserializada verificando la ventana temporal y el consumo único del Nonce.
     * Retorna TramaValidada si es legítima y reciente, o null si es un ataque de reenvío o inválida.
     */
    fun validarAntiReplay(trama: String, tiempoActual: Long = System.currentTimeMillis()): TramaValidada? {
        val partes = trama.split(";")
        if (partes.size < 4) return null

        val comando = partes[0]
        val rol = partes[1]
        val timestamp = partes[2].toLongOrNull() ?: return null
        val nonce = partes[3]

        // 1. Validar ventana temporal (±30s)
        val delta = tiempoActual - timestamp
        if (delta < -5000 || delta > VENTANA_MAX_REPLAY_MS) {
            return null // Trama expirada o desfasada en el tiempo
        }

        // 2. Limpieza de nonces viejos en la caché
        purgarNoncesExpirados(tiempoActual)

        // 3. Validar si el Nonce ya fue utilizado (Replay Attack)
        if (noncesConsumidos.containsKey(nonce)) {
            return null // Nonce duplicado detectado
        }

        // Registrar Nonce consumido
        noncesConsumidos[nonce] = timestamp

        return TramaValidada(comando, rol, timestamp, nonce)
    }

    fun purgarNoncesExpirados(tiempoActual: Long = System.currentTimeMillis()) {
        val iter = noncesConsumidos.entries.iterator()
        while (iter.hasNext()) {
            val entrada = iter.next()
            if (tiempoActual - entrada.value > VENTANA_MAX_REPLAY_MS) {
                iter.remove()
            }
        }
    }

    fun limpiarCacheNoncesParaTests() {
        noncesConsumidos.clear()
    }
}
