/**
 * ============================================================================
 * SISTEMA DE PESTILLO INTELIGENTE IOT - NODO UNIFICADO (SINGLE ESP32)
 * ASIGNATURA: Aplicaciones Móviles para IoT (TI3042) - INACAP
 * EVALUACIÓN SUMATIVA 2 (U2) & DEMOSTRACIÓN FÍSICA
 * 
 * ARQUITECTURA DEL NODO:
 * Un único microcontrolador ESP32 gestiona la sensórica, el actuador electromecánico
 * y la doble pasarela de comunicación inalámbrica (Bluetooth SPP + Wi-Fi TCP 5050).
 * 
 * COMPONENTES FÍSICOS CONECTADOS:
 * 1. ESP32 DevKit v1 (30 o 38 pines)
 * 2. Sensor Ultrasónico HC-SR04 (Detección de Proximidad < 8cm)
 * 3. Micro-Servomotor SG90 (Retracción de cerrojo a 90° / Bloqueo a 0°)
 * 4. LED Verde (Indicador de Acceso Concedido / Destrabado)
 * 5. LED Rojo (Indicador de Reposo / Cerrojo Bloqueado)
 * 
 * PROTOCOLO DE TELEMETRÍA Y COMANDOS (ASCII / OT IEC 62443):
 * - Comandos Entrantes: "CMD;latch;OPEN\n" / "CMD;latch;LOCK\n"
 * - Confirmaciones (ACK): "ACK;latch;OPEN\n" / "ACK;latch;LOCKED\n"
 * - Telemetría Periódica: "LECTURA;dist;XX.X;latch;LOCKED\n"
 * ============================================================================
 */

#include <WiFi.h>
#include <ESP32Servo.h>
#include "BluetoothSerial.h"

// --- CONFIGURACIÓN DE PINES GPIO ---
#define PIN_TRIG        5    // HC-SR04 Trigger
#define PIN_ECHO       18    // HC-SR04 Echo (Lógica 3.3V)
#define PIN_SERVO      19    // SG90 Señal PWM
#define PIN_LED_GREEN  22    // LED Verde (Abierto)
#define PIN_LED_RED    23    // LED Rojo (Bloqueado)

// --- PARÁMETROS WI-FI (MODO SOFT-AP Y/O ESTACIÓN) ---
const char* WIFI_AP_SSID = "Pestillo-IoT-Nodo";
const char* WIFI_AP_PASS = "Inacap2026!";
const int   TCP_PORT     = 5050;

// Opcional: Descomentar y configurar si se desea conectar a la red Wi-Fi del hogar/laboratorio
const char* WIFI_STA_SSID = "TU_WIFI_SSID";
const char* WIFI_STA_PASS = "TU_WIFI_PASSWORD";
const bool  USAR_MODO_STA = false; // true: Se conecta al router / false: Crea su propia red AP

// --- INSTANCIAS DE HARDWARE Y COMUNICACIÓN ---
BluetoothSerial SerialBT;
WiFiServer      tcpServer(TCP_PORT);
WiFiClient      tcpClient;
Servo           servoPestillo;

// --- VARIABLES DE ESTADO Y TEMPORIZADORES NO BLOQUEANTES ---
bool latchAbierto = false;
unsigned long tiempoApertura = 0;
const unsigned long DURACION_APERTURA_MS = 3000; // 3 segundos (Norma Fail-Secure)

unsigned long ultimoSondeoSensor = 0;
const unsigned long INTERVALO_SENSOR_MS = 120; // Muestreo HC-SR04 cada 120ms

unsigned long ultimaTelemetria = 0;
const unsigned long INTERVALO_TELEMETRIA_MS = 1000; // Transmisión cada 1s

float distanciaActual = 50.0;

// --- PROTOTIPOS DE FUNCIONES ---
void destrabarPestillo(const char* origen);
void bloquearPestillo(const char* razon);
float medirDistanciaHCSR04();
void procesarTramaComando(String trama, bool esBluetooth);
void broadcastMensaje(String mensaje);

void setup() {
  Serial.begin(115200);
  delay(500);

  Serial.println("\n========================================================");
  Serial.println("  SISTEMA PESTILLO IOT DISTRIBUIDO - NODO FÍSICO ESP32 ");
  Serial.println("  TI3042 - INACAP // FIRMWARE UNIFICADO (BT + WIFI TCP) ");
  Serial.println("========================================================");

  // 1. Configuración de Pines I/O
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED_GREEN, OUTPUT);
  pinMode(PIN_LED_RED, OUTPUT);

  digitalWrite(PIN_TRIG, LOW);
  digitalWrite(PIN_LED_RED, HIGH);
  digitalWrite(PIN_LED_GREEN, LOW);

  // 2. Inicialización del Servomotor SG90 con Timer LEDC
  servoPestillo.setPeriodHertz(50); // Frecuencia estándar de 50Hz para SG90
  servoPestillo.attach(PIN_SERVO, 500, 2400); // Rango de pulso 500us (0°) a 2400us (180°)
  servoPestillo.write(0); // Posición inicial de reposo: 0° (Bloqueado)
  Serial.println("[HARDWARE] Servomotor SG90 acoplado en GPIO 19 -> Posición: 0° (LOCKED)");

  // 3. Inicialización de Bluetooth Serial (Classic SPP)
  if (!SerialBT.begin("PestilloIoT")) {
    Serial.println("❌ [BLUETOOTH] Fallo al iniciar Bluetooth Serial.");
  } else {
    Serial.println("✅ [BLUETOOTH] SPP Listo. Nombre de dispositivo: 'PestilloIoT'");
  }

  // 4. Inicialización de Wi-Fi y Servidor TCP (Puerto 5050)
  if (USAR_MODO_STA) {
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_STA_SSID, WIFI_STA_PASS);
    Serial.printf("[WIFI] Conectando a %s...", WIFI_STA_SSID);
    int intentos = 0;
    while (WiFi.status() != WL_CONNECTED && intentos < 20) {
      delay(500);
      Serial.print(".");
      intentos++;
    }
    if (WiFi.status() == WL_CONNECTED) {
      Serial.print("\n✅ [WIFI] Conectado en Modo STA. IP asignada: ");
      Serial.println(WiFi.localIP());
    } else {
      Serial.println("\n⚠️ [WIFI] No se pudo conectar a STA. Activando Modo Soft-AP de respaldo...");
      WiFi.mode(WIFI_AP);
      WiFi.softAP(WIFI_AP_SSID, WIFI_AP_PASS);
      Serial.print("✅ [WIFI] Soft-AP activo: ");
      Serial.println(WiFi.softAPIP());
    }
  } else {
    WiFi.mode(WIFI_AP);
    WiFi.softAP(WIFI_AP_SSID, WIFI_AP_PASS);
    Serial.print("✅ [WIFI] Modo Soft-AP creado exitosamente. SSID: '");
    Serial.print(WIFI_AP_SSID);
    Serial.print("' | IP: ");
    Serial.println(WiFi.softAPIP());
  }

  tcpServer.begin();
  Serial.printf("🚀 [TCP SERVER] Escuchando conexiones de la App Android en puerto %d\n\n", TCP_PORT);
}

void loop() {
  unsigned long ahora = millis();

  // =========================================================================
  // 1. SONDEO DEL SENSOR ULTRASÓNICO HC-SR04
  // =========================================================================
  if (ahora - ultimoSondeoSensor >= INTERVALO_SENSOR_MS) {
    ultimoSondeoSensor = ahora;
    float dist = medirDistanciaHCSR04();
    if (dist > 0.0 && dist < 400.0) {
      distanciaActual = dist;
    }

    // Detección de proximidad: Objeto/Mano a menos de 8.0 cm (Request-to-Exit)
    if (distanciaActual > 0.0 && distanciaActual < 8.0 && !latchAbierto) {
      Serial.printf("⚡ [SENSOR HC-SR04] ¡Proximidad detectada! Distancia: %.1f cm (< 8.0 cm)\n", distanciaActual);
      destrabarPestillo("SENSOR_PROXIMIDAD_LOCAL");
    }
  }

  // =========================================================================
  // 2. TEMPORIZADOR DE AUTO-CIERRE (NORMA FAIL-SECURE 3.0 SEGUNDOS)
  // =========================================================================
  if (latchAbierto && (ahora - tiempoApertura >= DURACION_APERTURA_MS)) {
    Serial.println("⏱️ [FAIL-SECURE] Tiempo de paso expirado (3000 ms).");
    bloquearPestillo("AUTO_CIERRE_EXPIRADO");
  }

  // =========================================================================
  // 3. GESTIÓN DE ENLACE BLUETOOTH SERIAL (COMANDOS ENTRANTES)
  // =========================================================================
  if (SerialBT.available()) {
    String tramaBt = SerialBT.readStringUntil('\n');
    tramaBt.trim();
    if (tramaBt.length() > 0) {
      Serial.printf("[RX BLUETOOTH] Trama recibida: %s\n", tramaBt.c_str());
      procesarTramaComando(tramaBt, true);
    }
  }

  // =========================================================================
  // 4. GESTIÓN DE ENLACE WI-FI TCP (PUERTO 5050)
  // =========================================================================
  if (tcpServer.hasClient()) {
    if (!tcpClient || !tcpClient.connected()) {
      if (tcpClient) tcpClient.stop();
      tcpClient = tcpServer.available();
      Serial.print("📱 [TCP GATEWAY] Nueva conexión desde App Android: ");
      Serial.println(tcpClient.peerIP());
    }
  }

  if (tcpClient && tcpClient.connected() && tcpClient.available()) {
    String tramaTcp = tcpClient.readStringUntil('\n');
    tramaTcp.trim();
    if (tramaTcp.length() > 0) {
      Serial.printf("[RX WI-FI TCP] Trama recibida: %s\n", tramaTcp.c_str());
      procesarTramaComando(tramaTcp, false);
    }
  }

  // =========================================================================
  // 5. TRANSMISIÓN PERIÓDICA DE TELEMETRÍA (CADA 1 SEGUNDO)
  // =========================================================================
  if (ahora - ultimaTelemetria >= INTERVALO_TELEMETRIA_MS) {
    ultimaTelemetria = ahora;
    String estadoStr = latchAbierto ? "UNLOCKED" : "LOCKED";
    
    // Trama estándar del protocolo
    String tramaDist = "LECTURA;dist;" + String(distanciaActual, 1);
    String tramaLatch = "LECTURA;latch;" + estadoStr;

    broadcastMensaje(tramaDist);
    broadcastMensaje(tramaLatch);
  }
}

// ===========================================================================
// FUNCIONES DE CONTROL DE HARDWARE Y ACCIONES
// ===========================================================================

void destrabarPestillo(const char* origen) {
  latchAbierto = true;
  tiempoApertura = millis();

  // Movimiento del Servomotor SG90 a 90° (Retracción mecánica del pasador)
  servoPestillo.write(90);

  // Indicadores Visuales (LED Verde encendido, Rojo apagado)
  digitalWrite(PIN_LED_RED, LOW);
  digitalWrite(PIN_LED_GREEN, HIGH);

  Serial.printf("🔓 [ACTUADOR] PESTILLO DESTRABADO (90°) | Origen: %s\n", origen);

  // Notificar por todos los enlaces activos
  broadcastMensaje("ACK;latch;OPEN");
  broadcastMensaje("LECTURA;latch;UNLOCKED");
}

void bloquearPestillo(const char* razon) {
  latchAbierto = false;

  // Movimiento del Servomotor SG90 a 0° (Bloqueo / Extensión del pasador)
  servoPestillo.write(0);

  // Indicadores Visuales (LED Rojo encendido, Verde apagado)
  digitalWrite(PIN_LED_GREEN, LOW);
  digitalWrite(PIN_LED_RED, HIGH);

  Serial.printf("🔒 [ACTUADOR] PESTILLO BLOQUEADO (0°) | Causa: %s\n", razon);

  // Notificar por todos los enlaces activos
  broadcastMensaje("ACK;latch;LOCKED");
  broadcastMensaje("LECTURA;latch;LOCKED");
}

float medirDistanciaHCSR04() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  // Timeout de 25000 microsegundos (~4.3 metros máx)
  long duracion = pulseIn(PIN_ECHO, HIGH, 25000);
  if (duracion == 0) return 100.0;

  float distanciaCm = (duracion * 0.0343) / 2.0;
  return distanciaCm;
}

void procesarTramaComando(String trama, bool esBluetooth) {
  if (trama == "CMD;latch;OPEN" || trama.indexOf("CMD;latch;OPEN") >= 0) {
    destrabarPestillo(esBluetooth ? "APP_BLUETOOTH_REMOTO" : "APP_WIFI_TCP_REMOTO");
  } else if (trama == "CMD;latch;LOCK" || trama.indexOf("CMD;latch;LOCK") >= 0) {
    bloquearPestillo(esBluetooth ? "APP_BLUETOOTH_MANUAL" : "APP_WIFI_TCP_MANUAL");
  }
}

void broadcastMensaje(String mensaje) {
  // Transmitir por Bluetooth Serial si hay cliente conectado
  if (SerialBT.hasClient()) {
    SerialBT.println(mensaje);
  }

  // Transmitir por Wi-Fi TCP si el cliente Android está conectado
  if (tcpClient && tcpClient.connected()) {
    tcpClient.println(mensaje);
  }
}
