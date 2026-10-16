#!/usr/bin/env python3
"""Package the established migration inventory for native execution; never re-audit inputs."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import zipfile
import yaml
from dragonborn_sources import append_source_files
from hunter_sources import append_source_files as append_hunter


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    hub = Path(__file__).resolve().parents[1]
    output = args.output or hub / "core/src/imported/resources/default/source-pack.zip"
    inventory = json.loads(args.inventory.read_text())
    files = {}
    origins = {}
    for row in inventory["classes"]:
        source = args.references / row["source"]
        target = "SVFrameMMO/classes/" + ("mage/mage.yml" if source.name == "mage.yml" else source.name)
        files[target] = source.read_bytes()
        origins[target] = row["source"]

    # Already implemented native DK/AMK handlers retain their original runtime;
    # the rest use the actual wrapper sources and their unchanged scaling data.
    native = {row["id"] for row in inventory["skill_wrappers"]
              if row["id"].startswith(("CLS_DEATH_KNIGHT_", "CLS_ANTI_MAGE_KNIGHT_"))}
    wrappers = {}
    for row in inventory["skill_wrappers"]:
        if row["id"] in inventory["class_skill_ids"] and row["id"] not in native:
            if row["id"] in wrappers and wrappers[row["id"]] != row["definition"]:
                raise ValueError("Conflicting class wrapper: " + row["id"])
            wrappers[row["id"]] = row["definition"]
    wrapper_file = "SVFrameLib/skill/fantasy-hub-source.yml"
    files[wrapper_file] = yaml.safe_dump(wrappers, allow_unicode=True, sort_keys=False).encode()
    origins[wrapper_file] = "Established class_skill_ids / skill_wrappers inventory"

    # Player wrappers reference the MMOCore variant of a graph. Identical
    # copies collapse; differences and the selected original source are retained.
    def priority(row):
        source = row["source"]
        return (400 if source.split(":",1)[0].endswith("dragonborn_skills_mmocore.yml") else 300 if source.startswith("amk/") else 200 if "_mmocore" in source.lower()
                else 100 if "/Skills/" in source else 50)

    selected = {}
    alternatives = {}
    for row in inventory["mythic_meta_skills"]:
        name = row["id"]
        previous = selected.get(name)
        if previous is None:
            selected[name] = row
        elif previous["definition"] != row["definition"]:
            winner, other = (row, previous) if priority(row) > priority(previous) else (previous, row)
            selected[name] = winner
            alternatives.setdefault(name, []).append(other["source"])
    meta_file = "SVFrameMobs/skills/source-meta.yml"
    files[meta_file] = yaml.safe_dump({name: row["definition"] for name, row in selected.items()}, allow_unicode=True, sort_keys=False).encode()
    origins[meta_file] = {name: {"selected": row["source"], "otherDefinitions": alternatives.get(name, [])}
                          for name, row in selected.items()}

    # Source XP requirements accompany the source class definitions. Existing
    # customized files are preserved by the server installer.
    folder = args.references / "config2/MMOCore/exp-curves"
    for source in sorted(p for p in folder.rglob("*") if p.is_file() and p.suffix in (".yml", ".yaml", ".txt")):
        target="SVFrameMMO/exp-curves/"+str(source.relative_to(folder));files[target]=source.read_bytes();origins[target]=str(source.relative_to(args.references))
    # The supplied pack contains repeated table IDs in two files. Preserve each
    # unique table once; the explicit exp-tables file overrides the template.
    tables={}
    table_sources=[]
    for name in ("default_exp_tables.yml", "exp-tables.yml"):
        source=args.references/"config2/MMOCore/exp-tables"/name
        tables.update(yaml.safe_load(source.read_text()));table_sources.append(str(source.relative_to(args.references)))
    files["SVFrameMMO/exp-tables/default.yml"]=yaml.safe_dump(tables,allow_unicode=True,sort_keys=False).encode()
    origins["SVFrameMMO/exp-tables/default.yml"]={"sources":table_sources,"priority":"exp-tables.yml overrides repeated template IDs"}
    script_source = args.references / "config2/Skript/scripts/sv_class_switch.sk"
    script = script_source.read_text()
    def function(name):
        found = re.search(r"(?ms)^function " + re.escape(name) + r"\([^\n]+\n(.*?)(?=^function |^command |\Z)", script)
        if not found:
            raise ValueError("Missing source function: " + name)
        return found.group(1)
    fee = re.search(r"class-switch-fee:\s*(\d+)", script)
    cooldown = re.search(r"class-switch-cooldown:\s*(\d+) minutes", script)
    if not fee or not cooldown:
        raise ValueError("Unsupported original class-switch options")
    selectable = [v.upper() for v in re.findall(r'if \{_id\} is "([^"\n]+)":', function("svclass_valid"))]
    supporters = re.findall(r'has permission "([^"\n]+)"', function("svclass_supporter"))
    bosses = re.findall(r'does not have permission "([^"\n]+)"', function("svclass_skyrim_complete"))
    rules = {
        "source": "config2/Skript/scripts/sv_class_switch.sk",
        "fee": int(fee.group(1)), "cooldown-seconds": int(cooldown.group(1)) * 60,
        "selectable-classes": selectable,
        "additional-native-classes": ["ANTI_MAGE_KNIGHT"],
        "permission-prefix": "svframe.class.", "hunter-permission": "svframe.class.hunter.override",
        "dragonborn-permission": "svframe.class.dragonborn",
        "supporter-permissions": supporters, "dragonborn-story-permissions": bosses,
    }
    # This is a native adapter for the approved source policy. It does not claim
    # that the .sk interpreter or unrelated supplied scripts execute yet.
    policy_file = "FantasyHub/class-switch.yml"
    files[policy_file] = yaml.safe_dump(rules, allow_unicode=True, sort_keys=False).encode()
    origins[policy_file] = rules["source"]
    mastery=args.references/"config2/MMOCore/skill-trees/class-mastery.yml"
    files["SVFrameMMO/skill-trees/class-mastery.yml"]=mastery.read_bytes();origins["SVFrameMMO/skill-trees/class-mastery.yml"]=str(mastery.relative_to(args.references))
    append_source_files(inventory,args.references,files,origins)
    append_hunter(inventory,args.references,files,origins)
    manifest = {"schema": 1, "revision": "source-graphs-1", "files": [
        {"path": name, "sha256": hashlib.sha256(content).hexdigest()} for name, content in files.items()
    ]}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        archive.writestr("source-choices.json", json.dumps(origins, ensure_ascii=False, indent=2))
        for name, content in files.items():
            archive.writestr(name, content)
    print("Packaged established source inventory:", len(inventory["classes"]), "classes,",
          len(wrappers), "non-native class wrappers,", len(selected), "meta definitions.")


if __name__ == "__main__":
    main()
