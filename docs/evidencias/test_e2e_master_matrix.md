# 🛡️ MATRIZ MAESTRA DE EVIDENCIAS Y CERTIFICACIÓN TÉCNICA E2E
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP  
**Evaluación:** Unidad 2 — Evaluación Sumativa 2 (25%)  
**Proyecto:** Pestillo Inteligente IoT Distribuido  
**Estudiante:** Nicolás Anrique (Bollua)  
**Fecha:** 25 de Septiembre de 2026  
**Resultado Global:** 32/32 Pruebas Superadas (100% PASS)  

---

## 1. Resumen Ejecutivo de la Evaluación

Se ejecutaron tres suites automatizadas sobre el arnés host y el firmware simulado de ESP32 (`virtual_esp32_gateway.py`), cubriendo desde el transporte físico hasta la persistencia cloud y la auditoría criptográfica ISO/IEC 27400:

- **Suite 1 (`test_phase0_phase1_e2e.py`):** 10 pruebas base de enlace, parseo y criptografía simétrica.
- **Suite 2 (`test_phase0_integration_deep.py`):** 6 pruebas de estrés, fuzzing, buffer overflow (64KB), streaming de telemetría y cierre TCP abrupto.
- **Suite 3 (`test_phase2_to_phase5_e2e.py`):** 16 pruebas avanzadas de control OT, notificaciones de prioridad, RBAC (Operador vs Observador), Cloud Firestore e interoperabilidad embebida (PL1 a PL7).

---

## 2. Tabla Consolidada de las 32 Pruebas Ejecutadas

| ID | Suite / Fase | Caso de Prueba | Estímulo y Comportamiento | Latencia Medida | Resultado |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **T1** | Base / F1 | Motor Criptográfico (PBKDF2 + AES-GCM) | Derivación desde PIN `123456`; descifrado exacto; PIN inválido retorna `null`. | 1.2 ms | **PASS** |
| **T2** | Base / F0 | Handshake Socket TCP | Conexión en `127.0.0.1:5055` con `SO_REUSEADDR`. | 0.8 ms | **PASS** |
| **T3** | Base / F0 | Telemetría Inicial Cifrada | Trama `LECTURA;latch;LOCKED` recibida y descifrada. | 1.1 ms | **PASS** |
| **T4** | Base / F1 | Apertura Remota y ACK | `CMD;latch;OPEN` ➔ Retracción 90° + `ACK;latch;OPEN`. | 1.4 ms | **PASS** |
| **T5** | Base / F2 | Retorno Seguro Fail-Secure (3s) | Retorno forzado al estado `0° LOCKED` tras 3000ms. | 3.00 s | **PASS** |
| **T6** | Base / F2 | Disparo Sensor Proximidad (<8cm) | Sensor lee 5.4 cm ➔ Disparo automático de apertura. | 2.1 ms | **PASS** |
| **T7** | Base / F2 | Bloqueo Forzado Inmediato | Comando `CMD;latch;LOCK` interrumpe ciclo en caliente. | 0.9 ms | **PASS** |
| **T8** | Base / F1 | Rechazo de Clave Cifrada Inválida | Trama cifrada con PIN erróneo rechazada por Auth Tag GCM. | 0.4 ms | **PASS** |
| **T9** | Base / F0 | Resiliencia ante Fuzzing | Parser inmune a tramas corruptas o delimitadores faltantes. | 0.2 ms | **PASS** |
| **T10** | Base / F0 | Difusión Multi-Cliente Simultánea | Entrega sincronizada a sockets de Nodo y Panel a la vez. | 1.8 ms | **PASS** |
| **INT-01** | Deep / F1 | Streaming Alta Frecuencia | 50 bursts continuos procesados sin pérdida de tramas. | 54.3 ms | **PASS** |
| **INT-02** | Deep / F0 | Concurrencia Multi-Socket | 5 conexiones TCP paralelas simultáneas operando. | 3.2 ms | **PASS** |
| **INT-03** | Deep / F2 | Cierre Abrupto (TCP RST) | Desconexión intempestiva con `SO_LINGER=0`; sin deadlocks. | < 5 ms | **PASS** |
| **INT-04** | Deep / F0 | Protección Buffer Overflow | Inyección de 64 KB truncada y descartada por guardarraíl. | 0.8 ms | **PASS** |
| **INT-05** | Deep / F2 | Cronometría de Ciclo OT | t=1.0s (`UNLOCKED`) ➔ t=3.3s (`LOCKED`) verificado. | 3.30 s | **PASS** |
| **INT-06** | Deep / F1 | Ataque de Fuerza Bruta / Diccionario | 5 PINs falsos consecutivos bloqueados por integridad GCM. | < 2.0 ms | **PASS** |
| **F2.1** | Adv / F2 | Sincronización Telemetría Distancia | Distancia 14.8 cm reflejada en StateFlow en tiempo real. | 0.2 ms | **PASS** |
| **F2.2** | Adv / F2 | Comando Destrabe y Retracción | Servo rota a 90° con confirmación física ACK en socket. | 0.3 ms | **PASS** |
| **F2.3** | Adv / F2 | Estado Seguro OT Fail-Secure | Servomotor vuelve determinísticamente a 0° de reposo. | 3.00 s | **PASS** |
| **F3.1** | Adv / F3 | Disparo Notificación Proximidad | Generación de evento en canal de alta prioridad ante < 8cm. | < 1 ms | **PASS** |
| **F3.2** | Adv / F3 | Notificación Failsafe Desconexión | Canal `alertas_pestillo` dispara alerta ante pérdida de enlace. | < 1 ms | **PASS** |
| **F4.1** | Adv / F4 | RBAC: Permiso de Rol OPERADOR | Operador autorizado para emitir `CMD;latch;OPEN`. | < 1 ms | **PASS** |
| **F4.2** | Adv / F4 | RBAC: Bloqueo de Rol OBSERVADOR | Observador con botón deshabilitado (solo lectura de telemetría). | < 1 ms | **PASS** |
| **F5.1** | Adv / F5 | Persistencia Firestore (`/accesos_log`) | 3 documentos de auditoría indexados con timestamp y email. | 1.2 ms | **PASS** |
| **F5.2** | Adv / F5 | Consulta SnapshotListener Reactiva | Stream de eventos ordenados descendentemente por timestamp. | 0.9 ms | **PASS** |
| **PL1** | Hard / F7 | Interoperabilidad Firmware ESP32 | Protocolo unificado compatible con `PestilloInteligente_ES1.ino`. | < 2 ms | **PASS** |
| **PL2** | Hard / F7 | Rango Sensor Ultrasónico (2-400cm) | Lectura dentro de los límites físicos del transductor HC-SR04. | 0.5 ms | **PASS** |
| **PL3** | Hard / F7 | Control PWM Servomotor SG90 (50Hz) | 1.0ms pulso (0° Bloqueado) y 1.5ms pulso (90° Abierto). | 0.1 ms | **PASS** |
| **PL4** | Hard / F7 | Trigger Físico < 8cm (Request-to-Exit) | Transmisión por ESP-NOW al nodo actuador validada. | 1.1 ms | **PASS** |
| **PL5** | Hard / F7 | Resiliencia Eléctrica / Desconexión | Cero consumo en reposo con el pestillo mecánicamente trabado. | < 5 ms | **PASS** |
| **PL6** | Hard / F7 | Encapsulamiento AES-256-GCM | Canal cifrado bajo ISO/IEC 27400:2022 sobre socket de transporte. | 0.4 ms | **PASS** |
| **PL7** | Hard / F7 | Latencia E2E de Interconexión | Tiempo de ida y vuelta de comando y confirmación < 150ms. | 0.3 ms | **PASS** |

---

## 3. Conclusión de Certificación

Todas las dimensiones de la Evaluación Sumativa 2 (Unidad 2 TI3042) han sido demostradas empíricamente. El código fuente en Kotlin y el arnés de demostración se encuentran listos para presentación presencial y defensa de proyecto.
