import json
import pathlib
import tempfile
import unittest
from audit_fantasy_hub import audit, yaml_records


class SourceAuditTest(unittest.TestCase):
    def test_all_classes_and_missing_skill_are_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp);classes=root/'pack/MMOCore/classes';classes.mkdir(parents=True)
            (classes/'bard.yml').write_text('display: {name: Bard}\nskills: {SONG: {level: 2}}\nattributes: {max-health: {base: 20, per-level: 1}}\n')
            (classes/'mage.yml').write_text('skills: {FIRE: {level: 5}}\n')
            manifest=audit(root)
            self.assertEqual(2,manifest['counts']['unique_classes'])
            self.assertEqual({'SONG','FIRE'},set(manifest['class_skill_ids']))
            self.assertEqual(2,len(manifest['blockers']['missing_class_wrappers']))
            self.assertEqual(1,manifest['classes'][0]['attributes']['max-health']['per-level'])

    def test_inline_and_dynamic_meta_skills_are_not_missing_static_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp);p=root/'pack/MythicMobs/Skills';p.mkdir(parents=True)
            (p/'graph.yml').write_text('CAST:\n  Skills:\n  - skill{s=[ - damage{a=2} ]}\n  - skill{s=<caster.var.skill>}\n  - skill{s=MISSING}\n')
            m=audit(root)
            self.assertEqual(['MISSING'],[r['missing_meta_skill'] for r in m['blockers']['missing_mythic_references']])

    def test_scripts_disabled_in_source_are_not_counted_as_active_or_executed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp);p=root/'pack/Skript/scripts';(p/'-examples').mkdir(parents=True)
            (p/'live.sk').write_text('on join:\n    set {player::level} to 3\n')
            (p/'-examples/demo.sk').write_text('on join:\n    stop\n')
            m=audit(root)
            self.assertEqual(1,m['counts']['enabled_scripts'])
            self.assertTrue(all(not s['executed_on_fabric'] for s in m['scripts']))

    def test_conflicting_yaml_keys_and_parse_errors_are_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp);p=root/'test.yml';p.write_text('SKILL: {Skills: []}\nSKILL: {Cooldown: 4}\n')
            errors,duplicates=[],[]
            records=yaml_records([p],root,errors,duplicates)
            self.assertEqual(2,duplicates[0]['occurrences'])
            self.assertEqual(4,records[0]['definition']['Cooldown'])
            p.write_text('bad: [\n')
            yaml_records([p],root,errors,duplicates)
            self.assertEqual(1,len(errors))


if __name__=='__main__':unittest.main()
