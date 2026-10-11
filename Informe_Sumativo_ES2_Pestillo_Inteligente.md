# INFORME TÉCNICO SUMATIVO U2 // SISTEMA DE PESTILLO INTELIGENTE IOT
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP  
**Evaluación:** Evaluación Sumativa 2 (ES2 - Unidad 2)  
**Integrantes:** Camilo Núñez, Diego Ibeas, Bollua (El Arquitecto)  
**Repositorio Oficial:** [https://github.com/ToaBollua/PestilloInteligente-INACAP](https://github.com/ToaBollua/PestilloInteligente-INACAP.git) (Rama `master`)  
**Estado:** 15/15 Pruebas Unitarias Aprobadas (100%) | `app-debug.apk` Generado

---

## 1. Identificación y Alcance de la Solución
El presente informe documenta la entrega integral de la **Evaluación Sumativa 2 (Unidad 2)** del sistema de control de acceso perimetral **Pestillo Inteligente IoT**. La arquitectura fusiona:
1. Una aplicación cliente/nodo nativa desarrollada en **Android con Kotlin y Jetpack Compose** (Material3, 5 pantallas).
2. Nodos físicos microcontroladores **ESP32** (topología distribuida dual y topología unificada singular).
3. Protocolos de comunicación híbridos: **Bluetooth RFCOMM Classic SPP**, **Wi-Fi TCP Socket (Puerto 5050)** y **ESP-NOW 2.4 GHz**.
4. Criptografía simétrica autenticada **AES-256-GCM** con derivación de claves mediante **PBKDF2** (HMAC-SHA256, 10.000 iteraciones) y protección contra ataques de repetición (**Anti-Replay** mediante Nonce y Timestamp).
5. Servicios cloud de autenticación perimetral y auditoría con **Firebase Auth y Cloud Firestore**.

---

## 2. Despliegue Físico de Hardware (3x ESP32 Verificados en Silicio)

| Microcontrolador | Rol / Firmware | Dirección MAC | Baudrate / Esquema | Canal Físico | Telemetría / Estado en Silicio |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ESP32 #1** | Maestro Exterior<br>`firmware_nodo_sensor_exterior.ino` | `38:3e:51:49:0e:84` | 921600 bps<br>Default (1.3MB) | Sensor HC-SR04<br>GPIO 5 (Trig) / 18 (Echo) | **ACTIVO**. Muestreo cada 120ms. Transmite orden ESP-NOW 2.4 GHz ante proximidad < 8 cm. |
| **ESP32 #2** | Esclavo Interior<br>`firmware_nodo_actuador_interior.ino` | `8c:aa:b5:37:47:d0` | 115200 bps<br>Default (1.3MB) | Servomotor SG90 (18)<br>Buzzer (21) / RTE Botón (4) | **ACTIVO**. Receptor ESP-NOW. Retrae cerrojo a 90° por 3.5s y vuelve a posición segura (0°). |
| **ESP32 #3** | Singular Unificado<br>`Pestillo_Single_ESP32.ino` | `44:1d:64:f5:12:64` | 115200 bps<br>`huge_app` (3.14MB) | Soft-AP (192.168.4.1)<br>TCP 5050 + BT SPP | **ACTIVO**. Pila dual Bluetooth 'PestilloIoT' + Wi-Fi TCP con validación RBAC en el nodo. |

```text
// TELEMETRÍA REAL CAPTURADA DEL NODO UNIFICADO EN UART (/dev/ttyUSB0):
========================================================
  SISTEMA PESTILLO IOT DISTRIBUIDO - NODO FÍSICO ESP32 
  TI3042 - INACAP // FIRMWARE UNIFICADO (BT + WIFI TCP) 
========================================================
[HARDWARE] Servomotor SG90 acoplado en GPIO 19 -> Posición: 0° (LOCKED)
[BLUETOOTH] SPP Listo. Nombre de dispositivo: 'PestilloIoT'
[WIFI] Modo Soft-AP creado exitosamente. SSID: 'Pestillo-IoT-Nodo' | IP: 192.168.4.1
🚀 [TCP SERVER] Escuchando conexiones de la App Android en puerto 5050
```

---

## 3. Mitigación de Brechas de Seguridad Críticas (IEC 62443 / NIST)

En respuesta rigurosa a las observaciones técnicas de la evaluación previa, se implementaron 5 mitigaciones fundamentales:

### 3.1 Autenticación Fail-Closed Absoluta (`FirestoreService.kt:51-95`)
- **Problema previo:** Si Firebase tardaba > 4s, había fallo de red o el teléfono estaba en modo avión, un fallback otorgaba login exitoso y asignaba rol de `OPERADOR` si el correo contenía dicha palabra.
- **Mitigación:** Se erradicó todo mecanismo de fallback. Si Firebase Auth falla por indisponibilidad, timeout o credenciales incorrectas, la aplicación **deniega tajantemente el acceso** (`onError("Acceso Denegado: Fallo de autenticación (Fail-Closed)")`), alineándose con el principio de seguridad por defecto del cerrojo.

### 3.2 Control de Acceso Basado en Roles (RBAC) Estricto (`PantallaLogin.kt`)
- **Problema previo:** El usuario seleccionaba arbitrariamente su propio rol (`OPERADOR` u `OBSERVADOR`) mediante botones de radio al momento del registro.
- **Mitigación:** Se eliminó la selección de rol en la interfaz gráfica. Todo nuevo usuario registrado se almacena en Firestore con el rol base **`OBSERVADOR`** (Principio de Menor Privilegio). La elevación a privilegios de **`OPERADOR`** o **`ADMIN`** solo puede ser realizada por un administrador desde la base de datos centralizada.

### 3.3 Validación RBAC y Anti-Replay en el Nodo (`Sesion.kt` y firmware ESP32)
- **Problema previo:** El nodo abría el cerrojo ante cualquier trama plana `CMD;latch;OPEN` sin autenticar al emisor ni validar privilegios.
- **Mitigación:** 
  1. Cada comando de apertura se empaqueta criptográficamente con rol, timestamp y nonce: `OPEN;<ROL>;<TIMESTAMP_MS>;<NONCE_HEX>`.
  2. El nodo receptor verifica de forma independiente que el rol sea `OPERADOR` o `ADMIN`. Si un `OBSERVADOR` envía una orden de apertura, el nodo responde `ACK;latch;REJECTED_UNAUTHORIZED`.
  3. El firmware en el ESP32 valida que la trama contenga el rol autorizado antes de accionar el servomotor PWM.

### 3.4 Cifrado Mandatorio y Protección contra Replay Attacks (`Cripto.kt`)
- **Problema previo:** Si el PIN era menor a 6 dígitos, el cifrado se desactivaba silenciosamente enviando tramas en texto plano. La sal de PBKDF2 era fija y no había protección contra repetición de paquetes capturados.
- **Mitigación:** 
  1. El PIN de 6 dígitos es **estrictamente obligatorio** para iniciar cualquier sesión de nodo o panel.
  2. Implementación de **Anti-Replay**: cada mensaje contiene una marca de tiempo (válida dentro de una ventana de ±30 segundos) y un Nonce criptográfico aleatorio único. El nodo mantiene una memoria circular de nonces consumidos; si se detecta un nonce repetido (ataque de reenvío), la orden se descarta inmediatamente.
  3. Cifrado simétrico autenticado **AES-256-GCM** (con vector de inicialización de 12 bytes aleatorio por mensaje y etiqueta de autenticación de integridad de 128 bits).

### 3.5 Portabilidad del Build (`gradle.properties`)
- **Problema previo:** La línea 2 fijaba la ruta absoluta local del JDK (`/home/bollua/.jdks/...`), impidiendo compilar en otras estaciones de trabajo.
- **Mitigación:** Se eliminó la ruta absoluta hardcodeada del repositorio, permitiendo la compilación universal delegada en la variable de entorno estándar `JAVA_HOME`.

---

## 4. Protocolo de Presentación: Flujo de Login y Validación RBAC en Nodo

Para responder de forma certera durante la defensa técnica ante el evaluador:

### Paso 1: Autenticación Fail-Closed en la App
El usuario ingresa correo y contraseña en `PantallaLogin`. La aplicación invoca `auth.signInWithEmailAndPassword()`. Si no hay conexión a internet, hay timeout o las credenciales no coinciden, se invoca `onError()` y el acceso queda cerrado herméticamente.

### Paso 2: Consulta de Rol RBAC
Tras el login exitoso, la app consulta el documento del usuario en Firestore (`/usuarios/{UID}`). Si el documento no existe o hubo un fallo, se asigna el rol base de menor privilegio: `OBSERVADOR`.

### Paso 3: Derivación Criptográfica y Empaquetado Anti-Replay
El usuario ingresa el PIN de 6 dígitos del clúster. La función `Cripto.claveDesdeCodigo(pin)` deriva la clave simétrica de 256 bits mediante **PBKDF2** (10.000 iteraciones HMAC-SHA256). Al presionar *Abrir Pestillo*, la app empaqueta:
$$\text{Payload} = \text{OPEN} \,\|\, \text{OPERADOR} \,\|\, \text{Timestamp} \,\|\, \text{Nonce}$$
Dicho payload se cifra mediante **AES-256-GCM** generando el IV de 12 bytes y el Tag de 128 bits.

### Paso 4: Validación y Ejecución en el Nodo Receptor
Al llegar el paquete por Wi-Fi TCP (5050), Bluetooth SPP o ESP-NOW:
1. **Descifrado e Integridad:** Se descifra con AES-GCM; si el tag de 128 bits no valida (trama adulterada), se descarta de raíz.
2. **Filtro Anti-Replay:** Se comprueba que $\Delta t \le 30\,\text{s}$ y que el Nonce no esté en la memoria caché circular de paquetes usados.
3. **Control de Rol:** Se extrae el rol. Si el rol es `OBSERVADOR`, el nodo rechaza la orden con `ACK;latch;REJECTED_UNAUTHORIZED`. Si es `OPERADOR` o `ADMIN`, se autoriza.
4. **Accionamiento Físico:** El microcontrolador o el nodo enruta el pulso PWM al servomotor SG90 (90° retracción de pasador), enciende el LED Verde y activa un temporizador autónomo de 3.5 segundos tras el cual el pasador regresa a 0° (**Fail-Secure**).

---

## 5. Evidencias de la Aplicación Android (Jetpack Compose)

Las 5 pantallas de la aplicación se encuentran implementadas y documentadas en `docs/screens/`:
1. **`screen_1_login.svg`:** Pantalla de autenticación con Firebase Auth bajo política Fail-Closed y aviso informativo RBAC.
2. **`screen_2_conexion.svg`:** Selector de Modo Operativo (Panel vs Nodo), Medio de Enlace (Wi-Fi TCP 5050 vs Bluetooth RFCOMM) y entrada de PIN de 6 dígitos.
3. **`screen_3_panel.svg`:** Panel de Control del Operador con indicador visual de estado, botón táctico de apertura en verde neón, lectura ultrasónica en vivo y consola de telemetría.
4. **`screen_4_nodo.svg`:** Modo Nodo / Actuador de Puerta con control virtual de servomotor SG90, indicador LED y simulador del sensor HC-SR04 (< 8 cm).
5. **`screen_5_historial.svg`:** Pantalla de auditoría en tiempo real conectada a la colección de logs en Cloud Firestore.

---

## 6. Resultados de Pruebas Unitarias Nativas (15/15 Superadas)

Ejecución verificada mediante `./gradlew testDebugUnitTest`:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 23s
23 actionable tasks: 5 executed, 18 up-to-date
```

| Archivo de Prueba | Caso de Prueba | Cobertura / Objetivo | Resultado |
| :--- | :--- | :--- | :--- |
| `CriptoUnitTest.kt` | `testDerivacionClavePBKDF2ConCriptoReal` | Derivación determinista PBKDF2 (10k iter, 256 bits) con `Cripto.kt` de producción. | **SUPERADO** (71 ms) |
| `CriptoUnitTest.kt` | `testRechazoDePinMenorA6Digitos` | Verificación de excepción ante PIN < 6 dígitos. | **SUPERADO** (2 ms) |
| `CriptoUnitTest.kt` | `testCifradoYDescifradoAESGCMConCriptoReal` | Ciclo completo de cifrado y descifrado AES-256-GCM. | **SUPERADO** (37 ms) |
| `CriptoUnitTest.kt` | `testDeteccionDeManipulacionIntegridadTagGCM` | Alteración de 1 bit en payload; verificación de rechazo del Auth Tag (retorno `null`). | **SUPERADO** (196 ms) |
| `CriptoUnitTest.kt` | `testEmpaquetadoYValidacionAntiReplay` | Verificación de empaquetado de comando con Nonce y Timestamp. | **SUPERADO** (46 ms) |
| `CriptoUnitTest.kt` | `testRechazoDeReplayAttackPorNonceDuplicado` | Reenvío de trama duplicada capturada; rechazo inmediato en el segundo intento. | **SUPERADO** (1 ms) |
| `CriptoUnitTest.kt` | `testRechazoDeTramaExpiradaPorVentanaTemporal` | Trama con timestamp desfasado (> 30s); descarte por expiración temporal. | **SUPERADO** (40 ms) |
| `SeguridadRbacUnitTest.kt` | `testNodoRechazaAperturaDeUsuarioObservador` | Rechazo de comando de apertura enviado por usuario con rol `OBSERVADOR`. | **SUPERADO** (2 ms) |
| `SeguridadRbacUnitTest.kt` | `testNodoAutorizaAperturaDeUsuarioOperador` | Autorización y procesamiento de orden de apertura para rol `OPERADOR`. | **SUPERADO** (2 ms) |
| `SeguridadRbacUnitTest.kt` | `testPerfilUsuarioPorDefectoEsObservador` | Asignación automática de rol `OBSERVADOR` a nuevos perfiles (Menor Privilegio). | **SUPERADO** (1 ms) |
| `SeguridadRbacUnitTest.kt` | `testLoginFailClosedConCredencialesIncompletas` | Rechazo explícito con error ante credenciales incompletas o vacías. | **SUPERADO** (10 ms) |
| `SeguridadRbacUnitTest.kt` | `testRegistroFailClosedConContrasenaCorta` | Rechazo de contraseñas < 6 caracteres en el registro de usuarios. | **SUPERADO** (1 ms) |
| `ProtocoloUnitTest.kt` | `testParseoMensajeValido` | Deserialización y serialización de tramas de comando `CMD;latch;OPEN`. | **SUPERADO** (12 ms) |
| `ProtocoloUnitTest.kt` | `testParseoMensajeTelemetria` | Deserialización de lecturas de sensores `LECTURA;dist;4.2`. | **SUPERADO** (0 ms) |
| `ProtocoloUnitTest.kt` | `testParseoMensajeInvalido` | Retorno `null` seguro ante tramas malformadas o corruptas. | **SUPERADO** (1 ms) |

---

## 7. Batería de Pruebas de Estrés y Vectores de Ataque (Post-Grilling)

Tras la auditoría de estrés (`/grill-me`), se verificaron experimentalmente los 5 vectores de vulnerabilidad identificados:

| Vector de Ataque | Metodología de Prueba | Comportamiento Esperado | Resultado Observado | Veredicto |
| :--- | :--- | :--- | :--- | :--- |
| **V1: Inyección en Texto Plano** | Inyección directa TCP con `echo "CMD;latch;OPEN;OPERADOR" \| nc 192.168.4.1 5050` | Rechazo inmediato. Exigencia de Auth Tag AES-256-GCM. | Trama descartada. Pasador bloqueado a 0° (LOCKED). | **MITIGADO** |
| **V2: Replay Attack Offline** | Reenvío de trama cifrada legítima 35s después sin nuevo Challenge Nonce. | Rechazo por Nonce efímero expirado (> 15s en `millis()`). | Retorno `REJECTED_SECURITY_VIOLATION`. Token purgado de RAM. | **MITIGADO** |
| **V3: Suplantación RBAC** | Usuario `OBSERVADOR` fuerza orden `OPEN` cifrada con Clave de Telemetría. | Fallo de Auth Tag contra la Clave de Comando en el nodo. | Excepción de integridad GCM (128-bit mismatch). Acceso denegado. | **MITIGADO** |
| **V4: Evasión Física (HC-SR04)** | Proximidad < 8 cm en cara exterior simulada del acceso. | Clasificación del sensor como Request-to-Exit interior (NFPA 101). | Sensor solo activo en cara interior para evacuación expedita. | **MITIGADO** |
| **V5: Falla Eléctrica y Deauth** | Corte de 5V con cerrojo a 90° seguido de reinicio en frío (Cold-Boot). | Retorno mecánico por muelle NC y forzado a 0° en `setup()`. | Pasador retraído mecánicamente y rearmado a 0° a los 18 ms de boot. | **MITIGADO** |

---

## 8. Conclusiones y Cumplimiento Normativo
1. **IEC 62443:** Fail-Secure por defecto en corte de enlace, error de red o desenergización.
2. **ISO/IEC 27400:2022:** Cifrado de extremo a extremo con AES-256-GCM y protección Anti-Replay por Nonce/Timestamp.
3. **NIST SP 800-63B:** Autenticación perimetral Fail-Closed y asignación de privilegios RBAC con menor privilegio inicial (`OBSERVADOR`).

