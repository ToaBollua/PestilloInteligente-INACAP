# 🔌 GUÍA DE ENSAMBLADO Y DESPLIEGUE EN UN SOLO ESP32 (HARDWARE FÍSICO)
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP  
**Evaluación:** Unidad 2 / Prototipo Físico Funcional  
**Autor:** Nicolás Anrique, Camilo Núñez, Diego Ibeas & H0P3 OT-Kernel  

---

## 📌 1. Visión General del Prototipo
Este módulo integra **sensórica de proximidad**, **actuación electromecánica de cerrojo** y **doble pasarela inalámbrica (Bluetooth Serial SPP + Wi-Fi TCP)** dentro de **un único microcontrolador ESP32 DevKit v1**.

Permite validar tanto en el simulador Wokwi como en hardware físico real:
1. **Acceso por Proximidad (Request-to-Exit):** HC-SR04 detecta presencia `< 8.0 cm`, destrabando el pestillo inmediatamente.
2. **Acceso Remoto por Bluetooth SPP:** La aplicación Android se empareja directamente con `PestilloIoT` y envía órdenes `CMD;latch;OPEN`.
3. **Acceso Remoto por Wi-Fi TCP (Puerto 5050):** El ESP32 crea su propia red Soft-AP (`Pestillo-IoT-Nodo`) o se conecta al router local, atendiendo peticiones TCP.
4. **Norma de Seguridad OT / Fail-Secure:** Tras 3000 ms (3 segundos), el pestillo retorna automáticamente a su posición de bloqueo (`0°`).

---

## 📋 2. Tabla de Conexiones y Pinout Físico

| Componente | Pin del Componente | Pin ESP32 (GPIO) | Nivel de Voltaje | Función Técnica |
| :--- | :--- | :--- | :--- | :--- |
| **ESP32 DevKit** | `VIN` / `5V` | Fuente Externa / USB | 5.0 V DC | Alimentación general del riel de potencia |
| **ESP32 DevKit** | `GND` | `GND` Común | 0.0 V | Tierra de referencia unificada |
| **HC-SR04 (Sensor)** | `VCC` | `5V` (`VIN`) | 5.0 V | Alimentación del emisor ultrasónico |
| **HC-SR04 (Sensor)** | `GND` | `GND` | 0.0 V | Tierra |
| **HC-SR04 (Sensor)** | `TRIG` | **`GPIO 5`** | 3.3 V Output | Pulso de disparo de 10 µs |
| **HC-SR04 (Sensor)** | `ECHO` | **`GPIO 18`** | 3.3 V Input | Retorno de eco para cálculo de tiempo de vuelo |
| **SG90 (Servomotor)** | `VCC` (Cable Rojo) | `5V` (`VIN`) | 5.0 V | Potencia del motorreductor |
| **SG90 (Servomotor)** | `GND` (Cable Marrón) | `GND` | 0.0 V | Tierra |
| **SG90 (Servomotor)** | `PWM` (Cable Naranja)| **`GPIO 19`** | 3.3 V PWM | Señal de control angular (0° a 90°) |
| **LED Verde (Abierto)**| Ánodo (+) | **`GPIO 22`** | 3.3 V Output | Señalización visual de cerrojo destrabado |
| **LED Verde (Abierto)**| Cátodo (-) | Resistencia 220Ω ➔ `GND` | - | Limitación de corriente (15 mA) |
| **LED Rojo (Bloqueado)**| Ánodo (+) | **`GPIO 23`** | 3.3 V Output | Señalización visual de reposo / bloqueo |
| **LED Rojo (Bloqueado)**| Cátodo (-) | Resistencia 220Ω ➔ `GND` | - | Limitación de corriente (15 mA) |

---

## 🛠️ 3. Diagrama Esquemático en Texto

```text
               +----------------------------------------+
               |             ESP32 DEVKIT V1            |
               |                                        |
      +5V <----+ VIN                                GPIO5+------> HC-SR04 TRIG
      GND <----+ GND                               GPIO18+<------ HC-SR04 ECHO
               |                                   GPIO19+------> SG90 SERVO PWM
               |                                   GPIO22+------> [LED VERDE] ---> [220R] ---> GND
               |                                   GPIO23+------> [LED ROJO]  ---> [220R] ---> GND
               +----------------------------------------+
```

---

## 🚀 4. Procedimiento de Flasheo en Físico

1. **Requisitos de Software:**
   - Arduino IDE 2.x o VS Code con extensión PlatformIO.
   - Paquete de placas `esp32` de Espressif instalado en el Gestor de Tarjetas.
   - Librería `ESP32Servo` instalada desde el Gestor de Librerías de Arduino.

2. **Carga del Firmware:**
   - Abre [`Pestillo_Single_ESP32.ino`](file:///home/bollua/homework/IoT/PestilloInteligente-INACAP/hardware_single_esp32/Pestillo_Single_ESP32.ino).
   - Selecciona la placa: **DOIT ESP32 DEVKIT V1** (o tu variante de ESP32).
   - Conecta el ESP32 por USB y selecciona el puerto serial correspondiente (`/dev/ttyUSB0` o `/dev/ttyACM0`).
   - Haz clic en **Subir (Upload)**.

3. **Prueba Inalámbrica con la App Android:**
   - **Vía Bluetooth:**
     1. En los ajustes de Android, busca y vincula el dispositivo Bluetooth **`PestilloIoT`** (PIN por defecto `1234` o emparejamiento simple).
     2. Abre la app `Pestillo IoT`, selecciona **Panel de Control** ➔ **Bluetooth RF** ➔ Conectar.
     3. Pulsa **Destrabar Pestillo (3s)** y observa el movimiento del servo a 90°, el encendido del LED verde y el registro en tiempo real.
   - **Vía Wi-Fi TCP:**
     1. Conéctate con el smartphone a la red Wi-Fi emitida por el ESP32: `Pestillo-IoT-Nodo` (Clave: `Inacap2026!`).
     2. En la app Android, selecciona **Wi-Fi TCP (5050)** e ingresa la IP `192.168.4.1`.
     3. Acciona el cerrojo de forma remota.
