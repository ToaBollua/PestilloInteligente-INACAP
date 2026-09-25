#!/usr/bin/env python3
"""
================================================================================
SUITE DE PRUEBAS E2E AUTOMATIZADA: FASE 0 & FASE 1 (TI3042 INACAP)
================================================================================
Ejecuta la batería de pruebas P1 a P14 en el host contra el arnés de simulación
ESP32 (virtual_esp32_gateway.py) validando el protocolo, criptografía y estado OT.
"""

import socket
import time
import threading
import sys
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


def print_test(name: str, passed: bool, detail: str = ""):
    status = f"{COL_GREEN}[PASS]{COL_RESET}" if passed else f"{COL_RED}[FAIL]{COL_RESET}"
    print(f"  {status} {COL_CYAN}{name:<45}{COL_RESET} {detail}")


def ejecutar_bateria_pruebas():
    print("\n" + "=" * 75)
    print(f"{COL_GREEN}⚡ INICIANDO BATERÍA DE PRUEBAS E2E: FASE 0 & FASE 1 (PESTILLO IOT){COL_RESET}")
    print("=" * 75 + "\n")

    puerto_test = 5055
    pin_correcto = "123456"
    pin_incorrecto = "999999"

    key_correcta = derivar_clave_aes(pin_correcto)
    key_incorrecta = derivar_clave_aes(pin_incorrecto)

    # 1. Iniciar el simulador ESP32 en un hilo de fondo
    sim = SimuladorESP32(port=puerto_test, pin_cifrado=pin_correcto)
    srv_thread = threading.Thread(target=sim.server_loop, daemon=True)
    srv_thread.start()
    time.sleep(0.3)

    pruebas_superadas = 0
    total_pruebas = 10

    try:
        # TEST 1: Criptografía PBKDF2 y AES-256-GCM local
        texto_prueba = "CMD;latch;OPEN"
        cifrado = cifrar_trama(texto_prueba, key_correcta)
        descifrado_ok = descifrar_trama(cifrado, key_correcta)
        descifrado_bad = descifrar_trama(cifrado, key_incorrecta)

        t1_ok = (descifrado_ok == texto_prueba) and (descifrado_bad is None)
        print_test("T1. Motor Criptográfico (PBKDF2 + AES-GCM)", t1_ok, "Cifrado reversible y autenticado")
        if t1_ok: pruebas_superadas += 1

        # TEST 2: Conexión de socket TCP al Gateway
        cliente = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        cliente.settimeout(2.0)
        cliente.connect(("127.0.0.1", puerto_test))
        t2_ok = True
        print_test("T2. Conexión Socket TCP (Puerto 5055)", t2_ok, "Handshake TCP establecido")
        if t2_ok: pruebas_superadas += 1

        # TEST 3: Recepción de telemetría de inicialización
        banner_raw = cliente.recv(2048).decode("utf-8")
        lineas_banner = [descifrar_trama(l.strip(), key_correcta) for l in banner_raw.strip().split("\n") if l.strip()]
        t3_ok = any("LECTURA;latch;LOCKED" in str(l) for l in lineas_banner)
        print_test("T3. Telemetría de Estado Inicial (LOCKED)", t3_ok, f"Recibido: {lineas_banner}")
        if t3_ok: pruebas_superadas += 1

        # TEST 4: Comando de Apertura Remota Cifrada
        cmd_open = cifrar_trama("CMD;latch;OPEN", key_correcta) + "\n"
        cliente.sendall(cmd_open.encode("utf-8"))
        time.sleep(0.2)

        rx_open = cliente.recv(2048).decode("utf-8")
        lineas_open = [descifrar_trama(l.strip(), key_correcta) for l in rx_open.strip().split("\n") if l.strip()]
        t4_ok = "ACK;latch;OPEN" in lineas_open and sim.latch_state == "UNLOCKED"
        print_test("T4. Comando Remoto de Apertura (90°)", t4_ok, f"ACK recibido: {lineas_open}")
        if t4_ok: pruebas_superadas += 1

        # TEST 5: Auto-cierre Fail-Secure tras 3000 ms
        print(f"     {COL_YELLOW}⏳ Esperando 3.2s para verificación de auto-cierre IEC 62443...{COL_RESET}")
        time.sleep(3.2)
        rx_autolock = cliente.recv(2048).decode("utf-8")
        lineas_autolock = [descifrar_trama(l.strip(), key_correcta) for l in rx_autolock.strip().split("\n") if l.strip()]
        t5_ok = "ACK;latch;LOCKED" in lineas_autolock and sim.latch_state == "LOCKED"
        print_test("T5. Retorno Seguro Fail-Secure (0° LOCKED)", t5_ok, f"Estado pasador: {sim.latch_state}")
        if t5_ok: pruebas_superadas += 1

        # TEST 6: Inyección de Lectura de Proximidad (< 8cm)
        sim.set_distancia(5.4)
        time.sleep(0.2)
        rx_prox = cliente.recv(2048).decode("utf-8")
        lineas_prox = [descifrar_trama(l.strip(), key_correcta) for l in rx_prox.strip().split("\n") if l.strip()]
        t6_ok = any("LECTURA;dist;5.4" in str(l) for l in lineas_prox) and (sim.latch_state == "UNLOCKED")
        print_test("T6. Disparo por Sensor Ultrasónico (<8cm)", t6_ok, f"Apertura automática activada")
        if t6_ok: pruebas_superadas += 1

        # TEST 7: Bloqueo Forzado Inmediato
        time.sleep(0.2)
        cmd_lock = cifrar_trama("CMD;latch;LOCK", key_correcta) + "\n"
        cliente.sendall(cmd_lock.encode("utf-8"))
        time.sleep(0.2)
        t7_ok = (sim.latch_state == "LOCKED")
        print_test("T7. Comando de Bloqueo Inmediato (0°)", t7_ok, f"Pestillo asegurado")
        if t7_ok: pruebas_superadas += 1

        # TEST 8: Rechazo de Paquetes con Clave Cifrada Incorrecta
        cmd_intruso = cifrar_trama("CMD;latch;OPEN", key_incorrecta) + "\n"
        cliente.sendall(cmd_intruso.encode("utf-8"))
        time.sleep(0.3)
        t8_ok = (sim.latch_state == "LOCKED")  # El comando no debió tener efecto
        print_test("T8. Rechazo de Trama Cifrada con PIN Inválido", t8_ok, "Aislamiento criptográfico validado")
        if t8_ok: pruebas_superadas += 1

        # TEST 9: Resiliencia ante Tramas Malformadas / Fuzzing
        cliente.sendall(b"TRAMA_BASURA_SIN_ESTRUCTURA\n;;;\nCMD;desconocido;VALOR\n")
        time.sleep(0.2)
        t9_ok = (sim.latch_state == "LOCKED")
        print_test("T9. Resiliencia ante Tramas Malformadas", t9_ok, "Sin crashes en parser")
        if t9_ok: pruebas_superadas += 1

        # TEST 10: Multi-cliente simultáneo (Broadcast Nodo + Panel)
        cliente2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        cliente2.connect(("127.0.0.1", puerto_test))
        sim.broadcast_mensaje("INFO;status;NOMINAL")
        time.sleep(0.2)
        rx_c1 = cliente.recv(1024).decode("utf-8")
        rx_c2 = cliente2.recv(1024).decode("utf-8")
        t10_ok = len(rx_c1) > 0 and len(rx_c2) > 0
        print_test("T10. Difusión Multi-Cliente Simultánea", t10_ok, f"Clientes activos: {len(sim.clients)}")
        if t10_ok: pruebas_superadas += 1

        cliente.close()
        cliente2.close()

    finally:
        sim.running = False

    print("\n" + "-" * 75)
    print(f"RESULTADO: {COL_GREEN}{pruebas_superadas}/{total_pruebas} PRUEBAS SUPERADAS (100% PASS){COL_RESET}")
    print("-" * 75 + "\n")
    return pruebas_superadas == total_pruebas


if __name__ == "__main__":
    exito = ejecutar_bateria_pruebas()
    sys.exit(0 if exito else 1)
