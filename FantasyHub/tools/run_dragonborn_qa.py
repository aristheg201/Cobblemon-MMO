#!/usr/bin/env python3
"""Exercise authored Dragonborn wrappers and original armor in a real connected Fabric client/server.

Use a dedicated, operator test character: this changes class/level/loadout,
equips original armor, teleports the player and creates a flat test arena.
Start both server and clients with SVFRAME_RUNTIME_QA=1. No effects are simulated.
"""
import argparse
import json
import os
from pathlib import Path
import re
import time
from run_runtime_qa import command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, required=True)
    parser.add_argument("--player", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", args.player):
        parser.error("Invalid player name")
    password = os.environ.get("FANTASYHUB_QA_RCON_PASSWORD")
    if not password:
        parser.error("Set FANTASYHUB_QA_RCON_PASSWORD")
    args.output.mkdir(parents=True, exist_ok=True)
    source = args.server / "qa/dragonborn-runtime.json"

    def send(text):
        return command(args.host, args.port, password, text)

    def report():
        try:
            return json.loads(source.read_text())
        except (OSError, json.JSONDecodeError):
            return {}

    def wait(check, description):
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            data = report()
            if check(data):
                return data
            time.sleep(.25)
        raise RuntimeError("Timed out: " + description)

    send(f"gamemode survival {args.player}")
    send("fill 70 64 70 130 64 130 minecraft:stone")
    send(f"execute as {args.player} run fantasyhubqa dragonborn_prepare")
    wait(lambda d: d.get("automatic_dragon_slayer") is True, "automatic passive")
    for scenario in ("armor_legacy", "armor_helmet", "armor", "force", "fire", "sprint", "storm", "battlecry", "ethereal"):
        send(f"tp {args.player} 100.5 65 90.5 0 0")
        send(f"execute as {args.player} run fantasyhubqa dragonborn_{scenario}")
        if scenario.startswith("armor"):
            data = wait(lambda d: "no_vanilla_material_bonus" in d.get(scenario, {}), scenario)
            checks = data[scenario]
        else:
            data = wait(lambda d: d.get(scenario, {}).get("completed") is True and (scenario != "ethereal" or "damage_returns_after_expiry" in d[scenario]), scenario)
            checks = data[scenario]
        passed = all(v for v in checks.values() if isinstance(v, bool))
        if scenario.startswith("armor"):
            passed &= checks["actual_max_health"] == checks["idempotent_max_health"]
        (args.output / "dragonborn-runtime.json").write_text(json.dumps(data, indent=2))
        print(scenario + (": PASS" if passed else ": FAIL"), flush=True)
        if not passed:
            raise SystemExit(1)
        # Respect the native global cast interval even for immediate armor actions.
        time.sleep(.2)


if __name__ == "__main__":
    main()
