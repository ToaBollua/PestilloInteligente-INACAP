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

    fun usuarioActual(): FirebaseUser? = auth.currentUser

    fun iniciarSesion(
        email: String,
        clave: String,
        onSuccess: (FirebaseUser, String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || clave.isBlank()) {
            onError("Credenciales incompletas")
            return
        }

        auth.signInWithEmailAndPassword(email.trim(), clave)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    obtenerRolUsuario(user.uid) { rol ->
                        onSuccess(user, rol)
                    }
                } else {
                    onError("Usuario nulo tras autenticación")
                }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Error de autenticación")
            }
    }

    fun registrarUsuario(
        email: String,
        clave: String,
        rol: String,
        onSuccess: (FirebaseUser, String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || clave.length < 6) {
            onError("La contraseña debe tener al menos 6 caracteres")
            return
        }

        auth.createUserWithEmailAndPassword(email.trim(), clave)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    val perfil = PerfilUsuario(
                        uid = user.uid,
                        email = user.email ?: email,
                        rol = rol.uppercase()
                    )
                    db.collection(COLECCION_USUARIOS).document(user.uid)
                        .set(perfil)
                        .addOnSuccessListener {
                            onSuccess(user, perfil.rol)
                        }
                        .addOnFailureListener { e ->
                            onError("Error guardando perfil: ${e.localizedMessage}")
                        }
                } else {
                    onError("Error al registrar usuario")
                }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Fallo al crear usuario en Firebase")
            }
    }

    fun obtenerRolUsuario(uid: String, onResult: (String) -> Unit) {
        db.collection(COLECCION_USUARIOS).document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val rol = snapshot.getString("rol") ?: "OBSERVADOR"
                onResult(rol)
            }
            .addOnFailureListener {
                onResult("OBSERVADOR")
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
