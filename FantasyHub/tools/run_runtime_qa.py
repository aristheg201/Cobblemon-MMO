#!/usr/bin/env python3
"""Run opt-in native scenarios against a real local Fabric server and connected player.

Requires SVFRAME_RUNTIME_QA=1 on server/client and administrator permissions.
These scenarios teleport players and spawn temporary entities. --shop also spends
the configured real currency; use a funded test account and the documented arena.
No gameplay is simulated here: reports are emitted by the running mod.
"""
import argparse
import json
import os
from pathlib import Path
import re
import socket
import struct
import time


def receive_exact(connection, size):
    data = bytearray()
    while len(data) < size:
        chunk = connection.recv(size - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data.extend(chunk)
    return bytes(data)


def receive(connection):
    size, = struct.unpack("<i", receive_exact(connection, 4))
    if not 10 <= size <= 1024 * 1024:
        raise ConnectionError("Invalid RCON response size")
    packet = receive_exact(connection, size)
    request, kind = struct.unpack("<ii", packet[:8])
    return request, kind, packet[8:-2].decode("utf-8", errors="replace")


def send(connection, request, kind, text):
    payload = struct.pack("<ii", request, kind) + text.encode() + b"\0\0"
    connection.sendall(struct.pack("<i", len(payload)) + payload)


def command(host, port, password, text):
    with socket.create_connection((host, port), timeout=10) as connection:
        send(connection, 1, 3, password)
        for _ in range(3):
            request, kind, _ = receive(connection)
            if request == -1:
                raise PermissionError("RCON authentication rejected")
            if kind == 2:
                break
        else:
            raise ConnectionError("No RCON authentication response")
        send(connection, 2, 2, text)
        # Minecraft fragments long responses into multiple packets with the
        # same request ID. A second, empty command provides an ordered boundary
        # so a large catalog is read completely without waiting for a timeout.
        send(connection, 3, 2, "")
        responses = []
        for _ in range(64):
            request, _, response = receive(connection)
            if request == 3:
                return "".join(responses)
            if request != 2:
                raise ConnectionError("Unexpected RCON command response")
            responses.append(response)
        raise ConnectionError("RCON response exceeded the packet limit")


def checks_pass(report, name):
    if name == "shop":
        return report.get("finished") is True and report.get("count") == 15 and len(report.get("offers", [])) == 15 and report.get("unsupported_not_sold") is True and all(
            all(row.get(key) is True for key in ("purchase_correct", "duplicate_no_charge", "equipped", "castSuccessful", "actual_effect", "cooldown_rejects_repeat"))
            for row in report.get("offers", [])
        )
    # formalBattleStarted is observational metadata; the formal_battle_api_started
    # check independently verifies the real battle and is included in the count.
    checks = [value for key, value in report.items() if isinstance(value, bool) and key != "formalBattleStarted"]
    expected = {"death_sentence": 11, "reference_adapters": 21, "pokemon": 17, "catalog": 10}[name]
    return len(checks) == expected and all(checks)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, required=True, help="Server working directory")
    parser.add_argument("--player", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--shop", action="store_true", help="Also perform real configured purchases")
    parser.add_argument("--catalog", action="store_true", help="Test DK catalog/slot/TIMER behavior; requires a high-level DK with purchased Cut")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", args.player):
        parser.error("Invalid Minecraft player name")
    password = os.environ.get("FANTASYHUB_QA_RCON_PASSWORD")
    if not password:
        parser.error("Set FANTASYHUB_QA_RCON_PASSWORD; it is never written to the report")
    scenarios = [
        ("death_sentence", "svframelib deathsentenceqa", "death-sentence-runtime.json", 30),
        ("reference_adapters", "svframelib referenceqa", "reference-classes-runtime.json", 40),
        ("pokemon", "cobblemonentityqa", "cobblemon-entity-runtime.json", 60),
    ]
    if args.shop:
        scenarios.append(("shop", "cobblemonshopqa", "cobblemon-shop-runtime.json", 180))
    if args.catalog:
        scenarios.append(("catalog", "fantasyhubqa catalog", "skill-catalog-runtime.json", 30))
    args.output.mkdir(parents=True, exist_ok=True)
    results = {}
    for name, action, filename, timeout in scenarios:
        source = args.server / "qa" / filename
        previous = source.stat().st_mtime_ns if source.exists() else -1
        response = command(args.host, args.port, password, f"execute as {args.player} run {action}")
        deadline = time.monotonic() + timeout
        report = None
        passed = False
        while time.monotonic() < deadline:
            if source.exists() and source.stat().st_mtime_ns != previous:
                try:
                    report = json.loads(source.read_text())
                except json.JSONDecodeError:
                    time.sleep(.25)
                    continue
                passed = checks_pass(report, name)
                if name != "shop" or report.get("finished") or report.get("failure"):
                    break
            time.sleep(.5)
        results[name] = {"passed": passed, "response": response, "report": report}
        (args.output / "native-runtime-results.json").write_text(json.dumps(results, indent=2))
        print(f"{name}: {'PASS' if passed else 'FAIL'}", flush=True)
        if not passed:
            raise SystemExit(1)


if __name__ == "__main__":
    main()
