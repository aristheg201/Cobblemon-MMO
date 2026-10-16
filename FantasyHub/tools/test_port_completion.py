import copy
import tempfile
import unittest
from pathlib import Path
from check_port_completion import validate, gate


class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "runtime.log").write_text("actual runtime result\n")
        self.matrix = {"schema": 1, "source_inventory_reconciled": True,
                       "production_builds": "PASS", "runtime_acceptance": "PASS",
                       "requirements": [{"id": "health-reconnect", "category": "persistence",
                           "source": ["user:reconnect regression"], "required_behavior": "Actual health persists",
                           "implementation": ["PlayerData.java"], "status": "VERIFIED_COMPLETE",
                           "automated_tests": ["HealthLifecycleTest"], "runtime_qa": "PASS",
                           "evidence": ["runtime.log"], "remaining_incompatibilities": []}]}

    def test_every_release_condition_is_required(self):
        self.assertEqual([], validate(self.matrix, self.root))
        self.assertTrue(gate(self.matrix))
        for field in ("source_inventory_reconciled", "production_builds", "runtime_acceptance"):
            value = copy.deepcopy(self.matrix)
            value[field] = False
            self.assertFalse(gate(value), field)

    def test_compilation_does_not_verify_a_requirement(self):
        self.matrix["requirements"][0]["runtime_qa"] = "NOT_RUN"
        self.assertTrue(validate(self.matrix, self.root))

    def test_incomplete_and_blocked_records_prevent_release(self):
        for status in ("NOT_IMPLEMENTED", "IN_PROGRESS", "IMPLEMENTED_UNVERIFIED", "FAILED", "BLOCKED_EXTERNAL"):
            self.matrix["requirements"][0]["status"] = status
            self.assertFalse(gate(self.matrix), status)

    def test_missing_evidence_and_duplicate_ids_are_rejected(self):
        (self.root / "runtime.log").unlink()
        self.matrix["requirements"].append(copy.deepcopy(self.matrix["requirements"][0]))
        errors = validate(self.matrix, self.root)
        self.assertTrue(any("Duplicate" in e for e in errors))
        self.assertTrue(any("retained" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
