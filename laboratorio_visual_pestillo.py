#!/usr/bin/env python3
"""
================================================================================
LABORATORIO VISUAL INTERACTIVO: PESTILLO IOT DISTRIBUIDO (TI3042 INACAP)
================================================================================
Entorno gráfico en tiempo real para pruebas interactivas del Arquitecto.
Simula:
- ESP32 Nodo 1: Sensor Ultrasónico HC-SR04 + Transmisión ESP-NOW.
- ESP32 Nodo 2: Actuador Servomotor SG90 + Gateway TCP:5050 + LEDs GPIO 22/23.
- Teléfono A: App Android en Modo Nodo IoT (Servidor).
- Teléfono B: App Android en Modo Panel Operador (RBAC: Operador / Observador).
- Sniffer de Protocolo: Criptografía AES-256-GCM y Tramas TIPO;CLAVE;VALOR.
- Auditoría Cloud Firestore en Vivo.
"""

import http.server
import socketserver
import json
import threading
import time
import socket
import os
import sys
import base64
import urllib.parse
import urllib.request
from virtual_esp32_gateway import (
    SimuladorESP32,
    derivar_clave_aes,
    cifrar_trama,
    descifrar_trama,
)

PORT_HTTP = 8090
PORT_TCP_GATEWAY = 5050
FIREBASE_API_KEY = "AIzaSyPLACEHOLDER_FOR_LOCAL_BUILD_ONLY_0000"
FIREBASE_PROJECT_ID = "pestillo-iot-inacap"

# Estado global del laboratorio
lab_state = {
    "distancia": 45.0,
    "latch_state": "LOCKED",   # "LOCKED" o "UNLOCKED"
    "servo_angle": 0,          # 0 a 90
    "timer_countdown": 0.0,
    "rol_usuario": "OPERADOR", # "OPERADOR" o "OBSERVADOR"
    "usuario_email": "operador@inacap.cl",
    "pin_cifrado": "123456",
    "enlace_estado": "CONECTADO",
    "led_green": False,
    "led_red": True,
    "esp_now_packets": 0,
    "ultimo_evento": "Sistema iniciado y conectado a Cloud Firestore.",
    "logs_firestore": [],
    "sniffer": []
}

lock = threading.Lock()
key_bytes = derivar_clave_aes("123456")

# Simulador TCP Gateway
sim_esp32 = SimuladorESP32(port=PORT_TCP_GATEWAY, pin_cifrado="123456")

_firebase_token_cache = {"token": None, "expiry": 0}

def _get_cloud_token():
    now = time.time()
    if _firebase_token_cache["token"] and now < _firebase_token_cache["expiry"]:
        return _firebase_token_cache["token"]
    try:
        url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
        payload = json.dumps({"email": "operador@inacap.cl", "password": "Inacap2026!", "returnSecureToken": True}).encode("utf-8")
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=4) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            _firebase_token_cache["token"] = data["idToken"]
            _firebase_token_cache["expiry"] = now + 3000
            return data["idToken"]
    except Exception as e:
        print(f"[FIREBASE TOKEN ERR] {e}")
        return None

def _async_push_to_cloud_firestore(accion, estado, dist, email, rol):
    try:
        token = _get_cloud_token()
        if not token:
            return
        url = f"https://firestore.googleapis.com/v1/projects/{FIREBASE_PROJECT_ID}/databases/(default)/documents/accesos_log"
        body = {
            "fields": {
                "timestamp": {"integerValue": str(int(time.time() * 1000))},
                "email": {"stringValue": str(email)},
                "rol": {"stringValue": str(rol)},
                "accion": {"stringValue": str(accion)},
                "estado": {"stringValue": str(estado)},
                "distancia": {"doubleValue": float(dist)}
            }
        }
        req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"), headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}"
        })
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            doc_id = data.get("name", "").split("/")[-1]
            print(f"[CLOUD FIRESTORE SYNC] Evento '{accion}' guardado en la nube: {doc_id}")
    except Exception as e:
        print(f"[CLOUD FIRESTORE SYNC ERR] {e}")

def _fetch_cloud_logs_on_startup():
    try:
        token = _get_cloud_token()
        if not token:
            return
        url = f"https://firestore.googleapis.com/v1/projects/{FIREBASE_PROJECT_ID}/databases/(default)/documents/accesos_log"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            docs = data.get("documents", [])
            nuevos = []
            for doc in docs:
                fields = doc.get("fields", {})
                ts = int(fields.get("timestamp", {}).get("integerValue", time.time()*1000)) / 1000.0
                hora = time.strftime("%H:%M:%S", time.localtime(ts))
                em = fields.get("email", {}).get("stringValue", "operador@inacap.cl")
                ro = fields.get("rol", {}).get("stringValue", "OPERADOR")
                acc = fields.get("accion", {}).get("stringValue", "ACCESO")
                est = fields.get("estado", {}).get("stringValue", "LOCKED")
                dist = float(fields.get("distancia", {}).get("doubleValue", fields.get("distancia", {}).get("integerValue", 0.0)))
                nuevos.append({
                    "id": doc.get("name", "").split("/")[-1][:8],
                    "hora": hora,
                    "email": em,
                    "rol": ro,
                    "accion": acc,
                    "estado": est,
                    "dist": round(dist, 1)
                })
            with lock:
                if nuevos:
                    lab_state["logs_firestore"] = nuevos[:30]
    except Exception as e:
        print(f"[FETCH LOGS ERR] {e}")

# Iniciar sync de inicio en segundo plano
threading.Thread(target=_fetch_cloud_logs_on_startup, daemon=True).start()

def add_sniffer(tipo_dir, texto_plano, texto_cifrado):
    with lock:
        lab_state["sniffer"].insert(0, {
            "hora": time.strftime("%H:%M:%S"),
            "dir": tipo_dir, # "TX" o "RX"
            "plano": texto_plano,
            "cifrado": texto_cifrado[:35] + "..." if len(texto_cifrado) > 35 else texto_cifrado
        })
        if len(lab_state["sniffer"]) > 25:
            lab_state["sniffer"].pop()


def add_firestore_log(accion, estado, dist, email=None, rol=None):
    em = email or lab_state["usuario_email"]
    ro = rol or lab_state["rol_usuario"]
    with lock:
        lab_state["logs_firestore"].insert(0, {
            "id": f"log_{len(lab_state['logs_firestore'])+1:03d}",
            "hora": time.strftime("%H:%M:%S"),
            "email": em,
            "rol": ro,
            "accion": accion,
            "estado": estado,
            "dist": round(dist, 1)
        })
        if len(lab_state["logs_firestore"]) > 40:
            lab_state["logs_firestore"].pop()

    # Sincronización real asíncrona con Cloud Firestore
    threading.Thread(target=_async_push_to_cloud_firestore, args=(accion, estado, dist, em, ro), daemon=True).start()


def trigger_apertura(fuente="PANEL_OPERADOR"):
    with lock:
        if lab_state["latch_state"] == "UNLOCKED":
            return
        lab_state["latch_state"] = "UNLOCKED"
        lab_state["servo_angle"] = 90
        lab_state["led_green"] = True
        lab_state["led_red"] = False
        lab_state["timer_countdown"] = 3.0
        lab_state["ultimo_evento"] = f"Pestillo DESTRABADO (90°) por {fuente}"

    # Broadcast a clientes TCP (Android / Host)
    sim_esp32.broadcast_mensaje("ACK;latch;OPEN")
    sim_esp32.broadcast_mensaje("LECTURA;latch;UNLOCKED")

    cif = cifrar_trama("ACK;latch;OPEN", key_bytes)
    add_sniffer("ESP32 ➔ APP", "ACK;latch;OPEN", cif)
    add_firestore_log(f"APERTURA_{fuente}", "UNLOCKED", lab_state["distancia"])

    # Disparar hilo temporizador
    threading.Thread(target=_timer_autocierre, daemon=True).start()


def _timer_autocierre():
    for i in range(30):
        time.sleep(0.1)
        with lock:
            if lab_state["latch_state"] != "UNLOCKED":
                return
            lab_state["timer_countdown"] = max(0.0, round(3.0 - (i + 1) * 0.1, 1))

    with lock:
        lab_state["latch_state"] = "LOCKED"
        lab_state["servo_angle"] = 0
        lab_state["led_green"] = False
        lab_state["led_red"] = True
        lab_state["timer_countdown"] = 0.0
        lab_state["ultimo_evento"] = "Auto-cierre completado: Pestillo BLOQUEADO (0°)"

    sim_esp32.broadcast_mensaje("ACK;latch;LOCKED")
    sim_esp32.broadcast_mensaje("LECTURA;latch;LOCKED")

    cif = cifrar_trama("ACK;latch;LOCKED", key_bytes)
    add_sniffer("ESP32 ➔ APP", "ACK;latch;LOCKED", cif)
    add_firestore_log("BLOQUEO_FAILSAFE", "LOCKED", lab_state["distancia"], email="sistema@nodo.local", rol="SISTEMA")


def trigger_bloqueo():
    with lock:
        lab_state["latch_state"] = "LOCKED"
        lab_state["servo_angle"] = 0
        lab_state["led_green"] = False
        lab_state["led_red"] = True
        lab_state["timer_countdown"] = 0.0
        lab_state["ultimo_evento"] = "Bloqueo Forzado: Pestillo asegurado a 0°"

    sim_esp32.broadcast_mensaje("ACK;latch;LOCKED")
    sim_esp32.broadcast_mensaje("LECTURA;latch;LOCKED")

    cif = cifrar_trama("ACK;latch;LOCKED", key_bytes)
    add_sniffer("ESP32 ➔ APP", "ACK;latch;LOCKED", cif)
    add_firestore_log("BLOQUEO_MANUAL", "LOCKED", lab_state["distancia"])


def set_distancia_lab(d: float):
    with lock:
        lab_state["distancia"] = round(max(2.0, min(d, 200.0)), 1)
        lab_state["esp_now_packets"] += 1

    trama = f"LECTURA;dist;{lab_state['distancia']:.1f}"
    sim_esp32.broadcast_mensaje(trama)
    cif = cifrar_trama(trama, key_bytes)
    add_sniffer("SENSOR ➔ ACTUADOR (ESP-NOW)", trama, cif)

    if lab_state["distancia"] < 8.0 and lab_state["latch_state"] == "LOCKED":
        trigger_apertura("SENSOR_PROXIMIDAD_HCSR04")


# Enlazar parser de comandos del Gateway TCP directamente a la máquina de estados del laboratorio
def _esp32_procesar_comando_hook(linea: str):
    partes = linea.split(";")
    if len(partes) < 3:
        return
    tipo, clave, valor = partes[0].upper(), partes[1], partes[2]
    cif = cifrar_trama(linea, key_bytes)
    add_sniffer("APP ➔ ESP32", linea, cif)

    if tipo == "CMD" and clave == "latch":
        if valor == "OPEN":
            trigger_apertura("APP_ANDROID_REMOTA")
        elif valor == "LOCK":
            trigger_bloqueo()
    elif tipo == "LECTURA" and clave == "dist":
        try:
            d = float(valor)
            set_distancia_lab(d)
        except Exception:
            pass

sim_esp32.procesar_comando = _esp32_procesar_comando_hook


HTML_PAGE = """<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LABORATORIO INTERACTIVO // PESTILLO IOT DISTRIBUIDO</title>
    <style>
        :root {
            --bg: #06090e;
            --panel: #0b111a;
            --panel-card: #101824;
            --border: #1a2738;
            --border-bright: #2b3d55;
            --green: #00ff66;
            --cyan: #00f0ff;
            --pink: #ff0055;
            --yellow: #ffb800;
            --text: #e2e8f0;
            --muted: #64748b;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: var(--bg);
            color: var(--text);
            font-family: 'Fira Code', 'Courier New', monospace;
            padding: 16px;
            font-size: 12.5px;
        }

        .header-bar {
            background: var(--panel);
            border: 2px solid var(--green);
            box-shadow: 0 0 18px rgba(0, 255, 102, 0.2);
            padding: 14px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .header-title {
            color: var(--green);
            font-size: 18px;
            font-weight: 800;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .led-pulse {
            width: 10px; height: 10px;
            background-color: var(--green);
            border-radius: 50%;
            box-shadow: 0 0 8px var(--green);
            animation: pulse 1.2s infinite;
        }
        @keyframes pulse { 0%,100%{opacity:1;} 50%{opacity:0.3;} }

        .main-grid {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            gap: 16px;
            margin-bottom: 16px;
        }

        @media (max-width: 1200px) {
            .main-grid { grid-template-columns: 1fr; }
        }

        .section-card {
            background: var(--panel);
            border: 1px solid var(--border);
            padding: 16px;
            border-radius: 4px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .card-header {
            color: var(--cyan);
            font-size: 13.5px;
            font-weight: bold;
            border-bottom: 1px solid var(--border);
            padding-bottom: 8px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .badge {
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 10px;
            font-weight: bold;
        }
        .badge-green { background: rgba(0,255,102,0.15); color: var(--green); border: 1px solid var(--green); }
        .badge-yellow { background: rgba(255,184,0,0.15); color: var(--yellow); border: 1px solid var(--yellow); }
        .badge-pink { background: rgba(255,0,85,0.15); color: var(--pink); border: 1px solid var(--pink); }

        /* SERVO CANVAS */
        .servo-container {
            background: #05070c;
            border: 1px solid var(--border);
            padding: 16px;
            text-align: center;
            border-radius: 6px;
        }

        .servo-box {
            position: relative;
            width: 160px;
            height: 160px;
            margin: 0 auto;
            background: #0a111a;
            border-radius: 50%;
            border: 3px solid #1a293d;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .servo-arm {
            position: absolute;
            width: 65px;
            height: 10px;
            background: var(--green);
            transform-origin: 10px 5px;
            left: 70px;
            top: 75px;
            border-radius: 5px;
            transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 0 10px var(--green);
        }

        .servo-center {
            width: 24px; height: 24px;
            background: #ffffff;
            border-radius: 50%;
            z-index: 2;
        }

        /* LEDS */
        .led-indicator {
            display: inline-block;
            width: 14px; height: 14px;
            border-radius: 50%;
            margin-right: 6px;
        }
        .led-on-green { background: #00ff66; box-shadow: 0 0 10px #00ff66; }
        .led-off-green { background: #003314; }
        .led-on-red { background: #ff0055; box-shadow: 0 0 10px #ff0055; }
        .led-off-red { background: #330011; }

        /* BUTTONS */
        .btn {
            background: var(--panel-card);
            color: var(--text);
            border: 1px solid var(--border-bright);
            padding: 10px 14px;
            font-family: inherit;
            font-size: 12px;
            font-weight: bold;
            cursor: pointer;
            border-radius: 4px;
            transition: all 0.2s;
        }
        .btn:hover {
            border-color: var(--cyan);
            color: var(--cyan);
            box-shadow: 0 0 8px rgba(0,240,255,0.2);
        }
        .btn-green {
            background: var(--green);
            color: #000;
            border: none;
        }
        .btn-green:hover {
            background: #33ff85;
            box-shadow: 0 0 12px var(--green);
            color: #000;
        }
        .btn-pink {
            background: var(--pink);
            color: #fff;
            border: none;
        }
        .btn-pink:hover {
            background: #ff3377;
            box-shadow: 0 0 12px var(--pink);
        }

        /* SLIDER */
        .slider-box {
            background: #06090f;
            border: 1px solid var(--border);
            padding: 12px;
            border-radius: 4px;
        }
        .slider {
            width: 100%;
            accent-color: var(--cyan);
            margin: 8px 0;
        }

        /* PHONE MOCKUP */
        .phone-mock {
            background: #070c14;
            border: 2px solid var(--border-bright);
            border-radius: 18px;
            padding: 12px;
            box-shadow: inset 0 0 10px rgba(0,0,0,0.8);
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .phone-notch {
            width: 60px; height: 8px;
            background: #141f2e;
            border-radius: 4px;
            margin: 0 auto;
        }

        /* SNIFFER TABLE */
        .table-box {
            background: #04060a;
            border: 1px solid var(--border);
            border-radius: 4px;
            overflow-x: auto;
            max-height: 200px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 11px;
        }
        th, td {
            border-bottom: 1px solid #141f2e;
            padding: 6px 8px;
            text-align: left;
        }
        th { background: #0c1521; color: var(--green); }
        tr:hover { background: #0c1624; }

    </style>
</head>
<body>

    <div class="header-bar">
        <div class="header-title">
            <div class="led-pulse"></div>
            LABORATORIO VISUAL // PESTILLO IOT DISTRIBUIDO (TI3042 INACAP)
        </div>
        <div style="font-size: 11px; color: var(--muted);">
            GATEWAY TCP: <span style="color:var(--green)">PORT 5050 OK</span> | HTTP: <span style="color:var(--cyan)">PORT 8090</span>
        </div>
    </div>

    <!-- FILA PRINCIPAL: 3 COLUMNAS -->
    <div class="main-grid">

        <!-- COLUMNA 1: ESP32 NODO 1 (SENSOR) Y NODO 2 (ACTUADOR) -->
        <div class="section-card">
            <div class="card-header">
                <span>1. HARDWARE EMBEBIDO ESP32 (EVALUACIÓN 1)</span>
                <span class="badge badge-green" id="badge-espnow">ESP-NOW ACTIVO</span>
            </div>

            <!-- NODO 1 SENSOR -->
            <div style="background: #080e18; border: 1px solid var(--border); padding: 12px; border-radius: 4px;">
                <div style="color: var(--cyan); font-weight: bold; margin-bottom: 6px;">
                    📡 Nodo 1: Sensor Ultrasónico HC-SR04
                </div>
                <div class="slider-box">
                    <div style="display:flex; justify-content:space-between;">
                        <span>Distancia Medida:</span>
                        <strong style="color:var(--green); font-size:15px;" id="val-distancia">45.0 cm</strong>
                    </div>
                    <input type="range" min="2" max="100" step="0.5" value="45" class="slider" id="slider-dist" oninput="actualizarDistancia(this.value)" />
                    <div style="font-size: 10.5px; color: var(--muted); display:flex; justify-content:space-between;">
                        <span>2.0 cm</span>
                        <span style="color:var(--pink)">Umbral: &lt; 8.0 cm</span>
                        <span>100 cm</span>
                    </div>
                </div>
                <div style="display: flex; gap: 6px; margin-top: 8px;">
                    <button class="btn btn-pink" style="flex:1;" onclick="setPresetDist(4.2)">🖐️ Acercar Mano (4.2cm)</button>
                    <button class="btn" style="flex:1;" onclick="setPresetDist(15.0)">🚶 Peatón (15cm)</button>
                    <button class="btn" style="flex:1;" onclick="setPresetDist(60.0)">🚪 Despejado (60cm)</button>
                </div>
            </div>

            <!-- NODO 2 ACTUADOR -->
            <div style="background: #080e18; border: 1px solid var(--border); padding: 12px; border-radius: 4px;">
                <div style="color: var(--green); font-weight: bold; margin-bottom: 6px; display:flex; justify-content:space-between;">
                    <span>⚙️ Nodo 2: Actuador Servomotor SG90</span>
                    <span id="txt-timer" style="color:var(--yellow); font-weight:bold;">0.0s</span>
                </div>

                <div class="servo-container">
                    <div class="servo-box">
                        <div class="servo-center"></div>
                        <div class="servo-arm" id="servo-horn"></div>
                    </div>
                    <div style="margin-top: 10px; font-weight: bold; font-size: 14px;" id="txt-latch-status">
                        🔒 PESTILLO BLOQUEADO (0°)
                    </div>
                </div>

                <div style="display:flex; justify-content:space-between; margin-top: 10px; align-items:center;">
                    <div>
                        <span class="led-indicator led-on-red" id="led-red"></span><small>GPIO 23 (Rojo/Lock)</small>
                    </div>
                    <div>
                        <span class="led-indicator led-off-green" id="led-green"></span><small>GPIO 22 (Verde/Open)</small>
                    </div>
                </div>
            </div>
        </div>

        <!-- COLUMNA 2: TELÉFONO 1 (NODO) Y TELÉFONO 2 (PANEL) -->
        <div class="section-card">
            <div class="card-header">
                <span>2. APLICACIÓN MÓVIL ANDROID (JETPACK COMPOSE)</span>
                <span class="badge badge-green">2 TELÉFONOS ENLAZADOS</span>
            </div>

            <!-- TELÉFONO PANEL OPERADOR -->
            <div class="phone-mock">
                <div class="phone-notch"></div>
                <div style="display:flex; justify-content:space-between; font-size:10px; color:var(--muted); border-bottom:1px solid #1a2738; padding-bottom:4px;">
                    <span>📱 Teléfono B — Panel Control</span>
                    <span style="color:var(--green)">100% 🔋</span>
                </div>

                <div style="background:#111a26; padding:8px; border-radius:4px; display:flex; justify-content:space-between; align-items:center;">
                    <div>
                        <div style="font-size:11px; font-weight:bold; color:var(--cyan);">Perfil: <span id="app-rol">OPERADOR</span></div>
                        <div style="font-size:9.5px; color:var(--muted);" id="app-email">operador@inacap.cl</div>
                    </div>
                    <button class="btn" style="padding:4px 8px; font-size:10px;" onclick="toggleRol()">Cambiar Rol</button>
                </div>

                <div style="background:#0a121e; border:1px solid var(--border); padding:12px; border-radius:6px; text-align:center;">
                    <div style="font-size:24px;" id="phone-icon-latch">🔒</div>
                    <div style="font-weight:bold; font-size:13px; margin:4px 0;" id="phone-latch-text">CERROJO BLOQUEADO</div>
                    <div style="color:var(--cyan); font-size:11.5px;">Distancia Exterior: <strong id="phone-dist-text">45.0 cm</strong></div>
                </div>

                <button class="btn btn-green" id="btn-destrabar" style="padding:14px; font-size:13px;" onclick="enviarComandoApertura()">
                    🔓 DESTRABAR PESTILLO (3s)
                </button>
                <div id="msg-rbac-warning" style="display:none; color:var(--yellow); font-size:10px; text-align:center;">
                    ⚠️ Rol Observador: Apertura deshabilitada por RBAC.
                </div>

                <div style="display:flex; gap:6px;">
                    <button class="btn" style="flex:1; font-size:11px;" onclick="enviarComandoBloqueo()">Bloqueo Manual (0°)</button>
                </div>
            </div>

            <div style="font-size:10.5px; color:var(--muted); background:#080e18; padding:8px; border-radius:4px;">
                💡 <em>Lineamiento Industrial OT:</em> Si el panel se desconecta o expira el timer, el nodo fuerza automáticamente el estado seguro a <strong>0° BLOQUEADO (Fail-Secure)</strong>.
            </div>
        </div>

        <!-- COLUMNA 3: SNIFFER Y FIRESTORE CLOUD -->
        <div class="section-card">
            <div class="card-header">
                <span>3. SNIFFER PROTOCOLO & CLOUD FIRESTORE</span>
                <span class="badge badge-yellow">AES-256-GCM + PBKDF2</span>
            </div>

            <!-- SNIFFER -->
            <div>
                <div style="color:var(--cyan); font-weight:bold; margin-bottom:6px; font-size:11.5px;">
                    🔍 Monitor de Paquetes en Tránsito (Wire Inspector)
                </div>
                <div class="table-box">
                    <table>
                        <thead>
                            <tr>
                                <th>Hora</th>
                                <th>Flujo</th>
                                <th>Trama Plana</th>
                                <th>Cifrado GCM</th>
                            </tr>
                        </thead>
                        <tbody id="sniffer-body">
                            <!-- Inyectado por JS -->
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- FIRESTORE -->
            <div>
                <div style="color:var(--green); font-weight:bold; margin-bottom:6px; font-size:11.5px;">
                    ☁️ Auditoría en Tiempo Real (/accesos_log)
                </div>
                <div class="table-box">
                    <table>
                        <thead>
                            <tr>
                                <th>Hora</th>
                                <th>Usuario (Rol)</th>
                                <th>Acción</th>
                                <th>Estado</th>
                            </tr>
                        </thead>
                        <tbody id="firestore-body">
                            <!-- Inyectado por JS -->
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

    </div>

    <script>
        function fetchState() {
            fetch('/api/state')
                .then(res => res.json())
                .then(data => {
                    // Actualizar Distancia
                    document.getElementById('val-distancia').innerText = data.distancia.toFixed(1) + ' cm';
                    document.getElementById('slider-dist').value = data.distancia;
                    document.getElementById('phone-dist-text').innerText = data.distancia.toFixed(1) + ' cm';

                    // Actualizar Servo y Latch
                    const servo = document.getElementById('servo-horn');
                    const txtLatch = document.getElementById('txt-latch-status');
                    const phoneLatch = document.getElementById('phone-latch-text');
                    const phoneIcon = document.getElementById('phone-icon-latch');
                    const ledGreen = document.getElementById('led-green');
                    const ledRed = document.getElementById('led-red');
                    const txtTimer = document.getElementById('txt-timer');

                    servo.style.transform = `rotate(${data.servo_angle}deg)`;
                    txtTimer.innerText = data.timer_countdown > 0 ? `${data.timer_countdown.toFixed(1)}s` : '0.0s';

                    if (data.latch_state === 'UNLOCKED') {
                        txtLatch.innerHTML = '<span style="color:var(--green)">🔓 PESTILLO DESTRABADO (90°)</span>';
                        phoneLatch.innerHTML = '<span style="color:var(--green)">PESTILLO ABIERTO</span>';
                        phoneIcon.innerText = '🔓';
                        ledGreen.className = 'led-indicator led-on-green';
                        ledRed.className = 'led-indicator led-off-red';
                    } else {
                        txtLatch.innerHTML = '<span style="color:var(--pink)">🔒 PESTILLO BLOQUEADO (0°)</span>';
                        phoneLatch.innerHTML = '<span style="color:var(--pink)">CERROJO BLOQUEADO</span>';
                        phoneIcon.innerText = '🔒';
                        ledGreen.className = 'led-indicator led-off-green';
                        ledRed.className = 'led-indicator led-on-red';
                    }

                    // Actualizar Roles
                    document.getElementById('app-rol').innerText = data.rol_usuario;
                    document.getElementById('app-email').innerText = data.usuario_email;
                    const btnDestrabar = document.getElementById('btn-destrabar');
                    const warningRbac = document.getElementById('msg-rbac-warning');

                    if (data.rol_usuario === 'OPERADOR') {
                        btnDestrabar.disabled = false;
                        btnDestrabar.style.opacity = '1';
                        btnDestrabar.style.cursor = 'pointer';
                        warningRbac.style.display = 'none';
                    } else {
                        btnDestrabar.disabled = true;
                        btnDestrabar.style.opacity = '0.35';
                        btnDestrabar.style.cursor = 'not-allowed';
                        warningRbac.style.display = 'block';
                    }

                    // Actualizar Sniffer
                    const sniffBody = document.getElementById('sniffer-body');
                    sniffBody.innerHTML = data.sniffer.map(s => `
                        <tr>
                            <td>${s.hora}</td>
                            <td style="color:var(--cyan)">${s.dir}</td>
                            <td style="color:var(--green)">${s.plano}</td>
                            <td style="color:var(--muted); font-size:9.5px;">${s.cifrado}</td>
                        </tr>
                    `).join('');

                    // Actualizar Firestore
                    const fireBody = document.getElementById('firestore-body');
                    fireBody.innerHTML = data.logs_firestore.map(l => `
                        <tr>
                            <td>${l.hora}</td>
                            <td>${l.email} (${l.rol})</td>
                            <td style="color:${l.estado === 'UNLOCKED' ? 'var(--green)' : 'var(--cyan)'}; font-weight:bold;">${l.accion}</td>
                            <td>${l.estado}</td>
                        </tr>
                    `).join('');
                });
        }

        function actualizarDistancia(val) {
            fetch('/api/cmd', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({cmd: 'set_distancia', valor: parseFloat(val)})
            });
        }

        function setPresetDist(val) {
            actualizarDistancia(val);
        }

        function enviarComandoApertura() {
            fetch('/api/cmd', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({cmd: 'open'})
            });
        }

        function enviarComandoBloqueo() {
            fetch('/api/cmd', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({cmd: 'lock'})
            });
        }

        function toggleRol() {
            fetch('/api/cmd', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({cmd: 'toggle_rol'})
            });
        }

        setInterval(fetchState, 200);
        fetchState();
    </script>
</body>
</html>
"""


class LabHTTPHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode("utf-8"))
        elif self.path == "/api/state":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            with lock:
                payload = json.dumps(lab_state)
            self.wfile.write(payload.encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/api/cmd":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8")
            try:
                data = json.loads(body)
                cmd = data.get("cmd")
                if cmd == "set_distancia":
                    val = float(data.get("valor", 50.0))
                    set_distancia_lab(val)
                elif cmd == "open":
                    if lab_state["rol_usuario"] == "OPERADOR":
                        cif = cifrar_trama("CMD;latch;OPEN", key_bytes)
                        add_sniffer("APP ➔ ESP32", "CMD;latch;OPEN", cif)
                        trigger_apertura("PANEL_OPERADOR")
                elif cmd == "lock":
                    cif = cifrar_trama("CMD;latch;LOCK", key_bytes)
                    add_sniffer("APP ➔ ESP32", "CMD;latch;LOCK", cif)
                    trigger_bloqueo()
                elif cmd == "toggle_rol":
                    with lock:
                        if lab_state["rol_usuario"] == "OPERADOR":
                            lab_state["rol_usuario"] = "OBSERVADOR"
                            lab_state["usuario_email"] = "observador@inacap.cl"
                        else:
                            lab_state["rol_usuario"] = "OPERADOR"
                            lab_state["usuario_email"] = "operador@inacap.cl"
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"status": "ok"}')
            except Exception as e:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(str(e).encode("utf-8"))

    def log_message(self, format, *args):
        pass  # Silenciar logs HTTP redundantes


def run_laboratory():
    # Iniciar Gateway TCP 5050 en background
    t_tcp = threading.Thread(target=sim_esp32.server_loop, daemon=True)
    t_tcp.start()

    # Iniciar HTTP Server en 8090
    socketserver.TCPServer.allow_reuse_address = True
    httpd = socketserver.TCPServer(("0.0.0.0", PORT_HTTP), LabHTTPHandler)
    print("\n" + "=" * 75)
    print(f"🚀 LABORATORIO VISUAL INTERACTIVO ONLINE: http://localhost:{PORT_HTTP}")
    print(f"📡 GATEWAY EMBEBIDO ESP32 TCP LISTO EN: 127.0.0.1:{PORT_TCP_GATEWAY}")
    print("=" * 75 + "\n")

    httpd.serve_forever()


if __name__ == "__main__":
    run_laboratory()
