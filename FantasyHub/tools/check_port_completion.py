#!/usr/bin/env python3
"""Validate source-derived coverage and refuse a release without runtime evidence."""
import argparse
import collections
import json
from pathlib import Path

STATUSES = {"NOT_IMPLEMENTED", "IN_PROGRESS", "IMPLEMENTED_UNVERIFIED", "FAILED",
            "VERIFIED_COMPLETE", "BLOCKED_EXTERNAL"}
FIELDS = {"id", "category", "source", "required_behavior", "implementation", "status",
          "automated_tests", "runtime_qa", "evidence", "remaining_incompatibilities"}


def validate(matrix, root):
    errors, seen = [], set()
    if matrix.get("schema") != 1:
        errors.append("Unsupported matrix schema")
    entries = matrix.get("requirements", [])
    if not entries:
        errors.append("Empty requirements inventory")
    for row in entries:
        key = row.get("id", "<missing>")
        if key in seen:
            errors.append(f"Duplicate requirement: {key}")
        seen.add(key)
        missing = FIELDS - row.keys()
        if missing:
            errors.append(f"{key}: missing {sorted(missing)}")
        if row.get("status") not in STATUSES:
            errors.append(f"{key}: invalid status")
        if not row.get("source") or not row.get("required_behavior"):
            errors.append(f"{key}: no source or behavioral requirement")
        if row.get("status") == "VERIFIED_COMPLETE":
            if (not row.get("implementation") or not row.get("automated_tests")
                    or row.get("runtime_qa") != "PASS" or not row.get("evidence")
                    or row.get("remaining_incompatibilities")):
                errors.append(f"{key}: completion requires implementation, tests, runtime PASS, evidence and zero incompatibilities")
            for location in row.get("evidence", []):
                path = Path(location)
                if path.is_absolute() or ".." in path.parts or not (root / path).is_file():
                    errors.append(f"{key}: evidence must be a retained repository file: {location}")
    return errors


def gate(matrix):
    counts = collections.Counter(r["status"] for r in matrix.get("requirements", []))
    return (bool(counts) and matrix.get("source_inventory_reconciled") is True
            and matrix.get("production_builds") == "PASS"
            and matrix.get("runtime_acceptance") == "PASS"
            and set(counts) == {"VERIFIED_COMPLETE"})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=Path, default=Path(__file__).resolve().parents[2] / "docs/PORT_COMPLETION_MATRIX.json")
    parser.add_argument("--validate-only", action="store_true", help="Validate records without authorizing release")
    args = parser.parse_args()
    matrix = json.loads(args.matrix.read_text())
    errors = validate(matrix, args.matrix.parent.parent)
    for error in errors:
        print(error)
    counts = dict(sorted(collections.Counter(r["status"] for r in matrix["requirements"]).items()))
    print(json.dumps({"records": len(matrix["requirements"]), "statuses": counts,
                      "release_gate": "PASS" if not errors and gate(matrix) else "FAIL"}))
    return 1 if errors or (not args.validate_only and not gate(matrix)) else 0


if __name__ == "__main__":
    raise SystemExit(main())
