#!/usr/bin/env python3
"""
================================================================================
ARNES DE SIMULACION HOST: ESP32 GATEWAY & PESTILLO IOT (TI3042 INACAP)
================================================================================
Simula el clúster embebido de 2 placas ESP32 (Nodo Sensor HC-SR04 y Nodo Actuador SG90)
y expone un servidor TCP en el puerto 5050 (compatible con WifiEnlace de la app Android).

Características:
- Soporta protocolo estructurado: TIPO;CLAVE;VALOR
- Soporta cifrado simétrico AES-256-GCM con derivación PBKDF2 (código de 6 dígitos)
- Simula interrupción ESP-NOW entre Sensor y Actuador
- Máquina de estados Fail-Secure (0° Bloqueado, 90° Abierto por 3s)
- Consola interactiva para inyectar telemetría y eventos de hardware en vivo.
"""

import socket
import threading
import time
import base64
import sys
import os
import hashlib
from typing import Optional

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import hashes
    HAVE_CRYPTO = True
except ImportError:
    HAVE_CRYPTO = False


SALT_PRECOMPARTIDO = b"Inacap_Pestillo_IoT_2026_Salt"


def derivar_clave_aes(codigo_pin: str) -> bytes:
    if not HAVE_CRYPTO:
        return b""
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=SALT_PRECOMPARTIDO,
        iterations=10000,
    )
    return kdf.derive(codigo_pin.encode("utf-8"))


def cifrar_trama(texto_plano: str, key_bytes: bytes) -> str:
    if not HAVE_CRYPTO or not key_bytes:
        return texto_plano
    aesgcm = AESGCM(key_bytes)
    iv = os.urandom(12)
    ciphertext = aesgcm.encrypt(iv, texto_plano.encode("utf-8"), None)
    payload = iv + ciphertext
    return base64.b64encode(payload).decode("utf-8")


def descifrar_trama(trama_b64: str, key_bytes: bytes) -> Optional[str]:
    if not HAVE_CRYPTO or not key_bytes:
        return trama_b64
    try:
        data = base64.b64decode(trama_b64)
        if len(data) < 28:
            return None
        iv = data[:12]
        ciphertext = data[12:]
        aesgcm = AESGCM(key_bytes)
        decrypted = aesgcm.decrypt(iv, ciphertext, None)
        return decrypted.decode("utf-8")
    except Exception:
        return None


class SimuladorESP32:
    def __init__(self, host: str = "0.0.0.0", port: int = 5050, pin_cifrado: str = "123456"):
        self.host = host
        self.port = port
        self.pin = pin_cifrado
        self.key_bytes = derivar_clave_aes(pin_cifrado)

        self.distancia_actual = 50.0  # cm
        self.latch_state = "LOCKED"  # LOCKED (0°) o UNLOCKED (90°)
        self.latch_angle = 0         # 0° a 90°

        self.running = True
        self.clients = []
        self.lock = threading.Lock()

    def log(self, tag: str, msg: str):
        ts = time.strftime("%H:%M:%S")
        print(f"[{ts}] [\033[1;32m{tag}\033[0m] {msg}")

    def accionar_apertura(self, fuente: str = "ESP-NOW_SENSOR"):
        with self.lock:
            if self.latch_state == "UNLOCKED":
                return
            self.latch_state = "UNLOCKED"
            self.latch_angle = 90

        self.log("ACTUADOR_SG90", f"🚨 RETRAYENDO PESTILLO (90°) - Fuente: {fuente}")
        self.broadcast_mensaje("ACK;latch;OPEN")
        self.broadcast_mensaje("LECTURA;latch;UNLOCKED")

        # Temporizador no bloqueante de auto-bloqueo (3000 ms)
        threading.Thread(target=self._temporizador_autocierre, daemon=True).start()

    def _temporizador_autocierre(self):
        time.sleep(3.0)
        with self.lock:
            self.latch_state = "LOCKED"
            self.latch_angle = 0
        self.log("ACTUADOR_SG90", "🔒 PROYECTANDO PESTILLO (0°) - Auto-cierre IEC 62443 Fail-Secure")
        self.broadcast_mensaje("ACK;latch;LOCKED")
        self.broadcast_mensaje("LECTURA;latch;LOCKED")

    def set_distancia(self, d: float):
        self.distancia_actual = max(2.0, min(d, 400.0))
        self.log("SENSOR_HCSR04", f"Lectura de distancia: {self.distancia_actual:.1f} cm")
        self.broadcast_mensaje(f"LECTURA;dist;{self.distancia_actual:.1f}")

        if self.distancia_actual < 8.0 and self.latch_state == "LOCKED":
            self.log("ESP-NOW_BUS", "⚡ Disparo de Proximidad (< 8cm) -> Paquete transmitido al Actuador")
            self.accionar_apertura("SENSOR_PROXIMIDAD_HCSR04")

    def broadcast_mensaje(self, linea_plana: str):
        payload = cifrar_trama(linea_plana, self.key_bytes) + "\n"
        with self.lock:
            desconectados = []
            for c in self.clients:
                try:
                    c.sendall(payload.encode("utf-8"))
                except Exception:
                    desconectados.append(c)
            for d in desconectados:
                self.clients.remove(d)

    def handle_client(self, conn: socket.socket, addr):
        self.log("GATEWAY_TCP", f"📱 Nuevo cliente conectado desde {addr}")
        with self.lock:
            self.clients.append(conn)

        # Enviar estado inicial
        try:
            conn.sendall((cifrar_trama(f"LECTURA;latch;{self.latch_state}", self.key_bytes) + "\n").encode("utf-8"))
            conn.sendall((cifrar_trama(f"LECTURA;dist;{self.distancia_actual:.1f}", self.key_bytes) + "\n").encode("utf-8"))
        except Exception:
            pass

        buffer = ""
        try:
            while self.running:
                data = conn.recv(1024)
                if not data:
                    break
                buffer += data.decode("utf-8", errors="ignore")
                while "\n" in buffer:
                    linea, buffer = buffer.split("\n", 1)
                    linea = linea.strip()
                    if not linea:
                        continue

                    # Descifrar si aplica
                    plana = descifrar_trama(linea, self.key_bytes)
                    if not plana:
                        # Si no pudo descifrar con key, probar si viene en plano
                        plana = linea

                    self.log("CLIENTE_RX", f"Trama recibida: {plana}")
                    self.procesar_comando(plana)

        except Exception as e:
            self.log("GATEWAY_TCP", f"Desconexión de cliente {addr}: {e}")
        finally:
            with self.lock:
                if conn in self.clients:
                    self.clients.remove(conn)
            conn.close()
            self.log("GATEWAY_TCP", f"Conexión cerrada para {addr}")

    def procesar_comando(self, linea: str):
        partes = linea.split(";")
        if len(partes) < 3:
            return
        tipo, clave, valor = partes[0].upper(), partes[1], partes[2]

        if tipo == "CMD" and clave == "latch":
            if valor == "OPEN":
                self.accionar_apertura("COMANDO_REMOTO_ANDROID")
            elif valor == "LOCK":
                with self.lock:
                    self.latch_state = "LOCKED"
                    self.latch_angle = 0
                self.log("ACTUADOR_SG90", "🔒 Bloqueo forzado por comando remoto")
                self.broadcast_mensaje("ACK;latch;LOCKED")
                self.broadcast_mensaje("LECTURA;latch;LOCKED")

    def server_loop(self):
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((self.host, self.port))
        srv.listen(5)
        self.log("GATEWAY_TCP", f"🚀 Servidor TCP ESP32 escuchando en {self.host}:{self.port}")

        while self.running:
            try:
                srv.settimeout(1.0)
                conn, addr = srv.accept()
                threading.Thread(target=self.handle_client, args=(conn, addr), daemon=True).start()
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    self.log("GATEWAY_TCP", f"Error en socket accept: {e}")
                break
        srv.close()

    def run_interactive(self):
        t = threading.Thread(target=self.server_loop, daemon=True)
        t.start()

        print("\n" + "=" * 65)
        print("  CONSOLA DE CONTROL DE HARNESS ESP32 (TI3042 INACAP)")
        print("=" * 65)
        print("Comandos disponibles:")
        print("  dist <cm>   : Simular distancia del sensor ultrasónico (ej: dist 4.2)")
        print("  open        : Forzar evento de apertura física (90°)")
        print("  lock        : Forzar estado seguro de bloqueo (0°)")
        print("  status      : Mostrar telemetría y clientes conectados")
        print("  quit / exit : Detener el arnés")
        print("=" * 65 + "\n")

        while self.running:
            try:
                cmd = input("esp32-harness> ").strip()
                if not cmd:
                    continue
                if cmd in ["quit", "exit", "q"]:
                    self.running = False
                    break
                elif cmd == "open":
                    self.accionar_apertura("CONSOLA_HOST")
                elif cmd == "lock":
                    with self.lock:
                        self.latch_state = "LOCKED"
                        self.latch_angle = 0
                    self.broadcast_mensaje("ACK;latch;LOCKED")
                    self.broadcast_mensaje("LECTURA;latch;LOCKED")
                    self.log("ACTUADOR_SG90", "🔒 Bloqueado por consola")
                elif cmd.startswith("dist"):
                    partes = cmd.split()
                    if len(partes) > 1:
                        try:
                            val = float(partes[1])
                            self.set_distancia(val)
                        except ValueError:
                            print("Valor numérico inválido.")
                    else:
                        print("Uso: dist <centimetros>")
                elif cmd == "status":
                    print(f"Estado Cerrojo: {self.latch_state} ({self.latch_angle}°)")
                    print(f"Distancia: {self.distancia_actual:.1f} cm")
                    print(f"Clientes Conectados: {len(self.clients)}")
                    print(f"Cifrado AES-256-GCM: {'ACTIVO (PIN ' + self.pin + ')' if HAVE_CRYPTO else 'DESACTIVADO (sin python-cryptography)'}")
                else:
                    print("Comando no reconocido. Prueba con: dist <cm>, open, lock, status, exit")
            except (KeyboardInterrupt, EOFError):
                self.running = False
                break


if __name__ == "__main__":
    puerto = 5050
    pin = "123456"
    if len(sys.argv) > 1:
        try:
            puerto = int(sys.argv[1])
        except ValueError:
            pass
    if len(sys.argv) > 2:
        pin = sys.argv[2]

    sim = SimuladorESP32(port=puerto, pin_cifrado=pin)
    sim.run_interactive()
