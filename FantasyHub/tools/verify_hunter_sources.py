#!/usr/bin/env python3
"""Check generated Hunter gear against original numeric/policy/asset source data."""
import argparse,hashlib,json,zipfile
from pathlib import Path
from hunter_sources import BASE_IDS,COLLECTIONS,read_yaml

def main():
    p=argparse.ArgumentParser();p.add_argument('--references',type=Path,required=True);a=p.parse_args()
    hub=Path(__file__).resolve().parents[1];source=read_yaml((a.references/'config2/MMOItems/item/armor.yml').read_text())
    with zipfile.ZipFile(hub/'core/src/imported/resources/default/source-pack.zip') as z:
        items=read_yaml(z.read('SVFrameItems/items/hunter.yml'));rules=read_yaml(z.read('SVFrameItems/rules/hunter.yml'));sets=read_yaml(z.read('SVFrameItems/source/sets.yml'))
    assert set(items)==set(BASE_IDS), 'Original base gear selection changed'
    for key in BASE_IDS:
        base=source[key]['base'];native=items[key];policy=rules[key]
        for stat in ['armor','armor-toughness','max-health','skill-damage','weapon-damage','attack-speed','cooldown-reduction','damage-reduction','pve-damage-reduction','pvp-damage-reduction','knockback-resistance']:
            assert (stat in base)==(stat in native['stats']), (key,stat)
            if stat in base:assert native['stats'][stat]['value']==base[stat], (key,stat)
        assert 'upgrade-template' not in native and 'upgrade-stats' not in policy
        for flag in ['unbreakable','disable-enchanting','disable-droping','disable-death-drop','disable-crafting','disable-smelting','disable-smithing','disable-repairing']:
            assert policy.get(flag)==base.get(flag),(key,flag)
    original_sets=read_yaml((a.references/'config2/MMOItems/item-sets.yml').read_text())
    assert sets['SVFRAME_SET_HUNTER']['bonuses']==original_sets['SVFRAME_SET_HUNTER']['bonuses']
    root=hub/'client/src/imported/resources';models=json.loads((root/'assets/fantasyhub/hunter-models.json').read_text());assert len(models)==24
    for key in models:
        ns,path=key.split(':');assert (root/f'assets/{ns}/models/{path}.json').is_file(),key
    for collection in COLLECTIONS:
        original=a.references/f'config1/ItemsAdder/contents/svframe_imported_mod_assets/_REFERENCE_ONLY/witcher_rpg_geo/{collection}_armor.geo.json'
        generated=root/f'assets/fantasyhub/geo/hunter/{collection}.geo.json'
        assert original.read_bytes()==generated.read_bytes(),collection
        original=a.references/f'config1/ItemsAdder/contents/svframe_imported_mod_assets/resourcepack/assets/witcher_rpg/textures/armor/{collection}_armor.png'
        assert original.read_bytes()==(root/f'assets/fantasyhub/textures/armor/hunter/{collection}.png').read_bytes(),collection
    print(json.dumps({'result':'PASS','original_base_items':len(items),'model_ids':len(models),'unaltered_geo_models':len(COLLECTIONS),'source_stat_and_policy_conformance':'PASS','runtime':'NOT_COVERED_BY_THIS_CHECK'}))
if __name__=='__main__':main()
