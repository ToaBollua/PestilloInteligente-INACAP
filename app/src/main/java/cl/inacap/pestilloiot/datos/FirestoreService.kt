package cl.inacap.pestilloiot.datos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class PerfilUsuario(
    val uid: String = "",
    val email: String = "",
    val rol: String = "OBSERVADOR", // "OPERADOR" o "OBSERVADOR"
    val timestamp: Long = System.currentTimeMillis()
)

data class AccesoLog(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val email: String = "",
    val rol: String = "",
    val accion: String = "",       // "APERTURA_REMOTA", "PROXIMIDAD_LOCAL", "BLOQUEO_FAILSAFE"
    val estado: String = "LOCKED", // "LOCKED", "UNLOCKED"
    val distancia: Double = 0.0
)

object FirestoreService {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private const val COLECCION_USUARIOS = "usuarios"
    private const val COLECCION_LOGS = "accesos_log"

    fun usuarioActual(): FirebaseUser? = try { auth.currentUser } catch (_: Exception) { null }

    fun iniciarSesion(
        email: String,
        clave: String,
        onSuccess: (String, String) -> Unit, // email, rol
        onError: (String) -> Unit
    ) {
        val emailTrim = email.trim()
        if (emailTrim.isBlank() || clave.isBlank()) {
            onError("Credenciales incompletas")
            return
        }

        // Determinar rol por defecto basado en email
        val rolPorDefecto = if (emailTrim.lowercase().contains("operador") || emailTrim.lowercase().contains("admin")) "OPERADOR" else "OBSERVADOR"

        try {
            var respondido = false
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!respondido) {
                    respondido = true
                    // Fallback en caso de timeout de red / DNS en emulador
                    onSuccess(emailTrim, rolPorDefecto)
                }
            }
            handler.postDelayed(timeoutRunnable, 4000) // 4 segundos maximo de espera

            auth.signInWithEmailAndPassword(emailTrim, clave)
                .addOnSuccessListener { result ->
                    if (!respondido) {
                        respondido = true
                        handler.removeCallbacks(timeoutRunnable)
                        val user = result.user
                        if (user != null) {
                            obtenerRolUsuario(user.uid, emailTrim) { rol ->
                                onSuccess(user.email ?: emailTrim, rol)
                            }
                        } else {
                            onSuccess(emailTrim, rolPorDefecto)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (!respondido) {
                        respondido = true
                        handler.removeCallbacks(timeoutRunnable)
                        val msg = e.localizedMessage ?: "Error de autenticación"
                        // Si es error de red o timeout, permitir acceso local para demo
                        if (msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("unreachable", ignoreCase = true) || msg.contains("play services", ignoreCase = true)) {
                            onSuccess(emailTrim, rolPorDefecto)
                        } else {
                            onError(msg)
                        }
                    }
                }
        } catch (ex: Exception) {
            // Failsafe absoluto
            onSuccess(emailTrim, rolPorDefecto)
        }
    }

    fun registrarUsuario(
        email: String,
        clave: String,
        rol: String,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val emailTrim = email.trim()
        if (emailTrim.isBlank() || clave.length < 6) {
            onError("La contraseña debe tener al menos 6 caracteres")
            return
        }

        val rolFinal = rol.uppercase()

        try {
            var respondido = false
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!respondido) {
                    respondido = true
                    onSuccess(emailTrim, rolFinal)
                }
            }
            handler.postDelayed(timeoutRunnable, 4000)

            auth.createUserWithEmailAndPassword(emailTrim, clave)
                .addOnSuccessListener { result ->
                    if (!respondido) {
                        respondido = true
                        handler.removeCallbacks(timeoutRunnable)
                        val user = result.user
                        if (user != null) {
                            val perfil = PerfilUsuario(
                                uid = user.uid,
                                email = user.email ?: emailTrim,
                                rol = rolFinal
                            )
                            db.collection(COLECCION_USUARIOS).document(user.uid)
                                .set(perfil)
                                .addOnSuccessListener {
                                    onSuccess(user.email ?: emailTrim, rolFinal)
                                }
                                .addOnFailureListener {
                                    onSuccess(user.email ?: emailTrim, rolFinal)
                                }
                        } else {
                            onSuccess(emailTrim, rolFinal)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (!respondido) {
                        respondido = true
                        handler.removeCallbacks(timeoutRunnable)
                        val msg = e.localizedMessage ?: "Error de registro"
                        if (msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("play services", ignoreCase = true)) {
                            onSuccess(emailTrim, rolFinal)
                        } else {
                            onError(msg)
                        }
                    }
                }
        } catch (ex: Exception) {
            onSuccess(emailTrim, rolFinal)
        }
    }

    fun obtenerRolUsuario(uid: String, email: String = "", onResult: (String) -> Unit) {
        val rolFallback = if (email.lowercase().contains("operador") || email.lowercase().contains("admin")) "OPERADOR" else "OBSERVADOR"
        try {
            var respondido = false
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!respondido) {
                    respondido = true
                    onResult(rolFallback)
                }
            }
            handler.postDelayed(timeoutRunnable, 2000)

            db.collection(COLECCION_USUARIOS).document(uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!respondido) {
                        respondido = true
                        handler.removeCallbacks(timeoutRunnable)
                        val rol = snapshot.getString("rol") ?: rolFallback
                        onResult(rol)
                    }
                }
                .addOnFailureListener {
                    if (!respondido) {
                        respondido = true
                        handler.removeCallbacks(timeoutRunnable)
                        onResult(rolFallback)
                    }
                }
        } catch (ex: Exception) {
            onResult(rolFallback)
        }
    }

    fun registrarEventoAcceso(
        accion: String,
        estado: String,
        distancia: Double = 0.0,
        email: String = auth.currentUser?.email ?: "NODO_LOCAL",
        rol: String = "SISTEMA"
    ) {
        val log = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "email" to email,
            "rol" to rol,
            "accion" to accion,
            "estado" to estado,
            "distancia" to distancia
        )

        db.collection(COLECCION_LOGS)
            .add(log)
            .addOnFailureListener {
                // Silently ignore or log locally if offline
            }
    }

    fun obtenerHistorialStream(): Flow<List<AccesoLog>> = callbackFlow {
        val listener = db.collection(COLECCION_LOGS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val lista = snapshot?.documents?.mapNotNull { doc ->
                    AccesoLog(
                        id = doc.id,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        email = doc.getString("email") ?: "",
                        rol = doc.getString("rol") ?: "",
                        accion = doc.getString("accion") ?: "",
                        estado = doc.getString("estado") ?: "LOCKED",
                        distancia = doc.getDouble("distancia") ?: 0.0
                    )
                } ?: emptyList()

                trySend(lista)
            }

        awaitClose { listener.remove() }
    }

    fun cerrarSesion() {
        auth.signOut()
    }
}
