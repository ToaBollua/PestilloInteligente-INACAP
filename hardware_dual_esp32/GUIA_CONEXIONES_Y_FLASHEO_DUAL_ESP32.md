# 🛠️ GUÍA TÁCTICA DE CONEXIONES Y FLASHEO DUAL ESP32: PESTILLO INTELIGENTE IoT
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) - INACAP  
**Integrantes:** Nicolás Anrique, Camilo Núñez, Diego Ibeas  
**Arquitectura:** Sistema Distribuido P2P con protocolo ESP-NOW (2.4 GHz) sobre 2 nodos ESP32-WROOM-32.

---

## 📌 1. RESUMEN DE LA ARQUITECTURA DISTRIBUIDA

El sistema se compone de dos microcontroladores ESP32 independientes que no requieren router Wi-Fi ni conexión a Internet para comunicarse, gracias al protocolo de capa de enlace **ESP-NOW** (latencia < 5 ms):

1. **NODO 1: SENSOR EXTERIOR (Emisor / Puerta Exterior):**
   - Detecta la presencia física del usuario mediante ultrasonido (< 8 cm).
   - Genera una trama binaria y la transmite por radiofrecuencia.
2. **NODO 2: ACTUADOR INTERIOR (Receptor / Puerta Interior):**
   - Recibe la trama por interrupción de hardware (`OnDataRecv`).
   - Acciona el servomotor SG90 a 90° para retraer el pestillo mecánico.
   - Cuenta con botón físico de salida manual interior y buzzer de confirmación sonora.

```
+------------------------------------+        ESP-NOW (2.4 GHz)        +------------------------------------+
|       NODO 1: SENSOR EXTERIOR      |  ===========================>   |      NODO 2: ACTUADOR INTERIOR     |
|  - ESP32 WROOM 32                  |      (Paquete de Apertura)      |  - ESP32 WROOM 32                  |
|  - Sensor Ultrasónico (HC-SR04)    |                                 |  - Servomotor SG90 (90° / 0°)      |
|  - LEDs Estado (Verde / Rojo)      |                                 |  - Buzzer Piezoeléctrico           |
|  - Alimentación 5V / USB           |                                 |  - Botón Manual de Salida          |
+------------------------------------+                                 +------------------------------------+
```

---

## 🔌 2. DIAGRAMA DETALLADO DE CONEXIONES PIN A PIN

### 🏷️ A. NODO 1: SENSOR EXTERIOR (ESP32 #1)

| Componente | Pin del Componente | Pin en la Placa ESP32 | Color Cable Sugerido | Descripción Técnica |
| :--- | :--- | :--- | :--- | :--- |
| **Sensor HC-SR04** | **VCC** | **VIN (5V)** | 🔴 Rojo | Alimentación de 5V para el emisor ultrasónico. |
| **Sensor HC-SR04** | **GND** | **GND** | ⚫ Negro | Tierra común. |
| **Sensor HC-SR04** | **TRIG** | **GPIO 5** | 🟡 Amarillo | Salida digital del ESP32 (Dispara pulso de 10µs). |
| **Sensor HC-SR04** | **ECHO** | **GPIO 19** *(con divisor)* | 🟢 Verde | Entrada digital del ESP32 (Mide ancho del eco). |
| **LED Verde** | **Ánodo (+)** *(res. 220Ω)* | **GPIO 22** | 🟢 Verde | Indicador de detección válida (< 8 cm). |
| **LED Verde** | **Cátodo (-)** | **GND** | ⚫ Negro | Retorno a tierra. |
| **LED Rojo** | **Ánodo (+)** *(res. 220Ω)* | **GPIO 23** | 🔴 Naranja | Indicador de reposo / vigilando. |
| **LED Rojo** | **Cátodo (-)** | **GND** | ⚫ Negro | Retorno a tierra. |

> [!TIP]
> **Divisor de Voltaje para el ECHO (Protección de 3.3V):**  
> El pin ECHO del HC-SR04 emite pulsos a 5V. El ESP32 tolera 3.3V en sus GPIOs. Para máxima durabilidad, coloca una resistencia de **1kΩ** entre ECHO y GPIO 19, y una de **2kΩ** entre GPIO 19 y GND.

---

### 🏷️ B. NODO 2: ACTUADOR INTERIOR CON SERVOMOTOR (ESP32 #2)

| Componente | Pin del Componente | Pin en la Placa ESP32 | Color Cable Sugerido | Descripción Técnica |
| :--- | :--- | :--- | :--- | :--- |
| **Servomotor SG90** | **VCC (Cable Rojo)** | **VIN (5V)** *(o Fuente 5V ext)* | 🔴 Rojo | Alimentación de corriente para el motor. |
| **Servomotor SG90** | **GND (Cable Marrón)** | **GND** | ⚫ Negro/Marrón | Tierra común con el ESP32. |
| **Servomotor SG90** | **PWM (Cable Naranja)** | **GPIO 18** | 🟠 Naranja | Señal de pulsos PWM (50 Hz / 500µs - 2400µs). |
| **LED Verde** | **Ánodo (+)** *(res. 220Ω)* | **GPIO 22** | 🟢 Verde | Indicador de Pestillo Abierto (90°). |
| **LED Verde** | **Cátodo (-)** | **GND** | ⚫ Negro | Retorno a tierra. |
| **LED Rojo** | **Ánodo (+)** *(res. 220Ω)* | **GPIO 23** | 🔴 Naranja | Indicador de Pestillo Bloqueado (0°). |
| **LED Rojo** | **Cátodo (-)** | **GND** | ⚫ Negro | Retorno a tierra. |
| **Buzzer Activo/Pasivo** | **Positivo (+)** | **GPIO 21** | 🟣 Violeta | Tonos de confirmación de apertura y cierre. |
| **Buzzer Activo/Pasivo** | **Negativo (-)** | **GND** | ⚫ Negro | Retorno a tierra. |
| **Pulsador Manual** | **Terminal 1** | **GPIO 4** | 🔵 Azul | Entrada digital con resistencia interna PULLUP. |
| **Pulsador Manual** | **Terminal 2** | **GND** | ⚫ Negro | Al presionar conecta GPIO 4 a tierra (LOW). |

---

## ⚡ 3. GUÍA DE FLASHEO PASO A PASO (ARDUINO IDE)

### 📋 Paso 1: Configurar Arduino IDE
1. Descarga e instala **Arduino IDE 2.x**.
2. Ve a **Archivo > Preferencias** y en *Gestor de URLs Adicionales de Tarjetas* pega:
   ```text
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
3. Ve a **Herramientas > Placa > Gestor de Tarjetas**, busca `esp32` e instala **esp32 by Espressif Systems**.
4. Ve a **Herramientas > Administrar Bibliotecas**, busca `ESP32Servo` (de Kevin Harrington) e instálala.

### 📥 Paso 2: Flashear el NODO SENSOR (ESP32 #1)
1. Conecta el primer ESP32 a la PC mediante cable USB (verifica que el cable transmita datos, no solo carga).
2. Abre el archivo:  
   [`hardware_dual_esp32/firmware_nodo_sensor_exterior.ino`](./firmware_nodo_sensor_exterior.ino)
3. Selecciona en **Herramientas**:
   - **Placa:** `ESP32 Dev Module` (o `DOIT ESP32 DEVKIT V1`).
   - **Puerto:** Selecciona el puerto COM o `/dev/ttyUSB0` correspondiente.
   - **Velocidad:** `115200`.
4. Haz clic en **Subir (Upload)**.
   > *Nota:* Si el IDE se queda en `Connecting........_____.....`, mantén presionado el botón **BOOT** en la placa ESP32 hasta que comience el flasheo (`Writing at 0x...`).

### 📥 Paso 3: Flashear el NODO ACTUADOR (ESP32 #2)
1. Desconecta el primer ESP32 y conecta el segundo ESP32.
2. Abre el archivo:  
   [`hardware_dual_esp32/firmware_nodo_actuador_interior.ino`](./firmware_nodo_actuador_interior.ino)
3. Verifica la selección de Placa y Puerto en **Herramientas**.
4. Haz clic en **Subir (Upload)**.

---

## 🔍 4. PRUEBA DE FUNCIONAMIENTO Y VALIDACIÓN

1. Alimenta ambos ESP32 (vía USB o cargador de celular 5V).
2. En el **Nodo Actuador**, el LED rojo se encenderá indicando que el servomotor está bloqueado a **0°**.
3. Acerca la mano a menos de **8 cm** del sensor ultrasónico en el **Nodo Sensor**.
4. El Nodo Sensor encenderá el LED verde y emitirá el paquete ESP-NOW.
5. El Nodo Actuador recibirá la orden de inmediato (< 5 ms):
   - El buzzer emitirá dos tonos agudos de apertura.
   - El LED rojo se apagará y el verde se encenderá.
   - El servomotor girará a **90°** retrayendo el pestillo.
   - Esperará **3.5 segundos** de paso libre.
   - El servomotor volverá a **0°** re-bloqueando el mecanismo con tonos graves.
6. Si presionas el **botón manual interior** en GPIO 4, se ejecutará el mismo ciclo de apertura local.

---

## 📐 5. FUENTES Y REPOSITORIOS PARA MODELOS 3D IMPRIMIBLES

Para imprimir en 3D la carcasa, los rieles y el mecanismo de pestillo adaptado al servomotor SG90:

### 🌐 Repositorios Recomendados (Descarga Gratuita de archivos STL / STEP / 3MF):
1. **[Printables.com](https://www.printables.com)** *(Prusa Research)*:
   - Los modelos más precisos mecánicamente, orientados a piezas funcionales.
   - *Búsquedas recomendadas:* `SG90 lock mechanism`, `ESP32 enclosure`, `sliding deadbolt servo`.
2. **[Thingiverse.com](https://www.thingiverse.com)** *(UltiMaker)*:
   - El repositorio con mayor cantidad histórica de pestillos Arduino/ESP32 y cerraduras rack-and-pinion.
   - *Búsquedas recomendadas:* `servo door latch`, `smart lock SG90`, `HC-SR04 mount`.
3. **[MakerWorld.com](https://makerworld.com)** *(Bambu Lab)*:
   - Diseños funcionales modernos optimizados para impresión rápida sin soportes.
4. **[Thangs.com](https://thangs.com)** *(Metabuscador Geométrico 3D)*:
   - Motor de búsqueda inteligente que rastrea Thingiverse, Printables y GrabCAD simultáneamente.
5. **[GrabCAD.com](https://grabcad.com)** *(Ingeniería y CAD Industrial)*:
   - Modelos CAD exactos (archivos STEP/SolidWorks) de servomotores SG90 y placas ESP32 para verificar tolerancias antes de imprimir.

### ⚙️ Parámetros de Impresión 3D Recomendados (Impresoras Universitarias / FDM):
- **Material:** PLA o PETG (PETG recomendado para mayor resistencia mecánica al impacto).
- **Relleno (Infill):** 35% - 50% (Patrón *Gyroid* o *Grid* para rigidez estructural).
- **Paredes / Perímetros:** 4 perímetros (evita que los engranajes o tornillos rompan la pieza).
- **Altura de Capa:** 0.20 mm estándar.
