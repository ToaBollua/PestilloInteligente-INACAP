/**
 * ============================================================================
 * PROYECTO: SISTEMA DE PESTILLO INTELIGENTE IoT DISTRIBUIDO (DUAL ESP32)
 * INSTITUCIÓN: INACAP - Aplicaciones Móviles para IoT (TI3042)
 * ARCHIVO: firmware_nodo_sensor_exterior.ino
 * ROL: NODO 1 - EMISOR / SENSOR EXTERIOR (Lado Puerta Exterior)
 * ============================================================================
 * DESCRIPCIÓN:
 * Este nodo se ubica en el exterior de la puerta. Mide continuamente la
 * proximidad mediante el sensor ultrasónico HC-SR04. Cuando un usuario acerca
 * su mano o credencial a menos de 8 cm (< 8 cm), genera un paquete de datos
 * seguro y lo transmite inalámbricamente vía ESP-NOW (2.4 GHz) al Nodo Actuador.
 * ============================================================================
 */

#include <esp_now.h>
#include <WiFi.h>

// ==========================================
// 📌 1. ASIGNACIÓN DE PINES (PINOUT)
// ==========================================
#define PIN_TRIG        5    // Pin Trigger del Sensor HC-SR04 (Salida)
#define PIN_ECHO        19   // Pin Echo del Sensor HC-SR04 (Entrada)
#define PIN_LED_ESTADO  2    // LED Azul Integrado en placa ESP32 (Parpadeo de actividad)
#define PIN_LED_VERDE   22   // LED Externo Verde (Detección exitosa)
#define PIN_LED_ROJO    23   // LED Externo Rojo (Reposo / Espera)

// ==========================================
// 📡 2. ESTRUCTURA DE COMUNICACIÓN ESP-NOW
// ==========================================
typedef struct struct_pestillo_msg {
  uint32_t id_mensaje;    // Contador secuencial de mensaje
  bool     abrir_pestillo;// true = Orden de desbloqueo
  uint16_t distancia_cm;  // Distancia física detectada en cm
  char     origen[16];    // Identificador del nodo emisor ("SENSOR_EXTERIOR")
} struct_pestillo_msg;

struct_pestillo_msg datosTx;

// Dirección de Broadcast (FF:FF:FF:FF:FF:FF para comunicarse con cualquier receptor en canal)
// Opcional: Reemplazar con la MAC física del Nodo Actuador para cifrado unicast.
uint8_t direccionBroadcast[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
esp_now_peer_info_t peerInfo;

// Variables de Control y Tiempos
uint32_t contadorMensajes = 0;
unsigned long ultimoSondeo = 0;
const unsigned long INTERVALO_SONDEO_MS = 100; // Muestreo ultrasónico cada 100ms
const int UMBRAL_DISTANCIA_CM = 8;             // Distancia de disparo (< 8 cm)

// ==========================================
// 🛰️ 3. CALLBACK DE ESTADO DE ENVÍO ESP-NOW
// ==========================================
void OnDataSent(const uint8_t *mac_addr, esp_now_send_status_t status) {
  Serial.print("📡 [ESP-NOW TRANSMISIÓN] Estado del paquete: ");
  if (status == ESP_NOW_SEND_SUCCESS) {
    Serial.println("✅ ENTREGADO CON ÉXITO (ACK Recibido)");
  } else {
    Serial.println("⚠️ ERROR DE ENTREGA (Sin receptor o fuera de rango)");
  }
}

// ==========================================
// ⚙️ 4. CONFIGURACIÓN INICIAL (SETUP)
// ==========================================
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("   PESTILLO INTELIGENTE // NODO SENSOR EXTERIOR (ESP32)");
  Serial.println("   INACAP TI3042 - PROTOCOLO ESP-NOW 2.4 GHz          ");
  Serial.println("=======================================================");

  // Configuración de Pines
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED_ESTADO, OUTPUT);
  pinMode(PIN_LED_VERDE, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);

  // Estado inicial de LEDs (Rojo encendido = Espera)
  digitalWrite(PIN_LED_ROJO, HIGH);
  digitalWrite(PIN_LED_VERDE, LOW);
  digitalWrite(PIN_LED_ESTADO, LOW);

  // Inicializar Wi-Fi en Modo Estación (Requerido por ESP-NOW)
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  Serial.print("🌐 [WIFI] Modo Station Activo. Dirección MAC del Sensor: ");
  Serial.println(WiFi.macAddress());

  // Inicializar protocolo ESP-NOW
  if (esp_now_init() != ESP_OK) {
    Serial.println("❌ [ERROR CRÍTICO] Falló la inicialización de ESP-NOW.");
    while (true) { delay(1000); }
  }
  Serial.println("✅ [ESP-NOW] Capa física y enlace inicializados correctamente.");

  // Registrar callback de transmisión
  esp_now_register_send_cb(OnDataSent);

  // Registrar el Peer Receptor (Broadcast)
  memcpy(peerInfo.peer_addr, direccionBroadcast, 6);
  peerInfo.channel = 0;     // Canal automático de Wi-Fi
  peerInfo.encrypt = false; // Sin cifrado para modo broadcast

  if (esp_now_add_peer(&peerInfo) != ESP_OK) {
    Serial.println("❌ [ERROR] No se pudo registrar el peer receptor.");
    return;
  }
  Serial.println("✅ [ESP-NOW] Peer registrado. Nodo Sensor listo y vigilando...\n");
}

// ==========================================
// 🔁 5. BUCLE PRINCIPAL (LOOP)
// ==========================================
void loop() {
  unsigned long tiempoActual = millis();

  // Sondeo ultrasónico no bloqueante cada 100 ms
  if (tiempoActual - ultimoSondeo >= INTERVALO_SONDEO_MS) {
    ultimoSondeo = tiempoActual;

    // Generar pulso de Trigger de 10 microsegundos
    digitalWrite(PIN_TRIG, LOW);
    delayMicroseconds(2);
    digitalWrite(PIN_TRIG, HIGH);
    delayMicroseconds(10);
    digitalWrite(PIN_TRIG, LOW);

    // Medir duración del eco (timeout de 25 ms para evitar bloqueos)
    long duracion = pulseIn(PIN_ECHO, HIGH, 25000);

    if (duracion > 0) {
      // Cálculo de distancia: Distancia (cm) = (Tiempo * Velocidad del Sonido 0.0343 cm/us) / 2
      int distancia = duracion * 0.0343 / 2;

      // Evaluar si la distancia está en el rango de activación (< 8 cm)
      if (distancia > 0 && distancia < UMBRAL_DISTANCIA_CM) {
        contadorMensajes++;
        
        Serial.println("\n-------------------------------------------------------");
        Serial.printf("🎯 [SENSOR DETECTADO] Distancia: %d cm (< %d cm)\n", distancia, UMBRAL_DISTANCIA_CM);
        Serial.printf("📦 [PAQUETE #%u] Transmitiendo orden de apertura al Actuador...\n", contadorMensajes);

        // Feedback Visual Local
        digitalWrite(PIN_LED_ROJO, LOW);
        digitalWrite(PIN_LED_VERDE, HIGH);
        digitalWrite(PIN_LED_ESTADO, HIGH);

        // Preparar trama de datos
        datosTx.id_mensaje     = contadorMensajes;
        datosTx.abrir_pestillo = true;
        datosTx.distancia_cm   = distancia;
        strncpy(datosTx.origen, "SENSOR_EXT", sizeof(datosTx.origen));

        // Enviar trama inalámbrica directa P2P
        esp_err_t resultado = esp_now_send(direccionBroadcast, (uint8_t *) &datosTx, sizeof(datosTx));

        if (resultado == ESP_OK) {
          Serial.println("🚀 [ESP-NOW] Trama enviada al aire exitosamente.");
        } else {
          Serial.printf("❌ [ESP-NOW] Error al enviar trama (Código: %d)\n", resultado);
        }
        Serial.println("-------------------------------------------------------");

        // Cooldown de 4 segundos para evitar re-disparos accidentales con la misma mano
        delay(4000);

        // Restaurar estado de LEDs a modo reposo
        digitalWrite(PIN_LED_VERDE, LOW);
        digitalWrite(PIN_LED_ROJO, HIGH);
        digitalWrite(PIN_LED_ESTADO, LOW);
      }
    }
  }
}
