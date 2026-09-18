# 🔐 Sistema de Pestillo Inteligente IoT Distribuido (INACAP)

Prototipo de control de acceso y seguridad perimetral basado en microcontroladores ESP32/Arduino, arquitectura no bloqueante y telemetría de estados.

---

## 📋 Componentes del Repositorio

*   **`PestilloInteligente_ES1.ino`**: Firmware en C++/Arduino con lógica no bloqueante (`millis()`), máquina de estados finitos, antirrebote por software y control de solenoide/servo.
*   **`Informe_Sumativo_ES1_Pestillo_Inteligente.html`**: Informe técnico sumativo interactivo con telemetría, esquemas de conexión, tablas de verdad y justificación arquitectónica.
*   **`Informe_Sumativo_ES1_Pestillo_Inteligente.md`**: Versión en Markdown editable del informe.
*   **`diagrama_flujo_pestillo.svg` / `.png`**: Diagrama de flujo del ciclo de vida y control de estados del pestillo.
*   **`wokwi_diagram.png`**: Simulación de circuito y conexionado en Wokwi.
*   **`pestillo_prototipo_fisico.jpg`**: Registro fotográfico del prototipo ensamblado.

---

## ⚙️ Especificaciones Técnicas

| Parámetro | Valor / Descripción |
|---|---|
| **Microcontrolador** | ESP32 / Arduino UNO |
| **Actuador Principal** | Solenoide de 12V con Relé Optoacoplado / Servomotor SG90 |
| **Entradas** | Pulsador capacitivo / sensor de fin de carrera |
| **Indicadores** | LEDs RGB de estado + Buzzer piezoeléctrico |
| **Temporizador Failsafe** | Cierre automático tras 5000ms sin bloqueo |
