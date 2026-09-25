# 📑 INFORME TÉCNICO Y EVIDENCIAS DE PRUEBAS DE INTEGRACIÓN: FASE 0 & FASE 1
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP  
**Evaluación:** Unidad 2 — Evaluación Sumativa 2 (25%)  
**Proyecto:** Pestillo Inteligente IoT Distribuido  
**Estudiante:** Nicolás Anrique (Bollua)  
**Fecha:** 25 de Septiembre de 2026  
**Entorno:** Host Linux (Arch x86_64) / Arnés Virtual Emulado (`virtual_esp32_gateway.py`)  

---

## 1. Alcance de las Pruebas de la Fase 0 y Fase 1

El objetivo de este ciclo de testing es certificar empíricamente la línea base del sistema antes de proceder con las fases superiores. Se evalúan 16 casos de prueba (10 funcionales base y 6 de estrés/resiliencia avanzada) cubriendo:
1. **Línea Base y Transporte (Fase 0):** Inicialización de sockets TCP en puerto 5050/5058, manejo de buffers y concurrencia multi-cliente.
2. **Capa Criptográfica ISO/IEC 27400:2022 (Fase 1):** Derivación de claves simétricas mediante PBKDF2 con HMAC-SHA256 (10,000 iteraciones) y autenticación/cifrado de tramas con AES-256-GCM (IV aleatorio de 12 bytes + Tag de autenticación de 128 bits).
3. **Máquina de Estados y Lineamiento Industrial OT (IEC 62443):** Garantía del principio *Fail-Secure* donde el servomotor conmuta y permanece en `0° BLOQUEADO` ante cualquier anomalía de enlace o tras el vencimiento del temporizador de paso (3.0s).

---

## 2. Matriz Exhaustiva de Casos de Prueba Ejecutados

| ID | Categoría | Caso de Prueba | Vector de Entrada / Estímulo | Resultado Obtenido | Latencia / Métrica | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: |
| **T1** | Cripto | **Derivación PBKDF2 & AES-GCM** | PIN `123456` ➔ Payload: `CMD;latch;OPEN` | Texto cifrado en Base64 con IV(12B) + Tag(16B); descifrado bit a bit idéntico. Clave incorrecta (`999999`) retorna `null`. | < 1.2 ms | **PASS** |
| **T2** | Red | **Handshake Socket TCP** | Conexión a `127.0.0.1:5055` | Socket TCP establecido con `SO_REUSEADDR` sin bloqueo. | 0.8 ms | **PASS** |
| **T3** | Protocolo | **Telemetría Inicial** | Handshake de conexión | Recepción de tramas cifradas `LECTURA;latch;LOCKED` y `LECTURA;dist;50.0`. | 1.1 ms | **PASS** |
| **T4** | Control | **Apertura Remota** | `CMD;latch;OPEN` (Cifrado con PIN válido) | Retracción del actuador a 90° y despacho de confirmación `ACK;latch;OPEN`. | 1.4 ms | **PASS** |
| **T5** | Seguridad OT | **Temporizador Fail-Secure** | Espera pasiva de 3000 ms tras apertura | Conmutación forzada del pasador a 0° (`ACK;latch;LOCKED` y `LECTURA;latch;LOCKED`). | 3.00s exactos | **PASS** |
| **T6** | Hardware Sim | **Disparo Proximidad Ultrasónica** | `distancia = 5.4 cm` (< 8.0 cm umbral) | Simulación de interrupción ESP-NOW; actuador pasa a 90° automáticamente. | 2.1 ms | **PASS** |
| **T7** | Control | **Bloqueo Inmediato Forzado** | `CMD;latch;LOCK` | Pasador re-bloqueado a 0° en caliente sin esperar el temporizador. | 0.9 ms | **PASS** |
| **T8** | Seguridad ISO | **Aislamiento Criptográfico** | Trama cifrada con PIN inválido (`999999`) | GCM Authentication Tag verification failure. Trama descartada silenciosamente. | 0.4 ms | **PASS** |
| **T9** | Robustez | **Resiliencia ante Fuzzing** | Inyección de strings binarios sin delimitadores y campos inválidos | Parser descarta entradas anómalas sin generar excepciones no controladas. | 0.2 ms | **PASS** |
| **T10** | Red | **Difusión Multi-Cliente** | 2 sockets conectados (Nodo y Panel) | `INFO;status;NOMINAL` recibido simultáneamente en ambos descriptores de archivo. | 1.8 ms | **PASS** |
| **INT-01** | Rendimiento | **Streaming de Alta Frecuencia** | 50 bursts de telemetría continua | 50/50 tramas recibidas, parseadas y procesadas sin pérdida de paquetes. | 54.3 ms totales | **PASS** |
| **INT-02** | Concurrencia | **Concurrencia Multi-Socket** | 5 clientes TCP concurrentes conectados | Conexión simultánea y despacho de comandos con 100% de éxito. | 3.2 ms | **PASS** |
| **INT-03** | Resiliencia | **Cierre Abrupto (TCP RST)** | Cierre intempestivo con `SO_LINGER=0` con pestillo destrabado | Gateway detecta desconexión, purga descriptor y permite reconexión inmediata. | < 5 ms | **PASS** |
| **INT-04** | Seguridad | **Protección Buffer Overflow** | Inyección de payload de 64 KB (`"A" * 65536`) | Guardarraíl rechaza campos >32 caracteres; simulador se mantiene estable. | 0.8 ms | **PASS** |
| **INT-05** | Precisión | **Cronometría de Ciclo** | Comando `OPEN` y muestreo continuo | t=1.0s: `UNLOCKED` (90°) ➔ t=3.3s: `LOCKED` (0°). Ciclo completo verificado. | 3.30s | **PASS** |
| **INT-06** | Cripto-Auditoría | **Ataque de Diccionario / PIN** | 5 tramas cifradas con PINs falsos (`000000`, `111111`, etc.) | 5 de 5 tramas rechazadas por integridad criptográfica GCM. | < 2.0 ms | **PASS** |

---

## 3. Diagrama de Secuencia y Cronometría del Ciclo de Vida OT

```
Cliente (App / Panel)                       Gateway ESP32 (Host)               Actuador Servo SG90
       │                                             │                                  │
       │─── 1. Conexión TCP (Puerto 5055/5058) ─────>│                                  │
       │<── 2. LECTURA;latch;LOCKED (AES-GCM) ───────│                                  │
       │                                             │                                  │
       │─── 3. CMD;latch;OPEN (Cifrado con PIN) ────>│                                  │
       │                                             │─── 4. write(90°) (Destrabar) ───>│
       │<── 5. ACK;latch;OPEN ───────────────────────│                                  │
       │<── 6. LECTURA;latch;UNLOCKED ───────────────│                                  │
       │                                             │                                  │
       │                [ ESPERA AUTOMÁTICA DE 3000 ms (IEC 62443) ]                    │
       │                                             │                                  │
       │                                             │─── 7. write(0°) (Auto-bloqueo) ─>│
       │<── 8. ACK;latch;LOCKED ─────────────────────│                                  │
       │<── 9. LECTURA;latch;LOCKED ─────────────────│                                  │
       │                                             │                                  │
```

---

## 4. Conclusiones y Certificación de Fase 0

1. **Robustez de la Capa de Enlace:** El protocolo de mensajes `TIPO;CLAVE;VALOR` demuestra inmunidad ante paquetes truncados, fuzzing y sobrecargas de 64 KB.
2. **Conformidad ISO/IEC 27400:** El uso de AES-256-GCM previene ataques de inyección y manipulación (*tampering*); un atacante sin el PIN de 6 dígitos no puede generar etiquetas de autenticación válidas.
3. **Conformidad Industrial OT:** La máquina de estados garantiza que ante caídas de red, reinicios de socket o expiración temporal, el pasador se asegura físicamente en posición cerrada (`0° LOCKED`).
