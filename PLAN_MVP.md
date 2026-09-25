# PLAN_MVP — Pestillo Inteligente IoT Distribuido
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP  
**Evaluación:** Unidad 2 (Formativa y Sumativa 25%)  
**Estudiante:** Nicolás Anrique (Bollua)  
**Fecha:** Septiembre de 2026  
**Versión:** 1.0.0-MVP  

---

## 1. Resumen Ejecutivo

### 1.1 Problemática
En accesos perimetrales residenciales e institucionales, los sistemas de cerraduras convencionales concentran el panel de lectura exterior y el actuador mecánico en un único gabinete cableado, permitiendo que un atacante fuerce la apertura puenteando las líneas eléctricas expuestas. Además, los residentes y encargados de seguridad carecen de visibilidad en tiempo real sobre el estado del cerrojo (abierto/cerrado) y no reciben alertas inmediatas ante eventos de apertura no autorizada o proximidad física prolongada, requiriendo desplazamiento físico hasta el punto de acceso para verificar o destrabar la puerta.

### 1.2 Solución Propuesta
Se desarrolla una aplicación móvil Android nativa en Kotlin con Jetpack Compose que implementa un sistema distribuido de control y monitoreo de acceso en dos modos operativos:
1. **Modo Nodo IoT (Simulador de Cerradura / Gateway Periférico):** Simula el sensor de proximidad ultrasónico exterior (< 8 cm, Request-to-Exit) y controla el estado del servomotor de tracción pasante (0° Bloqueado / 90° Abierto por 3 segundos), conmutando a estado seguro (*Fail-Secure*) ante pérdida de enlace.
2. **Modo Panel de Control (Operador de Acceso):** Panel administrativo con autenticación de usuarios y roles RBAC (Operador / Observador), monitoreo de estado en tiempo real, comando de apertura remota cifrada, persistencia de eventos en la nube (Firebase Authentication + Cloud Firestore) y notificaciones locales inmediatas ante eventos de apertura o brechas de umbral.

### 1.3 Alcance del MVP
El MVP cubre estrictamente los 14 requisitos obligatorios (M1 a M14) de la Evaluación Sumativa: interconexión inalámbrica entre dos teléfonos Android (Bluetooth RFCOMM / Wi-Fi TCP), intercambio bidireccional de lecturas y comandos con confirmación (ACK), cifrado de enlace AES-256-GCM, autenticación segura con Firebase Auth, persistencia y consulta histórica en Cloud Firestore, notificaciones de alta prioridad, cumplimiento de ISO/IEC 27400, lineamiento de seguridad industrial OT (*Fail-Secure*) y fase de testing con el firmware ESP32 de la Evaluación 1.

---

## 2. Declaración Técnica Obligatoria

| Dimensión | Decisión Técnica | Justificación Técnica |
| :--- | :--- | :--- |
| **1. Lenguaje e IDE** | **Kotlin 2.0+** en **Android Studio (Koala / Ladybug)**. `minSdk 26` (Android 8.0 Oreo), `targetSdk 35/36`. Gradle con **Kotlin DSL** (`build.gradle.kts`). | Estándar oficial de Google (*Kotlin-first*). `minSdk 26` garantiza soporte nativo para canales de notificación (`NotificationChannel`) y criptografía `javax.crypto` sin compatibilidad heredada. |
| **2. Interfaz de Usuario** | **Jetpack Compose + Material 3**. Arquitectura desacoplada en funciones `@Composable` con gestión de estado reactivo mediante `StateFlow` y `rememberSaveable`. | Menor sobrecarga de código frente a XML clásico, integración declarativa reactiva con corrutinas y diseño alineado con directivas de diseño de Android. |
| **3. Conexión y Protocolo** | **Bluetooth clásico (RFCOMM / SPP)** con UUID propio (`7f3c2a10-5b8e-4d21-9c6f-2e8a4b1d9f03`) y alternativa **Wi-Fi local (Sockets TCP en puerto 5050)**. Protocolo de texto plano estructurado: `TIPO;CLAVE;VALOR`. | Alcance < 10 m en entorno perimetral; no depende de la infraestructura de red del campus; el emparejamiento previo entrega un canal autenticado y cifrado a nivel de enlace de radio. |
| **4. Login y Datos** | **Firebase Authentication** (Gestión de sesiones cifradas TLS 1.3) + **Cloud Firestore** (Colecciones `/usuarios` con roles RBAC y `/accesos_log` para historial de eventos). | Sincronización en tiempo real entre múltiples terminales, seguridad de credenciales delegada en proveedor cloud certificado y cumplimiento de interoperabilidad IoT en la nube. |
| **5. Notificaciones** | Locales mediante **`NotificationCompat`**, canal de alta prioridad `alertas_pestillo`, permiso en runtime `POST_NOTIFICATIONS` (Android 13+). | Sin dependencia de servidores push externos para la operativa de red local; despacho en tiempo real ante apertura de puerta, proximidad y desconexión. |
| **6. Seguridad e Industria OT** | **Cifrado AES-256-GCM** en tramas con derivación de clave desde código de vinculación de 6 dígitos. Control de acceso por roles (**Operador** vs **Observador**). Permisos mínimos (`BLUETOOTH_CONNECT`, `INTERNET`). `allowBackup="false"`, `usesCleartextTraffic="false"`. **Estado seguro OT:** retorno forzado a `LOCKED (0°)` al perder enlace. | Cumplimiento estricto de **ISO/IEC 27400:2022** (privacidad, autenticación, integridad y confidencialidad) e **IEC 62443** (diseño *Fail-Secure* y principio de mínimo privilegio). |
| **7. Placas Evaluación 1** | **2 × ESP32 DevKit v4** (Nodo 1 Sensor HC-SR04 y Nodo 2 Actuador Servo SG90 con ESP-NOW). La placa actuador expone `BluetoothSerial` ("Pestillo-IoT") / `WiFiServer:5050` con el protocolo `LECTURA;dist;...`, `CMD;latch;OPEN` y `ACK;latch;OPEN`. | Requisito M12 para validar la interoperabilidad directa entre la app Android y el hardware embebido original. |
| **Restricciones** | Trabajo estrictamente individual. Cero librerías externas no justificadas. Código limpio, comentado y defendible en demostración presencial. |

---

## 3. Arquitectura del Sistema

### 3.1 Paquetes y Estructura de Archivos
```
app/src/main/java/cl/inacap/pestilloiot/
├── MainActivity.kt               ← Punto de entrada, ciclo de vida, permisos runtime y navegación
├── Sesion.kt                     ← StateFlow singleton: cerebro de la app, router de mensajes y failsafe OT
├── Notificaciones.kt             ← Creación de canales e invocación de NotificationCompat
├── enlace/
│   ├── Enlace.kt                 ← Data class Mensaje, interface Enlace y clase abstracta EnlaceBase
│   ├── BluetoothEnlace.kt        ← Implementación RFCOMM Bluetooth clásico (Servidor / Cliente)
│   └── WifiEnlace.kt             ← Implementación Sockets TCP en puerto 5050
├── seguridad/
│   └── Cripto.kt                 ← Derivación de claves (PBKDF2) y cifrado/descifrado AES-256-GCM
├── datos/
│   └── FirestoreService.kt       ← Gestor Firestore: autenticación, guardado y streams de historial
└── ui/
    ├── PantallaLogin.kt          ← Login y registro Firebase con validación de roles y contraseña
    ├── PantallaConexion.kt       ← Configuración de rol (Nodo/Panel), medio (BT/Wi-Fi) y código
    ├── PantallaNodo.kt           ← Vista del Nodo IoT: sensor ultrasónico simulado y estado de pasador
    ├── PantallaPanel.kt          ← Vista del Panel Operador: botón de apertura remota y estado en vivo
    └── PantallaHistorial.kt      ← Vista de auditoría: listado en tiempo real de accesos con timestamp
```

### 3.2 Protocolo de Mensajes
Todas las tramas son strings delimitados por salto de línea `\n`, cifrados en Base64(IV + Ciphertext + Tag) bajo AES-GCM:
- `LECTURA;dist;4.2` : Telemetría del sensor ultrasónico exterior (distancia física en cm).
- `LECTURA;latch;LOCKED` / `LECTURA;latch;UNLOCKED` : Estado de tracción del pasador.
- `CMD;latch;OPEN` : Orden del panel para retraer el pestillo (90°) durante 3000 ms.
- `CMD;latch;LOCK` : Orden del panel para forzar bloqueo inmediato (0°).
- `ACK;latch;OPEN` / `ACK;latch;LOCKED` : Confirmación física emitida por el actuador.

---

## 4. Fases de Desarrollo del MVP

### Fase 0 — Preparación del Entorno y Línea Base (M1, M2 · Criterio 2.1.1)
- [x] **F0.1** Inicializar proyecto Android Studio en Kotlin DSL (`cl.inacap.pestilloiot`) con `minSdk 26` y Compose Material 3.  
  *Criterio de Aceptación:* El proyecto compila limpiamente y muestra pantalla base en teléfono físico.
- [x] **F0.2** Configurar `AndroidManifest.xml` con permisos estrictos (`BLUETOOTH_CONNECT`, `INTERNET`, `POST_NOTIFICATIONS`) y flags de seguridad `allowBackup="false"`, `usesCleartextTraffic="false"`.  
  *Criterio de Aceptación:* Android Studio sincroniza Gradle sin advertencias de permisos redundantes.
- [x] **F0.3** Integrar dependencias oficiales de Firebase BoM (`firebase-auth-ktx`, `firebase-firestore-ktx`) y asociar `google-services.json`.  
  *Criterio de Aceptación:* `google-services.json` verificado en `app/` y conexión inicial con Firebase SDK exitosa.

---

### Fase 1 — POC-1: Enlace Inalámbrico y Motor Criptográfico (M4, M10 · Criterio 2.1.2, 2.1.3)
- [x] **F1.1** Implementar `seguridad/Cripto.kt` con derivación PBKDF2 (`claveDesdeCodigo`) y cifrado/descifrado simétrico AES-256-GCM.  
  *Criterio de Aceptación:* Test unitario valida que un string cifrado con código `123456` se descifra idéntico y que con código erróneo retorna `null`.
- [x] **F1.2** Crear `enlace/Enlace.kt` con parser estricto `Mensaje.desdeLinea()` y clase base asíncrona `EnlaceBase` en `Dispatchers.IO`.  
  *Criterio de Aceptación:* Mensajes con formato inválido o campos mayores a 16 caracteres son descartados sin excepciones.
- [x] **F1.3** Implementar `enlace/BluetoothEnlace.kt` (servidor `listenUsingRfcommWithServiceRecord` y cliente `createRfcommSocketToServiceRecord`).  
  *Criterio de Aceptación:* Teléfono A (Nodo) y Teléfono B (Panel) se emparejan y establecen conexión RFCOMM en menos de 5 segundos.
- [x] **F1.4** Implementar `enlace/WifiEnlace.kt` mediante `ServerSocket(5050)` y `Socket.connect()` con timeout de 5000 ms.  
  *Criterio de Aceptación:* Ambos teléfonos intercambian tramas por IP local (o Hotspot) mostrando estado "Conectado".

---

### Fase 2 — Monitoreo, Control y Estado Seguro OT (M5, M6, M11 · Criterio 2.1.2, 2.1.4)
- [x] **F2.1** Implementar `Sesion.kt` gestionando el flujo de estados reactivos (`estado`, `distancia`, `latchState`) en `StateFlow`.  
  *Criterio de Aceptación:* El estado persiste y no se reinicia ante cambios de configuración o rotación de pantalla.
- [x] **F2.2** Desarrollar `ui/PantallaNodo.kt` con emulador de sensor ultrasónico (botón "Detectar Mano < 8cm" y slider 2-30cm) y visualizador de estado del servomotor (0° Bloqueado / 90° Abierto).  
  *Criterio de Aceptación:* Al pulsar detección manual, el nodo despacha `LECTURA;dist;...` y simula la retracción local.
- [x] **F2.3** Desarrollar `ui/PantallaPanel.kt` con indicador de estado en vivo y botón de acción "ABRIR PESTILLO".  
  *Criterio de Aceptación:* Al presionar "ABRIR PESTILLO", el panel emite `CMD;latch;OPEN`, el nodo acciona el actuador y devuelve `ACK;latch;OPEN` reflejado en el panel en < 2 segundos.
- [x] **F2.4** Implementar el lineamiento de seguridad industrial OT (*Fail-Secure*): si `enlace.estado` pasa a "Desconectado", el nodo fuerza inmediatamente `latchState = LOCKED` (0°).  
  *Criterio de Aceptación:* Al apagar el Bluetooth del panel con el pestillo abierto, el nodo vuelve instantáneamente a estado BLOQUEADO.

---

### Fase 3 — Sistema de Notificaciones de Alta Prioridad (M7 · Criterio 2.1.4)
- [x] **F3.1** Implementar `Notificaciones.kt` creando el canal `alertas_pestillo` con `IMPORTANCE_HIGH` y verificación de permiso `POST_NOTIFICATIONS`.  
  *Criterio de Aceptación:* En Android 13+, la app solicita el permiso en el primer arranque y no se bloquea si es denegado.
- [x] **F3.2** Conectar disparadores de notificación en `Sesion.kt`:
  - En el Panel: notificación inmediata cuando la puerta es desbloqueada (`"¡Acceso Concedido / Pestillo Abierto!"`).
  - En el Nodo: notificación al recibir orden remota (`"Comando de apertura recibido"`).
  - En ambos: notificación crítica ante corte inesperado (`"Alerta: Enlace Perdido con el Perímetro"`).  
  *Criterio de Aceptación:* Al comandar apertura remota, ambos teléfonos emiten la notificación correspondiente en la barra de estado.

---

### Fase 4 — Autenticación y Control de Acceso RBAC en Firestore (M8 · Criterio 2.1.2, 2.1.3)
- [x] **F4.1** Implementar `datos/FirestoreService.kt` con métodos de registro, login (`FirebaseAuth`) y mapeo de roles en `/usuarios/{uid}` (primer usuario = `operador`, siguientes = `observador`).  
  *Criterio de Aceptación:* Registro exitoso crea credencial en Firebase Auth y documento de perfil tipado con su rol en Firestore.
- [x] **F4.2** Desarrollar `ui/PantallaLogin.kt` con validación de formato (contraseña mínimo 8 caracteres), bloqueo temporal de 30 segundos tras 5 intentos fallidos y feedback visual de errores.  
  *Criterio de Aceptación:* Usuario incorrecto 5 veces consecutivas deshabilita el formulario durante 30 segundos exactos.
- [x] **F4.3** Aplicar Control de Acceso Basado en Roles (RBAC) en `PantallaPanel.kt`: si el rol es `observador`, el botón de apertura remota se deshabilita visualmente mostrando leyenda informativa.  
  *Criterio de Aceptación:* Un usuario logueado como observador puede ver telemetría pero no puede presionar el botón de apertura.

---

### Fase 5 — Persistencia Cloud y Consulta Histórica (M9 · Criterio 2.1.2)
- [x] **F5.1** Implementar en `FirestoreService.kt` el guardado automático de eventos en `/accesos_log` (`timestamp`, `tipo_evento`, `usuario_id`, `rol`, `distancia_cm`, `estado_latch`).  
  *Criterio de Aceptación:* Cada apertura local o remota inserta un nuevo documento en la colección de Firestore en < 1 segundo.
- [x] **F5.2** Desarrollar `ui/PantallaHistorial.kt` utilizando `LazyColumn` que consume un stream reactivo (`snapshotListener`) de los últimos 50 registros ordenados descendentemente.  
  *Criterio de Aceptación:* La pantalla de historial renderiza la lista con fecha/hora formateada (`dd-MM HH:mm:ss`), evento y autor, actualizándose en caliente.

---

### Fase 6 — Auditoría de Seguridad ISO/IEC 27400 y Preentrega (M3, M10, M13 · Criterio 2.1.1, 2.1.3)
- [x] **F6.1** Generar mockup técnico formal de la UI en `docs/mockup.png` reflejando el flujo: Login ➔ Conexión ➔ Nodo / Panel ➔ Historial.  
  *Criterio de Aceptación:* Imagen de alta resolución añadida al repositorio y referenciada en `README.md`.
- [x] **F6.2** Ejecutar batería completa de pruebas P1 a P14 documentando resultados y capturas en `docs/evidencias/`.  
  *Criterio de Aceptación:* Todas las pruebas P1–P14 aprobadas y consolidadas en tabla Markdown.
- [x] **F6.3** Estructurar `README.md` final con problemática, arquitectura, declaración técnica, matriz ISO 27400 y declaración de uso de IA.  
  *Criterio de Aceptación:* Archivo `.txt` con link a GitHub preparado para entrega en plataforma AAI.

---

### Fase 7 — Testing e Interoperabilidad con Placas ESP32 de Evaluación 1 (M12 · Criterio 2.1.4)
- [x] **F7.1 (T0 Línea Base):** Verificar la compilación y ejecución original del sketch `PestilloInteligente_ES1.ino` (Sensor HC-SR04 ➔ ESP-NOW ➔ Actuador SG90).  
  *Criterio de Aceptación:* El servo físico/simulado reacciona al detectar proximidad física (< 8 cm).
- [x] **F7.2 (T1/T2 Adaptación Gateway):** Modificar el firmware del Nodo Actuador para inicializar `BluetoothSerial` ("Pestillo-IoT" con UUID SPP `00001101-0000-1000-8000-00805F9B34FB`) o servidor TCP en puerto 5050, manteniendo ESP-NOW activo en paralelo.  
  *Criterio de Aceptación:* La app Android se conecta vía Bluetooth/Wi-Fi directamente a la placa ESP32.
- [x] **F7.3 (T3/T4 Ejecución Casos PL1–PL7):** Ejecutar pruebas de integración física/emulada (lectura real de sensor ultrasónico, apertura remota del servo, notificación en teléfono y estado seguro ante desconexión).  
  *Criterio de Aceptación:* Casos PL1 a PL7 superados al 100% y documentados con registros en el informe.

---

## 5. Plan de Pruebas

### 5.1 Pruebas de Software entre Teléfonos (P1 a P14)

| ID | Caso de Prueba | Pasos de Ejecución | Resultado Esperado |
| :--- | :--- | :--- | :--- |
| **P1** | **Conexión Inalámbrica** | Teléfono A inicia como Nodo; Teléfono B como Panel con mismo código de 6 dígitos. | Ambos terminales transicionan a estado "Conectado" en < 5 s. |
| **P2** | **Monitoreo de Sensor** | Modificar distancia ultrasónica en el Nodo a 5.0 cm. | El Panel actualiza la lectura a `5.0 cm` en 2 segundos o menos. |
| **P3** | **Notificación de Alerta** | Provocar proximidad (< 8 cm) en el Nodo. | El Panel emite notificación de alta prioridad `"¡Acceso Concedido / Pestillo Abierto!"`. |
| **P4** | **Control Remoto** | Presionar "ABRIR PESTILLO" en el Panel de Control. | El Nodo muestra "ABIERTO (90°)", emite notificación y el Panel refleja `ACK;latch;OPEN`. |
| **P5** | **Aislamiento Criptográfico** | Configurar Nodo con código `111111` y Panel con `222222`. | Se establece conexión de socket pero los paquetes no se descifran (se descartan en silencio). |
| **P6** | **Failsafe OT por Desconexión** | Desactivar Bluetooth en el Panel mientras el pestillo está abierto. | Ambos teléfonos avisan "Enlace perdido"; el Nodo conmuta forzosamente a `LOCKED (0°)`. |
| **P7** | **Denegación de Permiso** | Rechazar permiso `BLUETOOTH_CONNECT` en el inicio. | La app muestra mensaje explicativo sin cerrarse inesperadamente (*No crash*). |
| **P8** | **Registro y Roles RBAC** | Registrar usuario 1 (`admin@inacap.cl`) y usuario 2 (`guardia@inacap.cl`). | Usuario 1 obtiene rol `operador`; Usuario 2 obtiene rol `observador` en Firestore. |
| **P9** | **Bloqueo Anti-Fuerza Bruta** | Ingresar contraseña errónea 5 veces consecutivas. | El sistema bloquea el formulario durante 30 segundos mostrando aviso de seguridad. |
| **P10** | **Validación de Credenciales** | Intentar registrar contraseña de 5 caracteres. | Formulario rechaza el envío exigiendo longitud mínima de 8 caracteres. |
| **P11** | **Mínimo Privilegio (Observador)** | Iniciar sesión con cuenta de rol `observador` y entrar al Panel. | Visualiza telemetría en tiempo real; botón "ABRIR PESTILLO" bloqueado con aviso explicativo. |
| **P12** | **Persistencia Cloud** | Ejecutar 5 aperturas de pestillo y abrir Pantalla de Historial. | Se visualizan los 5 registros con timestamp exacto y usuario responsable desde Firestore. |
| **P13** | **Persistencia de Sesión** | Cerrar la aplicación por completo y reabrirla. | El historial y la sesión de usuario se recuperan íntegros sin pérdida de datos. |
| **P14** | **Auditoría de Criptografía** | Inspeccionar Firestore Database y tráfico de red. | No existen contraseñas en texto claro; tráfico encapsulado bajo TLS 1.3 / AES-256-GCM. |

---

### 5.2 Pruebas de Integración con Hardware ESP32 (PL1 a PL7)

| ID | Caso de Prueba | Pasos de Ejecución | Resultado Esperado |
| :--- | :--- | :--- | :--- |
| **PL1** | **Lectura Física de Proximidad** | Acercar la mano a 4 cm del sensor HC-SR04 físico/emulado. | El Panel Android refleja `4.0 cm` sincronizado con el monitor serie del ESP32. |
| **PL2** | **Accionamiento de Servomotor** | Enviar comando "ABRIR PESTILLO" desde el Panel Android. | El servomotor SG90 gira físicamente a 90° y retorna `ACK;latch;OPEN` a la app. |
| **PL3** | **Notificación por Evento Físico** | Disparar detección de proximidad en el sensor de la placa exterior. | El teléfono Panel recibe notificación local inmediata de apertura. |
| **PL4** | **Estado Seguro en Placa** | Con el servomotor en 90°, cortar la conexión Bluetooth del teléfono. | El firmware del ESP32 fuerza el servomotor a 0° (bloqueado) inmediatamente. |
| **PL5** | **Sanitización de Tramas en Placa** | Inyectar cadena de texto inválida `MALICIOUS_PAYLOAD` al puerto serie. | El parser del ESP32 descarta la trama sin alterar la máquina de estados. |
| **PL6** | **Medición de Latencia Real** | Medir tiempo entre disparo ultrasónico y renderizado en app. | Latencia total de enlace inferior a 150 ms. |
| **PL7** | **Regresión ESP-NOW Original** | Probar comunicación directa entre Nodo 1 y Nodo 2 sin teléfono. | El enlace ESP-NOW original de Evaluación 1 opera al 100% en paralelo. |

---

## 6. Matriz de Trazabilidad (Must ➔ Fases ➔ Pruebas)

| Requisito Must (MVP) | Descripción del Requisito | Fase de Desarrollo | Casos de Prueba Verificadores |
| :---: | :--- | :---: | :---: |
| **M1** | App Android en Kotlin con Jetpack Compose en dos teléfonos | Fase 0 | P1 |
| **M2** | Problemática, funcionalidades y herramientas justificadas | Fase 0, 6 | Revisión Docente / README |
| **M3** | Mockup de pantallas, navegación y control de acceso | Fase 6 | Mockup `docs/mockup.png` |
| **M4** | Conexión inalámbrica justificada (Bluetooth / Wi-Fi) | Fase 1 | P1, P6, P7 |
| **M5** | Monitoreo en vivo de telemetría de sensor (Nodo ➔ Panel) | Fase 2 | P2, PL1 |
| **M6** | Control remoto de actuador de pestillo con confirmación ACK | Fase 2 | P4, PL2 |
| **M7** | Notificación local de alta prioridad en terminal destino | Fase 3 | P3, P4, PL3 |
| **M8** | Login y gestión de roles RBAC con Firebase Authentication | Fase 4 | P8, P9, P10, P11 |
| **M9** | Almacenamiento y consulta de historial en Cloud Firestore | Fase 5 | P12, P13 |
| **M10** | Seguridad ISO/IEC 27400: AES-256-GCM, TLS y permisos mínimos | Fase 1, 4, 6 | P5, P14 |
| **M11** | Estado seguro industrial OT (*Fail-Secure* a 0° ante corte) | Fase 2 | P6, PL4 |
| **M12** | Pruebas de interoperabilidad con placas ESP32 de Evaluación 1 | Fase 7 | PL1, PL2, PL3, PL4, PL5, PL6, PL7 |
| **M13** | Repositorio GitHub con README, PLAN_MVP y Declaración IA | Fase 6 | Auditoría Git / Archivo `.txt` |
| **M14** | Demostración en clase de login, conexión, datos y seguridad | Fase 6, 7 | Demostración Presencial 5-7 min |

---

## 7. Gestión de Riesgos y Pruebas de Concepto (POC)

| Identificador | Pregunta Crítica de la POC | Plazo de Validación | Plan de Contingencia ante Fallo |
| :--- | :--- | :---: | :--- |
| **POC-1** | ¿Se establece el socket RFCOMM Bluetooth y Wi-Fi entre ambos dispositivos con cifrado AES-256-GCM? | 24 horas | Conmutar a Wi-Fi TCP local mediante Hotspot dedicado en puerto 5050. |
| **POC-2** | ¿El ESP32 Actuador puede atender simultáneamente la interrupción ESP-NOW y el socket BluetoothSerial? | 24 horas | Delegar la conexión de la app exclusivamente al ESP32 Actuador configurando buffers de recepción en FreeRTOS. |
| **POC-3** | ¿Se sincronizan las credenciales y el historial de eventos en Cloud Firestore en tiempo real sobre red móvil/Wi-Fi? | 24 horas | Implementar capa de persistencia offline nativa de Firestore (`persistentCacheSettings`). |

---

## 8. Fuera de Alcance (Won't Have)

Las siguientes funcionalidades quedan formalmente excluidas del MVP para garantizar el cumplimiento estricto del plazo y la pauta:
- ❌ Publicación en Google Play Store o distribución comercial.
- ❌ Versión multiplataforma para Apple iOS.
- ❌ Conexión mediante brokers MQTT públicos en la nube para esta entrega (postergado para Unidad 4).
- ❌ Ejecución de servicios en segundo plano persistentes (*Foreground Services*) con la aplicación destruida por el sistema operativo.
- ❌ Integración de pasarelas de pago o autenticación con proveedores externos de terceros (OAuth Facebook / Twitter).
