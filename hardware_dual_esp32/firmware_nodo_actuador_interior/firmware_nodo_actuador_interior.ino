/**
 * ============================================================================
 * PROYECTO: SISTEMA DE PESTILLO INTELIGENTE IoT DISTRIBUIDO (DUAL ESP32)
 * INSTITUCIÓN: INACAP - Aplicaciones Móviles para IoT (TI3042)
 * ARCHIVO: firmware_nodo_actuador_interior.ino
 * ROL: NODO 2 - ACTUADOR / RECEPTOR INTERIOR (Lado Puerta Interior / Cerradura)
 * ============================================================================
 * DESCRIPCIÓN:
 * Este nodo se ubica en el interior de la puerta físicamente acoplado a la
 * cerradura. Escucha paquetes ESP-NOW. Al recibir la orden de apertura desde el
 * Nodo Sensor o al presionar el pulsador interior manual (Request-to-Exit),
 * retrae el pestillo mecánicamente con el micro-servomotor SG90 a 90°,
 * mantiene 3.5 segundos de paso libre y vuelve a proyectar el pestillo a 0°.
 * ============================================================================
 */

#include <esp_now.h>
#include <WiFi.h>
#include <ESP32Servo.h>

// ==========================================
// 📌 1. ASIGNACIÓN DE PINES (PINOUT)
// ==========================================
#define PIN_SERVO_PWM     18   // Pin Señal PWM Servomotor SG90 (Cable Naranja/Amarillo)
#define PIN_LED_VERDE     22   // LED Verde (Pestillo Abierto / Desbloqueado)
#define PIN_LED_ROJO      23   // LED Rojo (Pestillo Cerrado / Bloqueado)
#define PIN_BUZZER        21   // Buzzer / Zumbador piezoeléctrico de confirmación
#define PIN_BOTON_MANUAL  4    // Botón Pulsador Interior (Apertura manual por contacto a GND)

// Constantes de Posición del Servomotor
const int ANGULO_BLOQUEADO = 0;    // 0°: Pestillo Proyectado (Cerrado)
const int ANGULO_ABIERTO   = 90;   // 90°: Pestillo Retraído (Abierto)
const int TIEMPO_APERTURA_MS = 3500; // 3.5 segundos de puerta abierta

// ==========================================
// 📡 2. ESTRUCTURA DE COMUNICACIÓN ESP-NOW
// ==========================================
typedef struct struct_pestillo_msg {
  uint32_t id_mensaje;    // Contador secuencial
  bool     abrir_pestillo;// true = Desbloquear
  uint16_t distancia_cm;  // Distancia del sensor emisor
  char     origen[16];    // Nombre del nodo emisor
} struct_pestillo_msg;

struct_pestillo_msg datosRx;

// Instancias de Hardware
Servo servomotorPestillo;
bool aperturaEnProgreso = false;

// ==========================================
// 🔊 3. FUNCIONES DE RETROALIMENTACIÓN Y CONTROL
// ==========================================
void emitirTonoApertura() {
  tone(PIN_BUZZER, 1800, 120);
  delay(140);
  tone(PIN_BUZZER, 2400, 180);
}

void emitirTonoCierre() {
  tone(PIN_BUZZER, 1200, 150);
  delay(180);
  tone(PIN_BUZZER, 800, 200);
}

void ejecutarSecuenciaApertura(const char* motivo) {
  if (aperturaEnProgreso) return;
  aperturaEnProgreso = true;

  Serial.println("\n=======================================================");
  Serial.printf("🔓 [ACCIONAMIENTO] INICIANDO SECUENCIA POR: %s\n", motivo);
  Serial.println("⚙️ [MECÁNICA] Retrayendo pestillo con servomotor SG90 (90°)...");
  
  // Feedback Visual y Sonoro
  digitalWrite(PIN_LED_ROJO, LOW);
  digitalWrite(PIN_LED_VERDE, HIGH);
  emitirTonoApertura();

  // Movimiento del Servo a 90°
  servomotorPestillo.write(ANGULO_ABIERTO);

  Serial.printf("⏳ [TEMPORIZADOR] Puerta abierta. Esperando %d ms de paso libre...\n", TIEMPO_APERTURA_MS);
  delay(TIEMPO_APERTURA_MS);

  // Cierre Automático
  Serial.println("🔒 [MECÁNICA] Proyectando pestillo con servomotor SG90 (0°)...");
  servomotorPestillo.write(ANGULO_BLOQUEADO);

  digitalWrite(PIN_LED_VERDE, LOW);
  digitalWrite(PIN_LED_ROJO, HIGH);
  emitirTonoCierre();

  Serial.println("✅ [ESTADO] Puerta re-bloqueada en modo reposo.");
  Serial.println("=======================================================\n");

  aperturaEnProgreso = false;
}

// ==========================================
// 🛰️ 4. CALLBACK DE RECEPCIÓN ESP-NOW
// ==========================================
void OnDataRecv(const esp_now_recv_info_t *recv_info, const uint8_t *incomingData, int len) {
  memcpy(&datosRx, incomingData, sizeof(datosRx));

  Serial.println("\n📡 [ESP-NOW RECEPCIÓN] Paquete inalámbrico recibido!");
  Serial.printf("   • Origen: %s\n", datosRx.origen);
  Serial.printf("   • ID Mensaje: %u | Longitud: %d bytes\n", datosRx.id_mensaje, len);
  Serial.printf("   • Distancia Sensor: %d cm | Orden Apertura: %s\n", 
                datosRx.distancia_cm, datosRx.abrir_pestillo ? "SÍ (TRUE)" : "NO");

  if (datosRx.abrir_pestillo) {
    ejecutarSecuenciaApertura("COMANDO INALÁMBRICO ESP-NOW");
  }
}

// ==========================================
// ⚙️ 5. CONFIGURACIÓN INICIAL (SETUP)
// ==========================================
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("  PESTILLO INTELIGENTE // NODO ACTUADOR INTERIOR (ESP32)");
  Serial.println("  INACAP TI3042 - CONTROL MECÁNICO Y ESP-NOW 2.4 GHz   ");
  Serial.println("=======================================================");

  // Configuración de Pines I/O
  pinMode(PIN_LED_VERDE, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_BOTON_MANUAL, INPUT_PULLUP); // Botón a tierra con resistencia interna PULLUP

  // Inicialización del Servomotor (Hardware PWM LEDC)
  servomotorPestillo.setPeriodHertz(50); // Frecuencia estándar de 50 Hz para servomotores
  servomotorPestillo.attach(PIN_SERVO_PWM, 500, 2400); // Rango de pulso microsegundos (SG90)
  servomotorPestillo.write(ANGULO_BLOQUEADO); // Posición inicial: 0° (Cerrado)

  // Estado inicial de LEDs (Rojo encendido = Bloqueado)
  digitalWrite(PIN_LED_ROJO, HIGH);
  digitalWrite(PIN_LED_VERDE, LOW);

  // Configurar Wi-Fi en Modo Estación (Requerido para ESP-NOW)
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  Serial.print("🌐 [WIFI] Modo Station Activo. Dirección MAC del Actuador: ");
  Serial.println(WiFi.macAddress());

  // Inicializar ESP-NOW
  if (esp_now_init() != ESP_OK) {
    Serial.println("❌ [ERROR CRÍTICO] Falló la inicialización de ESP-NOW.");
    while (true) { delay(1000); }
  }
  Serial.println("✅ [ESP-NOW] Receptor listo. Esperando tramas de radiofrecuencia.");

  // Registrar función callback de recepción
  esp_now_register_recv_cb(OnDataRecv);

  // Beep inicial de arranque
  tone(PIN_BUZZER, 2000, 100);
  Serial.println("🚀 [SISTEMA] Nodo Actuador operativo. Escuchando eventos...\n");
}

// ==========================================
// 🔁 6. BUCLE PRINCIPAL (LOOP)
// ==========================================
void loop() {
  // Comprobación de Botón Pulsador Interior Manual (Active LOW con INPUT_PULLUP)
  if (digitalRead(PIN_BOTON_MANUAL) == LOW) {
    delay(50); // Antirrebote (Debounce)
    if (digitalRead(PIN_BOTON_MANUAL) == LOW) {
      ejecutarSecuenciaApertura("BOTÓN MANUAL INTERIOR (PULLDOWN)");
      while (digitalRead(PIN_BOTON_MANUAL) == LOW) { delay(10); } // Esperar a que suelte el botón
    }
  }

  delay(20); // Pausa mínima para ahorro de ciclos de CPU
}
