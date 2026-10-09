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
    val rol: String = "OBSERVADOR", // "ADMIN", "OPERADOR" o "OBSERVADOR"
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

/**
 * Servicio de Autenticación y Telemetría en la Nube (Firebase Auth & Cloud Firestore).
 * Implementa el principio estricto de Fail-Closed (Seguridad por Defecto):
 * - Ante cualquier error de red, timeout o credencial no verificada, se DENEGA el acceso.
 * - Los nuevos usuarios se registran exclusivamente con rol base "OBSERVADOR" (Menor Privilegio).
 * - La elevación a "OPERADOR" o "ADMIN" requiere intervención explícita en base de datos.
 */
object FirestoreService {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private const val COLECCION_USUARIOS = "usuarios"
    private const val COLECCION_LOGS = "accesos_log"

    fun usuarioActual(): FirebaseUser? = try { auth.currentUser } catch (_: Exception) { null }

    /**
     * Inicia sesión con Firebase Auth bajo política Fail-Closed.
     * En caso de error de red, credenciales inválidas o indisponibilidad del servicio,
     * se notifica un error explícito y se aborta el acceso.
     */
    fun iniciarSesion(
        email: String,
        clave: String,
        onSuccess: (String, String) -> Unit, // email, rol
        onError: (String) -> Unit
    ) {
        val emailTrim = email.trim()
        if (emailTrim.isBlank() || clave.isBlank()) {
            onError("Credenciales incompletas: Ingrese correo y contraseña")
            return
        }

        try {
            auth.signInWithEmailAndPassword(emailTrim, clave)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        obtenerRolUsuario(user.uid) { rol ->
                            onSuccess(user.email ?: emailTrim, rol)
                        }
                    } else {
                        onError("Acceso Denegado: Usuario no encontrado tras autenticación (Fail-Closed)")
                    }
                }
                .addOnFailureListener { e ->
                    val msg = e.localizedMessage ?: "Error de autenticación"
                    onError("Acceso Denegado: $msg (Fail-Closed)")
                }
        } catch (ex: Exception) {
            onError("Acceso Denegado: Excepción en servicio de autenticación: ${ex.message} (Fail-Closed)")
        }
    }

    /**
     * Registra un nuevo usuario en Firebase Auth y crea su documento en Firestore
     * con rol inicial estrictamente asignado a "OBSERVADOR" (Principio de Menor Privilegio).
     */
    fun registrarUsuario(
        email: String,
        clave: String,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val emailTrim = email.trim()
        if (emailTrim.isBlank() || clave.length < 6) {
            onError("La contraseña debe tener al menos 6 caracteres")
            return
        }

        val rolAsignado = "OBSERVADOR" // Todo nuevo usuario inicia como OBSERVADOR

        try {
            auth.createUserWithEmailAndPassword(emailTrim, clave)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        val perfil = PerfilUsuario(
                            uid = user.uid,
                            email = user.email ?: emailTrim,
                            rol = rolAsignado
                        )
                        db.collection(COLECCION_USUARIOS).document(user.uid)
                            .set(perfil)
                            .addOnSuccessListener {
                                onSuccess(user.email ?: emailTrim, rolAsignado)
                            }
                            .addOnFailureListener { e ->
                                onError("Fallo al persistir perfil de usuario en base de datos: ${e.localizedMessage}")
                            }
                    } else {
                        onError("Fallo al crear usuario: Instancia nula")
                    }
                }
                .addOnFailureListener { e ->
                    onError("Fallo en registro de credenciales: ${e.localizedMessage}")
                }
        } catch (ex: Exception) {
            onError("Excepción en registro: ${ex.message}")
        }
    }

    /**
     * Obtiene el rol RBAC de Firestore para un UID determinado.
     * Si no se encuentra el documento o hay error, asigna "OBSERVADOR" como failsafe de menor privilegio.
     */
    fun obtenerRolUsuario(uid: String, onResult: (String) -> Unit) {
        try {
            db.collection(COLECCION_USUARIOS).document(uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val rol = snapshot.getString("rol") ?: "OBSERVADOR"
                    onResult(rol.uppercase())
                }
                .addOnFailureListener {
                    onResult("OBSERVADOR")
                }
        } catch (_: Exception) {
            onResult("OBSERVADOR")
        }
    }

    fun registrarEventoAcceso(
        accion: String,
        estado: String,
        distancia: Double = 0.0,
        email: String = try { auth.currentUser?.email ?: "observador@inacap.cl" } catch (_: Exception) { "observador@inacap.cl" },
        rol: String = "OBSERVADOR"
    ) {
        val logMap = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "email" to email,
            "rol" to rol,
            "accion" to accion,
            "estado" to estado,
            "distancia" to distancia
        )

        try {
            db.collection(COLECCION_LOGS)
                .add(logMap)
                .addOnSuccessListener { _ -> }
                .addOnFailureListener { _ -> }
        } catch (_: Exception) {}
    }

    fun obtenerHistorialStream(): Flow<List<AccesoLog>> = callbackFlow {
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            listener = db.collection(COLECCION_LOGS)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val listaCloud = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val rawTs = doc.get("timestamp")
                            val ts = when (rawTs) {
                                is Number -> rawTs.toLong()
                                is String -> rawTs.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                            val rawDist = doc.get("distancia")
                            val dist = when (rawDist) {
                                is Number -> rawDist.toDouble()
                                is String -> rawDist.toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }
                            AccesoLog(
                                id = doc.id,
                                timestamp = if (ts > 0) ts else System.currentTimeMillis(),
                                email = doc.getString("email") ?: "observador@inacap.cl",
                                rol = doc.getString("rol") ?: "OBSERVADOR",
                                accion = doc.getString("accion") ?: "EVENTO_ACCESO",
                                estado = doc.getString("estado") ?: "LOCKED",
                                distancia = dist
                            )
                        } catch (_: Exception) {
                            null
                        }
                    } ?: emptyList()

                    trySend(listaCloud)
                }
        } catch (_: Exception) {
            trySend(emptyList())
        }

        awaitClose { listener?.remove() }
    }

    fun cerrarSesion() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
    }
}
