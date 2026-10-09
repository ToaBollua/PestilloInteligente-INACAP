#!/usr/bin/env python3
import os

os.makedirs("/home/bollua/homework/IoT/PestilloInteligente-INACAP/docs/screens", exist_ok=True)

# 1. PANTALLA LOGIN
svg_login = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 740" width="360" height="740">
  <rect width="360" height="740" rx="36" fill="#0A0E17" stroke="#1E293B" stroke-width="4"/>
  <!-- Status Bar -->
  <text x="24" y="28" fill="#94A3B8" font-family="monospace" font-size="11">15:30</text>
  <text x="290" y="28" fill="#94A3B8" font-family="monospace" font-size="11">📶 84%</text>
  <!-- App Bar -->
  <rect x="0" y="44" width="360" height="48" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="20" y="74" fill="#00E676" font-family="monospace" font-weight="bold" font-size="13">PESTILLO IOT // AUTH KERNEL</text>
  <!-- Content -->
  <circle cx="180" cy="140" r="32" fill="#00E676" fill-opacity="0.1" stroke="#00E676" stroke-width="2"/>
  <text x="180" y="148" text-anchor="middle" font-size="28">🔒</text>
  <text x="180" y="195" text-anchor="middle" fill="#F8FAFC" font-family="monospace" font-weight="bold" font-size="16">INACAP TI3042</text>
  <text x="180" y="215" text-anchor="middle" fill="#64748B" font-family="monospace" font-size="11">SISTEMA CONTROL DE ACCESO IOT</text>
  
  <!-- Email Input -->
  <rect x="24" y="240" width="312" height="52" rx="8" fill="#0F172A" stroke="#00E5FF" stroke-width="1"/>
  <text x="36" y="260" fill="#00E5FF" font-family="monospace" font-size="9">CORREO INSTITUCIONAL</text>
  <text x="36" y="280" fill="#F8FAFC" font-family="monospace" font-size="13">operador@inacap.cl</text>

  <!-- Pass Input -->
  <rect x="24" y="306" width="312" height="52" rx="8" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="36" y="326" fill="#94A3B8" font-family="monospace" font-size="9">CLAVE DE ACCESO</text>
  <text x="36" y="346" fill="#F8FAFC" font-family="monospace" font-size="13">••••••••••••</text>

  <!-- RBAC Banner -->
  <rect x="24" y="372" width="312" height="64" rx="8" fill="#0F172A" stroke="#00E5FF" stroke-width="1" stroke-opacity="0.4"/>
  <text x="36" y="392" fill="#00E5FF" font-family="monospace" font-weight="bold" font-size="10">POLÍTICA RBAC (MENOR PRIVILEGIO):</text>
  <text x="36" y="410" fill="#94A3B8" font-family="monospace" font-size="9">Todo nuevo usuario se crea como OBSERVADOR.</text>
  <text x="36" y="424" fill="#94A3B8" font-family="monospace" font-size="9">Elevación a OPERADOR sujeta a Admin.</text>

  <!-- Login Button -->
  <rect x="24" y="456" width="312" height="48" rx="10" fill="#00E676"/>
  <text x="180" y="486" text-anchor="middle" fill="#0A0E17" font-family="monospace" font-weight="bold" font-size="13">INICIAR SESIÓN</text>

  <!-- Register Switch Button -->
  <rect x="24" y="516" width="312" height="42" rx="10" fill="transparent" stroke="#334155" stroke-width="1"/>
  <text x="180" y="542" text-anchor="middle" fill="#94A3B8" font-family="monospace" font-size="11">¿NO TIENES CUENTA? REGÍSTRATE</text>

  <!-- Security Badge -->
  <rect x="24" y="660" width="312" height="40" rx="6" fill="#00E676" fill-opacity="0.05" stroke="#00E676" stroke-width="1" stroke-dasharray="4"/>
  <text x="180" y="684" text-anchor="middle" fill="#00E676" font-family="monospace" font-size="10">FAIL-CLOSED AUTH // SHA-256 + GCM</text>
</svg>'''

# 2. PANTALLA CONEXION
svg_conexion = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 740" width="360" height="740">
  <rect width="360" height="740" rx="36" fill="#0A0E17" stroke="#1E293B" stroke-width="4"/>
  <text x="24" y="28" fill="#94A3B8" font-family="monospace" font-size="11">15:31</text>
  <text x="290" y="28" fill="#94A3B8" font-family="monospace" font-size="11">📶 84%</text>
  <!-- App Bar -->
  <rect x="0" y="44" width="360" height="48" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="20" y="74" fill="#00E676" font-family="monospace" font-weight="bold" font-size="13">ENLACE // CONEXIÓN NODO</text>
  
  <!-- User Pill -->
  <rect x="24" y="104" width="312" height="42" rx="8" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="36" y="126" fill="#F8FAFC" font-family="monospace" font-size="11">operador@inacap.cl</text>
  <rect x="240" y="112" width="86" height="24" rx="4" fill="#00E676" fill-opacity="0.2"/>
  <text x="283" y="128" text-anchor="middle" fill="#00E676" font-family="monospace" font-weight="bold" font-size="10">OPERADOR</text>

  <!-- Step 1: Mode -->
  <text x="24" y="172" fill="#00E676" font-family="monospace" font-weight="bold" font-size="11">1. SELECCIONA EL MODO OPERATIVO:</text>
  <rect x="24" y="184" width="150" height="40" rx="8" fill="#1E293B" stroke="#00E676" stroke-width="1.5"/>
  <text x="99" y="209" text-anchor="middle" fill="#00E676" font-family="monospace" font-weight="bold" font-size="11">🎛 Panel Control</text>
  <rect x="186" y="184" width="150" height="40" rx="8" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="261" y="209" text-anchor="middle" fill="#64748B" font-family="monospace" font-size="11">📡 Nodo IoT</text>

  <!-- Step 2: Medium -->
  <text x="24" y="248" fill="#00E676" font-family="monospace" font-weight="bold" font-size="11">2. MEDIO DE COMUNICACIÓN:</text>
  <rect x="24" y="260" width="150" height="40" rx="8" fill="#1E293B" stroke="#00E5FF" stroke-width="1.5"/>
  <text x="99" y="285" text-anchor="middle" fill="#00E5FF" font-family="monospace" font-weight="bold" font-size="11">🌐 Wi-Fi TCP (5050)</text>
  <rect x="186" y="260" width="150" height="40" rx="8" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="261" y="285" text-anchor="middle" fill="#64748B" font-family="monospace" font-size="11">🔵 Bluetooth SPP</text>

  <!-- IP Input -->
  <rect x="24" y="320" width="312" height="52" rx="8" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="36" y="340" fill="#94A3B8" font-family="monospace" font-size="9">DIRECCIÓN IP GATEWAY (TCP 5050)</text>
  <text x="36" y="360" fill="#F8FAFC" font-family="monospace" font-size="13">192.168.4.1</text>

  <!-- Suggestion Chips -->
  <rect x="24" y="382" width="120" height="24" rx="12" fill="#0F172A" stroke="#00E676" stroke-width="1"/>
  <text x="84" y="398" text-anchor="middle" fill="#00E676" font-family="monospace" font-size="9">ESP32 AP 192.168.4.1</text>
  <rect x="150" y="382" width="90" height="24" rx="12" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="195" y="398" text-anchor="middle" fill="#64748B" font-family="monospace" font-size="9">Waydroid</text>
  <rect x="246" y="382" width="90" height="24" rx="12" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="291" y="398" text-anchor="middle" fill="#64748B" font-family="monospace" font-size="9">Localhost</text>

  <!-- PIN Input -->
  <rect x="24" y="420" width="312" height="52" rx="8" fill="#0F172A" stroke="#00E676" stroke-width="1"/>
  <text x="36" y="440" fill="#00E676" font-family="monospace" font-size="9">PIN CIFRADO AES-256 (6 DÍGITOS OBLIGATORIO)</text>
  <text x="36" y="460" fill="#F8FAFC" font-family="monospace" font-size="14">8 4 9 2 0 1</text>

  <!-- Connect Button -->
  <rect x="24" y="500" width="312" height="50" rx="10" fill="#00E676"/>
  <text x="180" y="531" text-anchor="middle" fill="#0A0E17" font-family="monospace" font-weight="bold" font-size="13">VINCULAR Y ENLAZAR PANEL</text>
</svg>'''

# 3. PANTALLA PANEL OPERADOR
svg_panel = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 740" width="360" height="740">
  <rect width="360" height="740" rx="36" fill="#0A0E17" stroke="#1E293B" stroke-width="4"/>
  <text x="24" y="28" fill="#94A3B8" font-family="monospace" font-size="11">15:32</text>
  <text x="290" y="28" fill="#94A3B8" font-family="monospace" font-size="11">📶 84%</text>
  <!-- App Bar -->
  <rect x="0" y="44" width="360" height="48" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="20" y="74" fill="#00E676" font-family="monospace" font-weight="bold" font-size="13">PANEL OPERADOR // CONECTADO</text>
  
  <!-- Status Badge -->
  <rect x="24" y="104" width="312" height="150" rx="12" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <circle cx="180" cy="155" r="30" fill="#EF4444" fill-opacity="0.15" stroke="#EF4444" stroke-width="2"/>
  <text x="180" y="163" text-anchor="middle" font-size="24">🔒</text>
  <text x="180" y="208" text-anchor="middle" fill="#EF4444" font-family="monospace" font-weight="bold" font-size="16">PESTILLO BLOQUEADO</text>
  <text x="180" y="228" text-anchor="middle" fill="#94A3B8" font-family="monospace" font-size="10">NORMA IEC 62443 // FAIL-SECURE ACTIVO</text>

  <!-- Distance Gauge -->
  <rect x="24" y="266" width="312" height="90" rx="10" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="36" y="290" fill="#00E5FF" font-family="monospace" font-size="11">SENSOR ULTRASÓNICO (HC-SR04):</text>
  <text x="36" y="325" fill="#F8FAFC" font-family="monospace" font-weight="bold" font-size="28">38.4 cm</text>
  <rect x="180" y="306" width="140" height="10" rx="5" fill="#1E293B"/>
  <rect x="180" y="306" width="80" height="10" rx="5" fill="#00E5FF"/>
  <text x="180" y="332" fill="#64748B" font-family="monospace" font-size="9">Umbral proximidad: &lt; 8.0 cm</text>

  <!-- Open Action Button (Neon Green) -->
  <rect x="24" y="372" width="312" height="70" rx="12" fill="#00E676" stroke="#00E676" stroke-width="2"/>
  <text x="180" y="414" text-anchor="middle" fill="#0A0E17" font-family="monospace" font-weight="bold" font-size="16">🔓 ABRIR PESTILLO REMOTO</text>
  <text x="180" y="430" text-anchor="middle" fill="#0A0E17" font-family="monospace" font-size="10">DESPACHO CON NONCE ANTI-REPLAY</text>

  <!-- Live Log Console -->
  <rect x="24" y="460" width="312" height="170" rx="10" fill="#030712" stroke="#1E293B" stroke-width="1"/>
  <text x="36" y="482" fill="#00E676" font-family="monospace" font-weight="bold" font-size="10">&gt; TELEMETRÍA EN TIEMPO REAL:</text>
  <text x="36" y="504" fill="#94A3B8" font-family="monospace" font-size="9">15:31:02 [TCP] Conexión establecida a 192.168.4.1:5050</text>
  <text x="36" y="520" fill="#94A3B8" font-family="monospace" font-size="9">15:31:03 [AES] Clave PBKDF2 derivada con PIN 849201</text>
  <text x="36" y="536" fill="#00E676" font-family="monospace" font-size="9">15:31:10 [RX] LECTURA;dist;38.4;latch;LOCKED</text>
  <text x="36" y="552" fill="#00E5FF" font-family="monospace" font-size="9">15:31:25 [TX] CMD;latch;OPEN;OPERADOR;... (Anti-Replay)</text>
  <text x="36" y="568" fill="#00E676" font-family="monospace" font-size="9">15:31:26 [RX] ACK;latch;OPEN (Retracción SG90 a 90°)</text>
  <text x="36" y="584" fill="#EF4444" font-family="monospace" font-size="9">15:31:29 [SYS] Temporizador 3s expirado -&gt; Re-bloqueo</text>
</svg>'''

# 4. PANTALLA NODO PUERTA
svg_nodo = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 740" width="360" height="740">
  <rect width="360" height="740" rx="36" fill="#0A0E17" stroke="#1E293B" stroke-width="4"/>
  <text x="24" y="28" fill="#94A3B8" font-family="monospace" font-size="11">15:33</text>
  <text x="290" y="28" fill="#94A3B8" font-family="monospace" font-size="11">📶 84%</text>
  <!-- App Bar -->
  <rect x="0" y="44" width="360" height="48" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="20" y="74" fill="#00E5FF" font-family="monospace" font-weight="bold" font-size="13">NODO IOT // ACTUADOR PUERTA</text>

  <!-- Latch Graphic -->
  <rect x="24" y="104" width="312" height="200" rx="12" fill="#0F172A" stroke="#00E676" stroke-width="1"/>
  <circle cx="180" cy="180" r="45" fill="#00E676" fill-opacity="0.15" stroke="#00E676" stroke-width="3"/>
  <text x="180" y="190" text-anchor="middle" font-size="36">🔓</text>
  <text x="180" y="250" text-anchor="middle" fill="#00E676" font-family="monospace" font-weight="bold" font-size="18">PESTILLO ABIERTO (90°)</text>
  <text x="180" y="272" text-anchor="middle" fill="#94A3B8" font-family="monospace" font-size="10">PASO LIBRE EN CURSO (AUTO-LOCK EN 3S)</text>

  <!-- Hardware Indicators -->
  <rect x="24" y="316" width="150" height="70" rx="8" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="36" y="338" fill="#94A3B8" font-family="monospace" font-size="9">SERVOMOTOR SG90</text>
  <text x="36" y="364" fill="#00E676" font-family="monospace" font-weight="bold" font-size="16">ÁNGULO: 90°</text>

  <rect x="186" y="316" width="150" height="70" rx="8" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="198" y="338" fill="#94A3B8" font-family="monospace" font-size="9">LEDS ESTADO</text>
  <text x="198" y="364" fill="#00E676" font-family="monospace" font-weight="bold" font-size="14">VERDE (ON) / ROJO (OFF)</text>

  <!-- Proximity Slider Simulation -->
  <rect x="24" y="400" width="312" height="100" rx="10" fill="#0F172A" stroke="#334155" stroke-width="1"/>
  <text x="36" y="424" fill="#00E5FF" font-family="monospace" font-size="11">SIMULADOR SENSOR EXTERIOR (HC-SR04):</text>
  <rect x="36" y="445" width="288" height="8" rx="4" fill="#1E293B"/>
  <rect x="36" y="445" width="40" height="8" rx="4" fill="#EF4444"/>
  <circle cx="76" cy="449" r="8" fill="#00E676"/>
  <text x="36" y="475" fill="#EF4444" font-family="monospace" font-weight="bold" font-size="12">Distancia: 5.2 cm &lt; 8 cm -&gt; ACTIVACIÓN PROXIMIDAD</text>

  <!-- Security Verification Box -->
  <rect x="24" y="515" width="312" height="120" rx="10" fill="#030712" stroke="#00E676" stroke-width="1"/>
  <text x="36" y="538" fill="#00E676" font-family="monospace" font-weight="bold" font-size="10">🛡 VERIFICACIÓN EN NODO RECEPTOR:</text>
  <text x="36" y="558" fill="#94A3B8" font-family="monospace" font-size="9">✓ Validación RBAC: Emisor autenticado como OPERADOR</text>
  <text x="36" y="574" fill="#94A3B8" font-family="monospace" font-size="9">✓ Filtro Anti-Replay: Nonce 0x4f82a1 registrado</text>
  <text x="36" y="590" fill="#94A3B8" font-family="monospace" font-size="9">✓ Ventana Timestamp: +120ms (Válido dentro de 30s)</text>
  <text x="36" y="606" fill="#00E676" font-family="monospace" font-size="9">✓ Descifrado AES-GCM: Tag de 128-bits íntegro</text>
</svg>'''

# 5. PANTALLA HISTORIAL
svg_historial = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 740" width="360" height="740">
  <rect width="360" height="740" rx="36" fill="#0A0E17" stroke="#1E293B" stroke-width="4"/>
  <text x="24" y="28" fill="#94A3B8" font-family="monospace" font-size="11">15:34</text>
  <text x="290" y="28" fill="#94A3B8" font-family="monospace" font-size="11">📶 84%</text>
  <!-- App Bar -->
  <rect x="0" y="44" width="360" height="48" fill="#0F172A" stroke="#1E293B" stroke-width="1"/>
  <text x="20" y="74" fill="#00E676" font-family="monospace" font-weight="bold" font-size="13">CLOUD FIRESTORE // AUDITORÍA</text>

  <!-- Log Card 1 -->
  <rect x="24" y="104" width="312" height="80" rx="8" fill="#0F172A" stroke="#00E676" stroke-width="1"/>
  <text x="36" y="126" fill="#00E676" font-family="monospace" font-weight="bold" font-size="11">APERTURA_REMOTA_AUTORIZADA</text>
  <text x="36" y="144" fill="#94A3B8" font-family="monospace" font-size="9">Usuario: operador@inacap.cl [OPERADOR]</text>
  <text x="36" y="160" fill="#64748B" font-family="monospace" font-size="9">Distancia: 38.4 cm | Estado: UNLOCKED | Nonce: 0x4f82a1</text>
  <text x="260" y="126" fill="#64748B" font-family="monospace" font-size="9">15:31:26</text>

  <!-- Log Card 2 -->
  <rect x="24" y="194" width="312" height="80" rx="8" fill="#0F172A" stroke="#EF4444" stroke-width="1"/>
  <text x="36" y="216" fill="#EF4444" font-family="monospace" font-weight="bold" font-size="11">BLOQUEO_FAILSAFE_TEMPORIZADOR</text>
  <text x="36" y="234" fill="#94A3B8" font-family="monospace" font-size="9">Emisor: SISTEMA_AUTONOMO [FAIL-SECURE]</text>
  <text x="36" y="250" fill="#64748B" font-family="monospace" font-size="9">Causa: Expiración 3.5s paso libre | Estado: LOCKED</text>
  <text x="260" y="216" fill="#64748B" font-family="monospace" font-size="9">15:31:29</text>

  <!-- Log Card 3 -->
  <rect x="24" y="284" width="312" height="80" rx="8" fill="#0F172A" stroke="#00E5FF" stroke-width="1"/>
  <text x="36" y="306" fill="#00E5FF" font-family="monospace" font-weight="bold" font-size="11">PROXIMIDAD_LOCAL_SENSOR</text>
  <text x="36" y="324" fill="#94A3B8" font-family="monospace" font-size="9">Sensor: HC-SR04 Exterior ESP-NOW</text>
  <text x="36" y="340" fill="#64748B" font-family="monospace" font-size="9">Distancia: 4.8 cm (&lt; 8 cm) | Estado: UNLOCKED</text>
  <text x="260" y="306" fill="#64748B" font-family="monospace" font-size="9">15:30:12</text>

  <!-- Log Card 4 (Replay Rejection) -->
  <rect x="24" y="374" width="312" height="80" rx="8" fill="#0F172A" stroke="#F59E0B" stroke-width="1"/>
  <text x="36" y="396" fill="#F59E0B" font-family="monospace" font-weight="bold" font-size="11">INTENTO_INTRUSION_RECHAZADO</text>
  <text x="36" y="414" fill="#94A3B8" font-family="monospace" font-size="9">Causa: REPLAY_ATTACK_NONCE_DUPLICADO</text>
  <text x="36" y="430" fill="#64748B" font-family="monospace" font-size="9">Trama capturada retransmitida sin validez</text>
  <text x="260" y="396" fill="#64748B" font-family="monospace" font-size="9">15:28:44</text>
</svg>'''

with open("/home/bollua/homework/IoT/PestilloInteligente-INACAP/docs/screens/screen_1_login.svg", "w") as f:
    f.write(svg_login)

with open("/home/bollua/homework/IoT/PestilloInteligente-INACAP/docs/screens/screen_2_conexion.svg", "w") as f:
    f.write(svg_conexion)

with open("/home/bollua/homework/IoT/PestilloInteligente-INACAP/docs/screens/screen_3_panel.svg", "w") as f:
    f.write(svg_panel)

with open("/home/bollua/homework/IoT/PestilloInteligente-INACAP/docs/screens/screen_4_nodo.svg", "w") as f:
    f.write(svg_nodo)

with open("/home/bollua/homework/IoT/PestilloInteligente-INACAP/docs/screens/screen_5_historial.svg", "w") as f:
    f.write(svg_historial)

print("Screens generadas con exito.")
