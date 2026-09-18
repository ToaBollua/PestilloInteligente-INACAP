/**
 * ============================================================================
 * PROYECTO: SISTEMA DE PESTILLO INTELIGENTE IoT DISTRIBUIDO (ES1)
 * ASIGNATURA: Aplicaciones Móviles para IoT (TI3042) - INACAP
 * INTEGRANTES: Nicolás Anrique, Camilo Núñez, Diego Ibeas
 * FECHA: Septiembre 2026
 * 
 * DESCRIPCIÓN:
 * Firmware unificado para la simulación y despliegue del sistema distribuido.
 * Implementa comunicación Peer-to-Peer mediante ESP-NOW sobre Wi-Fi (2.4 GHz).
 * - NODO SENSOR (Exterior): Lee HC-SR04 (< 8cm) y emite trama de apertura.
 * - NODO ACTUADOR (Interior): Recibe trama vía interrupción OnDataRecv y acciona
 *   el micro-servomotor SG90 a 90° (abierto) por 3 segundos, retornando a 0°.
 * ============================================================================
 */

#include <esp_now.h>
#include <WiFi.h>
#include <ESP32Servo.h>

// --- ASIGNACIÓN DE PINES ---
#define TRIG_PIN 5     // Pin Trigger Sensor Ultrasónico (Nodo Sensor)
#define ECHO_PIN 19    // Pin Echo Sensor Ultrasónico (Nodo Sensor)
#define SERVO_PIN 18   // Pin Señal PWM Servomotor SG90 (Nodo Actuador)
#define LED_GREEN 22   // Indicador Acceso / Desbloqueado (Opcional)
#define LED_RED 23     // Indicador Bloqueado / Reposo (Opcional)

// --- ESTRUCTURA DE MENSAJE ESP-NOW ---
typedef struct struct_message {
  bool unlock;         // true: Orden de retracción / apertura
  uint32_t msg_id;     // Identificador de secuencia del mensaje
} struct_message;

struct_message txData; // Datos a transmitir (Sensor)
struct_message rxData; // Datos recibidos (Actuador)

// --- DIRECCIÓN DE BROADCAST / PEER ---
// Para compatibilidad con simulación en Wokwi se utiliza Broadcast (FF:FF:FF:FF:FF:FF).
// En despliegue de hardware con cifrado Unicast, se reemplaza por la MAC física del receptor.
uint8_t broadcastAddress[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
esp_now_peer_info_t peerInfo;

// --- INSTANCIAS DE CONTROL ---
Servo latchServo;
uint32_t messageCounter = 0;
unsigned long lastSensorRead = 0;
const unsigned long SENSOR_INTERVAL = 100; // Sondeo cada 100ms

// --- CALLBACK DE RECEPCIÓN (NODO ACTUADOR) ---
void OnDataRecv(const esp_now_recv_info_t *recv_info, const uint8_t *incomingData, int len) {
  memcpy(&rxData, incomingData, sizeof(rxData));
  
  Serial.println("\n------------------------------------------------");
  Serial.printf("[ACTUADOR ESP-NOW] Trama recibida (%d bytes) | MsgID: %u\n", len, rxData.msg_id);
  
  if (rxData.unlock) {
    Serial.println("[ACTUADOR] -> ORDEN VALIDADA: APERTURA SOLICITADA");
    Serial.println("[ACTUADOR] -> Accionando Servo: RETRAYENDO PESTILLO (90°)");
    
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_GREEN, HIGH);
    
    latchServo.write(90);
    delay(3000); // Mantiene la puerta abierta durante 3 segundos
    
    Serial.println("[ACTUADOR] -> Tiempo de paso expirado (3000 ms).");
    Serial.println("[ACTUADOR] -> Accionando Servo: PROYECTANDO PESTILLO (0°)");
    
    latchServo.write(0);
    digitalWrite(LED_GREEN, LOW);
    digitalWrite(LED_RED, HIGH);
    
    Serial.println("[ACTUADOR] -> Estado: PUERTA BLOQUEADA (REPOSO)");
  }
  Serial.println("------------------------------------------------");
}

// --- CALLBACK DE ENVÍO (NODO SENSOR - OPCIONAL PARA TELEMETRÍA) ---
void OnDataSent(const uint8_t *mac_addr, esp_now_send_status_t status) {
  Serial.print("[SENSOR ESP-NOW] Estado de transmisión del paquete: ");
  Serial.println(status == ESP_NOW_SEND_SUCCESS ? "ENTREGA EXITOSA (ACK)" : "FALLO EN ENVÍO");
}

void setup() {
  Serial.begin(115200);
  delay(500);
  
  Serial.println("\n==================================================");
  Serial.println("   INICIALIZANDO SISTEMA DE PESTILLO INTELIGENTE  ");
  Serial.println("   TI3042 - INACAP // EVALUACIÓN SUMATIVA 1 (ES1) ");
  Serial.println("==================================================");

  // Configuración de Pines I/O
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_RED, OUTPUT);

  // Inicialización del Servomotor (LEDC Hardware PWM)
  latchServo.setPeriodHertz(50); // Frecuencia estándar SG90: 50 Hz
  latchServo.attach(SERVO_PIN, 500, 2400); // Rango de pulso: 500us a 2400us
  latchServo.write(0); // Posición inicial: 0° (Bloqueado)
  
  digitalWrite(LED_RED, HIGH);
  digitalWrite(LED_GREEN, LOW);

  // Configuración de Wi-Fi en Modo Estación (Requerido por ESP-NOW)
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  
  Serial.print("[WIFI] Modo Station activo. MAC Local: ");
  Serial.println(WiFi.macAddress());

  // Inicialización del protocolo ESP-NOW
  if (esp_now_init() != ESP_OK) {
    Serial.println("❌ [ERROR CRÍTICO] Falló la inicialización de ESP-NOW.");
    return;
  }
  Serial.println("✅ [ESP-NOW] Protocolo inicializado correctamente.");

  // Registro de Callbacks
  esp_now_register_recv_cb(OnDataRecv);
  esp_now_register_send_cb(OnDataSent);

  // Registro del Peer (Receptor Broadcast)
  memcpy(peerInfo.peer_addr, broadcastAddress, 6);
  peerInfo.channel = 0;     // Canal automático de radiofrecuencia Wi-Fi
  peerInfo.encrypt = false; // Sin cifrado en modo broadcast para Wokwi
  
  if (esp_now_add_peer(&peerInfo) != ESP_OK) {
    Serial.println("❌ [ERROR] No se pudo registrar el peer de destino.");
    return;
  }
  Serial.println("✅ [ESP-NOW] Peer registrado exitosamente.");
  Serial.println("[SISTEMA] Listo. Escuchando eventos y sondeo ultrasónico...\n");
}

void loop() {
  unsigned long currentMillis = millis();

  // Sondeo periódico del Sensor Ultrasónico
  if (currentMillis - lastSensorRead >= SENSOR_INTERVAL) {
    lastSensorRead = currentMillis;

    // Disparo del pulso ultrasónico (Trigger 10us)
    digitalWrite(TRIG_PIN, LOW);
    delayMicroseconds(2);
    digitalWrite(TRIG_PIN, HIGH);
    delayMicroseconds(10);
    digitalWrite(TRIG_PIN, LOW);

    // Medición del ancho de pulso con timeout de 30ms (no bloqueante para distancias largas)
    long duration = pulseIn(ECHO_PIN, HIGH, 30000);

    if (duration > 0) {
      // Cálculo de distancia física: Distancia (cm) = (Tiempo * Velocidad_Sonido) / 2
      int distance = duration * 0.034 / 2;

      // Umbral de detección: Menor a 8 cm (Request-to-Exit)
      if (distance > 0 && distance < 8) {
        messageCounter++;
        Serial.printf("\n[SENSOR] ¡PRESENCIA DETECTADA! Distancia: %d cm (< 8 cm)\n", distance);
        Serial.printf("[SENSOR] Preparando paquete ESP-NOW (MsgID: %u)...\n", messageCounter);

        txData.unlock = true;
        txData.msg_id = messageCounter;

        // Transmisión inalámbrica P2P directa
        esp_err_t result = esp_now_send(broadcastAddress, (uint8_t *) &txData, sizeof(txData));

        if (result == ESP_OK) {
          Serial.println("[SENSOR] -> Paquete emitido al éter correctamente.");
        } else {
          Serial.printf("[SENSOR] -> Error al emitir paquete: %d\n", result);
        }

        // Periodo de enfriamiento para evitar ráfagas múltiples ante la misma mano
        delay(3500);
      }
    }
  }
}
