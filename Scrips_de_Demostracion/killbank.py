#!/usr/bin/env python3
"""
killbank.py - Da de baja (o revive) un banco para la demo de tolerancia a fallos.

Uso:
    python killbank.py --A            # tumba el Banco A (docker stop)
    python killbank.py --B            # tumba el Banco B
    python killbank.py --A --C        # tumba A y C a la vez
    python killbank.py --A --up       # vuelve a levantar el Banco A (docker start)
    python killbank.py --status       # muestra el estado de los 3 bancos

Al tumbar un banco con "docker stop" el contenedor NO se borra: conserva su
volumen de datos, así que --up lo revive con las mismas cuentas. Ideal para
mostrar en vivo que los otros dos bancos siguen operando.
"""
import argparse
import subprocess
import sys
import urllib.request

# Prefijo -> (nombre de contenedor, puerto expuesto)
BANCOS = {
    "A": ("banco-a", 8081),
    "B": ("banco-b", 8082),
    "C": ("banco-c", 8083),
}


def docker(*args):
    """Ejecuta un comando docker y devuelve (ok, salida)."""
    try:
        r = subprocess.run(
            ["docker", *args], capture_output=True, text=True, timeout=30
        )
        return r.returncode == 0, (r.stdout + r.stderr).strip()
    except FileNotFoundError:
        print("ERROR: no se encontro 'docker'. Instala Docker Desktop o abre otra terminal.")
        sys.exit(1)
    except subprocess.TimeoutExpired:
        return False, "docker no respondio (timeout)"


def esta_corriendo(contenedor):
    ok, salida = docker("ps", "--filter", f"name=^{contenedor}$", "--format", "{{.Names}}")
    return ok and contenedor in salida.splitlines()


def esta_sano(puerto):
    """Consulta /actuator/health directo al puerto del banco."""
    try:
        with urllib.request.urlopen(f"http://localhost:{puerto}/actuator/health", timeout=3) as resp:
            return b"UP" in resp.read()
    except Exception:
        return False


def mostrar_estado():
    print("\n  Estado de los bancos")
    print("  --------------------")
    for pref, (cont, puerto) in BANCOS.items():
        if not esta_corriendo(cont):
            estado = "CAIDO   (contenedor detenido)"
        elif esta_sano(puerto):
            estado = "ARRIBA  (health UP)"
        else:
            estado = "iniciando/no responde"
        print(f"  Banco {pref}  [{cont}:{puerto}]  ->  {estado}")
    print()


def main():
    p = argparse.ArgumentParser(
        description="Da de baja o revive un banco para la demo de tolerancia a fallos.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Ejemplos:\n"
        "  python killbank.py --A\n"
        "  python killbank.py --A --up\n"
        "  python killbank.py --status",
    )
    p.add_argument("--A", action="store_true", help="seleccionar Banco A")
    p.add_argument("--B", action="store_true", help="seleccionar Banco B")
    p.add_argument("--C", action="store_true", help="seleccionar Banco C")
    p.add_argument("--up", action="store_true", help="levantar el/los banco(s) en vez de tumbarlos")
    p.add_argument("--status", action="store_true", help="solo mostrar el estado y salir")
    args = p.parse_args()

    if args.status:
        mostrar_estado()
        return

    seleccion = [pref for pref in ("A", "B", "C") if getattr(args, pref)]
    if not seleccion:
        p.print_help()
        print("\nElige al menos un banco: --A, --B o --C")
        sys.exit(2)

    accion, verbo = ("start", "Levantando") if args.up else ("stop", "Tumbando")
    for pref in seleccion:
        cont, _ = BANCOS[pref]
        print(f"{verbo} Banco {pref} ({cont})...", end=" ", flush=True)
        ok, salida = docker(accion, cont)
        print("OK" if ok else f"FALLO -> {salida}")

    mostrar_estado()


if __name__ == "__main__":
    main()
