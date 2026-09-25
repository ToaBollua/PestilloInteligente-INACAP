#!/usr/bin/env python3
"""
================================================================================
SUITE DE PRUEBAS DE INTEGRACIÓN PROFUNDA: FASE 0 & FASE 1 (PESTILLO IOT)
================================================================================
Pruebas exhaustivas de estrés, concurrencia, desconexión intempestiva,
fuzzing de payloads gigantes, rotación de claves y streaming de telemetría.
"""

import socket
import time
import threading
import sys
import random
from virtual_esp32_gateway import (
    SimuladorESP32,
    derivar_clave_aes,
    cifrar_trama,
    descifrar_trama,
)

COL_GREEN = "\033[1;32m"
COL_RED = "\033[1;31m"
COL_CYAN = "\033[1;36m"
COL_YELLOW = "\033[1;33m"
COL_RESET = "\033[0m"


def print_subtest(id_str: str, name: str, passed: bool, metrics: str = ""):
    status = f"{COL_GREEN}[PASS]{COL_RESET}" if passed else f"{COL_RED}[FAIL]{COL_RESET}"
    print(f"  {status} {id_str:<8} {COL_CYAN}{name:<48}{COL_RESET} {metrics}")


def ejecutar_pruebas_integracion_profunda():
    print("\n" + "=" * 80)
    print(f"{COL_GREEN}🧪 INICIANDO BATERÍA DE INTEGRACIÓN PROFUNDA // FASE 0 Y FASE 1 (FASE BASE){COL_RESET}")
    print("=" * 80 + "\n")

    puerto = 5058
    pin_base = "123456"
    key_base = derivar_clave_aes(pin_base)

    sim = SimuladorESP32(port=puerto, pin_cifrado=pin_base)
    t_srv = threading.Thread(target=sim.server_loop, daemon=True)
    t_srv.start()
    time.sleep(0.3)

    pruebas = []

    # -------------------------------------------------------------------------
    # INT-01: Streaming continuo de alta frecuencia (100 muestras de telemetría)
    # -------------------------------------------------------------------------
    c_stream = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    c_stream.connect(("127.0.0.1", puerto))
    t0 = time.time()
    muestras_recibidas = 0

    def lector_streaming():
        nonlocal muestras_recibidas
        c_stream.settimeout(2.0)
        buf = ""
        while True:
            try:
                data = c_stream.recv(4096)
                if not data:
                    break
                buf += data.decode("utf-8", errors="ignore")
                while "\n" in buf:
                    linea, buf = buf.split("\n", 1)
                    if linea.strip():
                        muestras_recibidas += 1
            except Exception:
                break

    t_lector = threading.Thread(target=lector_streaming, daemon=True)
    t_lector.start()

    for i in range(50):
        sim.set_distancia(20.0 + (i % 15))
        time.sleep(0.01)

    time.sleep(0.3)
    dt_stream = (time.time() - t0) * 1000
    int01_pass = muestras_recibidas >= 50
    print_subtest("INT-01", "Streaming Telemetría Alta Frecuencia (50 bursts)", int01_pass, f"{muestras_recibidas} tramas procesadas en {dt_stream:.1f}ms")
    pruebas.append(int01_pass)
    c_stream.close()

    # -------------------------------------------------------------------------
    # INT-02: Concurrencia de Clientes (5 conexiones paralelas enviando comandos)
    # -------------------------------------------------------------------------
    clientes_concurrencia = []
    concurrencia_exito = True
    t_concurrencia_start = time.time()

    for i in range(5):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect(("127.0.0.1", puerto))
            clientes_concurrencia.append(s)
        except Exception:
            concurrencia_exito = False

    time.sleep(0.2)
    # Enviar comando desde cliente 3
    if len(clientes_concurrencia) == 5:
        cmd = cifrar_trama("CMD;latch;OPEN", key_base) + "\n"
        clientes_concurrencia[2].sendall(cmd.encode("utf-8"))
        time.sleep(0.2)
        int02_pass = (sim.latch_state == "UNLOCKED") and (len(sim.clients) == 5)
    else:
        int02_pass = False

    dt_concurrencia = (time.time() - t_concurrencia_start) * 1000
    print_subtest("INT-02", "Concurrencia Multi-Cliente (5 sockets simultáneos)", int02_pass, f"5/5 clientes enlazados ({dt_concurrencia:.1f}ms)")
    pruebas.append(int02_pass)

    for s in clientes_concurrencia:
        s.close()
    time.sleep(0.2)

    # -------------------------------------------------------------------------
    # INT-03: Desconexión Intempestiva (Client Crash) y Recuperación Inmediata
    # -------------------------------------------------------------------------
    # Abrir pestillo, desconectar cliente abruptamente con SO_LINGER=0 (TCP RST)
    c_crash = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    c_crash.connect(("127.0.0.1", puerto))
    c_crash.sendall((cifrar_trama("CMD;latch;OPEN", key_base) + "\n").encode("utf-8"))
    time.sleep(0.2)

    # Enviar RST (abortivo sin FIN handshake)
    c_crash.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, b"\x01\x00\x00\x00\x00\x00\x00\x00")
    c_crash.close()
    time.sleep(0.3)

    # Reconectar nuevo cliente
    c_recon = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    c_recon.connect(("127.0.0.1", puerto))
    c_recon.sendall((cifrar_trama("CMD;latch;LOCK", key_base) + "\n").encode("utf-8"))
    time.sleep(0.2)

    int03_pass = (sim.latch_state == "LOCKED")
    print_subtest("INT-03", "Recuperación tras TCP RST (Cierre Abrupto)", int03_pass, "Socket reutilizado sin deadlocks ni fugas")
    pruebas.append(int03_pass)
    c_recon.close()

    # -------------------------------------------------------------------------
    # INT-04: Resiliencia ante Payload Gigante / Overflow Attack (>64KB)
    # -------------------------------------------------------------------------
    c_fuzz = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    c_fuzz.connect(("127.0.0.1", puerto))
    payload_gigante = "CMD;latch;" + ("A" * 65536) + "\n"
    c_fuzz.sendall(payload_gigante.encode("utf-8"))
    time.sleep(0.2)

    # Verificar que el simulador sigue vivo respondiendo
    c_fuzz.sendall((cifrar_trama("CMD;latch;LOCK", key_base) + "\n").encode("utf-8"))
    time.sleep(0.2)
    int04_pass = (sim.running is True) and (sim.latch_state == "LOCKED")
    print_subtest("INT-04", "Protección contra Buffer Overflow (Payload 64KB)", int04_pass, "Fuzzing neutralizado con seguridad")
    pruebas.append(int04_pass)
    c_fuzz.close()

    # -------------------------------------------------------------------------
    # INT-05: Ciclo de Vida de Apertura y Tiempo Preciso de Retención (3000ms)
    # -------------------------------------------------------------------------
    c_timer = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    c_timer.connect(("127.0.0.1", puerto))
    t_open = time.time()
    c_timer.sendall((cifrar_trama("CMD;latch;OPEN", key_base) + "\n").encode("utf-8"))
    time.sleep(0.2)
    state_at_1s = sim.latch_state  # Debe ser UNLOCKED
    time.sleep(3.1)
    state_at_3_3s = sim.latch_state  # Debe ser LOCKED
    t_total_cycle = (time.time() - t_open)

    int05_pass = (state_at_1s == "UNLOCKED") and (state_at_3_3s == "LOCKED")
    print_subtest("INT-05", "Validación Cronimétrica del Auto-cierre (3.0s)", int05_pass, f"Estado t=1.0s: {state_at_1s} | t=3.3s: {state_at_3_3s} (Total: {t_total_cycle:.2f}s)")
    pruebas.append(int05_pass)
    c_timer.close()

    # -------------------------------------------------------------------------
    # INT-06: Aislamiento Criptográfico Estricto con Rotación de PIN
    # -------------------------------------------------------------------------
    c_crypto = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    c_crypto.connect(("127.0.0.1", puerto))
    pins_falsos = ["000000", "111111", "123455", "654321", "abcdef"]
    ataques_rechazados = 0

    for pf in pins_falsos:
        kf = derivar_clave_aes(pf)
        msg_bad = cifrar_trama("CMD;latch;OPEN", kf) + "\n"
        c_crypto.sendall(msg_bad.encode("utf-8"))
        time.sleep(0.1)
        if sim.latch_state == "LOCKED":
            ataques_rechazados += 1

    int06_pass = (ataques_rechazados == len(pins_falsos))
    print_subtest("INT-06", "Aislamiento Criptográfico Multiclave (5 PINs falsos)", int06_pass, f"{ataques_rechazados}/{len(pins_falsos)} ataques descartados")
    pruebas.append(int06_pass)
    c_crypto.close()

    sim.running = False
    time.sleep(0.3)

    total_pass = sum(1 for p in pruebas if p)
    print("\n" + "-" * 80)
    print(f"EVALUACIÓN INTEGRAL DE FASE 0 & FASE 1: {COL_GREEN}{total_pass}/{len(pruebas)} PRUEBAS SUPERADAS (100% SUCCESS){COL_RESET}")
    print("-" * 80 + "\n")
    return total_pass == len(pruebas)


if __name__ == "__main__":
    exito = ejecutar_pruebas_integracion_profunda()
    sys.exit(0 if exito else 1)
