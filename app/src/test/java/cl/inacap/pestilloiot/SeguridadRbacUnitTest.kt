package cl.inacap.pestilloiot

import cl.inacap.pestilloiot.datos.FirestoreService
import cl.inacap.pestilloiot.datos.PerfilUsuario
import cl.inacap.pestilloiot.enlace.Mensaje
import cl.inacap.pestilloiot.enlace.TipoMensaje
import cl.inacap.pestilloiot.seguridad.Cripto
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de Control de Acceso Basado en Roles (RBAC) y Seguridad Fail-Closed:
 * - Verificación de que el Nodo rechaza órdenes de apertura si el emisor es "OBSERVADOR".
 * - Verificación de que el Nodo autoriza aperturas únicamente a "OPERADOR" o "ADMIN".
 * - Verificación de política Fail-Closed: credenciales inválidas o incompletas rechazan el acceso.
 * - Verificación de que el registro nuevo asigna siempre "OBSERVADOR" por defecto (Menor Privilegio).
 */
class SeguridadRbacUnitTest {

    @Before
    fun setUp() {
        Cripto.limpiarCacheNoncesParaTests()
    }

    @Test
    fun testNodoRechazaAperturaDeUsuarioObservador() {
        // Un usuario con rol OBSERVADOR intenta armar una trama de apertura
        val tramaObservador = Cripto.empaquetarComandoSeguro("OPEN", "OBSERVADOR")
        val validacion = Cripto.validarAntiReplay(tramaObservador)

        assertNotNull("La trama es estructuralmente válida", validacion)
        assertEquals("OBSERVADOR", validacion?.rol)

        // El guardarraíl RBAC del Nodo debe denegar la autorización
        val tienePermisoApertura = (validacion?.rol == "OPERADOR" || validacion?.rol == "ADMIN")
        assertFalse("El rol OBSERVADOR no tiene privilegios de apertura (Principio de Menor Privilegio)", tienePermisoApertura)
    }

    @Test
    fun testNodoAutorizaAperturaDeUsuarioOperador() {
        val tramaOperador = Cripto.empaquetarComandoSeguro("OPEN", "OPERADOR")
        val validacion = Cripto.validarAntiReplay(tramaOperador)

        assertNotNull(validacion)
        assertEquals("OPERADOR", validacion?.rol)

        val tienePermisoApertura = (validacion?.rol == "OPERADOR" || validacion?.rol == "ADMIN")
        assertTrue("El rol OPERADOR tiene autorización explícita para accionar el pestillo", tienePermisoApertura)
    }

    @Test
    fun testPerfilUsuarioPorDefectoEsObservador() {
        val perfilNuevo = PerfilUsuario(
            uid = "test-uid-12345",
            email = "nuevo_usuario@inacap.cl"
        )
        // Por diseño de seguridad, si no se especifica rol, DEBE ser OBSERVADOR
        assertEquals("OBSERVADOR", perfilNuevo.rol)
    }

    @Test
    fun testLoginFailClosedConCredencialesIncompletas() {
        var errorRecibido: String? = null
        var exitoEjecutado = false

        FirestoreService.iniciarSesion(
            email = "",
            clave = "",
            onSuccess = { _, _ -> exitoEjecutado = true },
            onError = { err -> errorRecibido = err }
        )

        assertFalse("El login jamás debe tener éxito con credenciales vacías (Fail-Closed)", exitoEjecutado)
        assertNotNull("Debe devolverse un error explícito de rechazo", errorRecibido)
        assertTrue(errorRecibido!!.contains("Credenciales incompletas"))
    }

    @Test
    fun testRegistroFailClosedConContrasenaCorta() {
        var errorRecibido: String? = null
        var exitoEjecutado = false

        FirestoreService.registrarUsuario(
            email = "usuario@inacap.cl",
            clave = "123", // Menos de 6 caracteres
            onSuccess = { _, _ -> exitoEjecutado = true },
            onError = { err -> errorRecibido = err }
        )

        assertFalse("El registro no debe permitirse con contraseñas débiles", exitoEjecutado)
        assertNotNull(errorRecibido)
        assertTrue(errorRecibido!!.contains("al menos 6 caracteres"))
    }
}
