#!/usr/bin/env python3
from pathlib import Path
import io, re, shutil, sys, zipfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'SVFrameLib').resolve()
java = root / 'src/main/java'
res = root / 'src/main/resources'

DELETE_JAVA = {
    'vn/svframe/svframelib/comp/flags/WorldGuardFlags.java',
    'vn/svframe/svframelib/skill/handler/FabledSkillHandler.java',
    'vn/svframe/svframelib/skill/result/FabledSkillResult.java',
}
CLASS_RENAMES = {
    'MythicLib': 'SVFrameLib',
    'MythicCraftItemEvent': 'SVFrameCraftItemEvent',
    'MythicBlueprintInventory': 'SVFrameBlueprintInventory',
    'MythicIngredient': 'SVFrameIngredient',
    'MythicRecipeIngredient': 'SVFrameRecipeIngredient',
    'MythicRecipeInventory': 'SVFrameRecipeInventory',
    'MythicRecipeOutput': 'SVFrameRecipeOutput',
    'MythicCachedResult': 'SVFrameCachedResult',
    'MythicCraftingManager': 'SVFrameCraftingManager',
    'MythicRecipeBlueprint': 'SVFrameRecipeBlueprint',
    'MythicRecipeStation': 'SVFrameRecipeStation',
    'MythicRecipe': 'SVFrameRecipe',
    'MythicItemUIFilter': 'SVFrameItemUIFilter',
    'MMOItemType': 'SVFrameItemType',
    'MythicPlaceholder': 'SVFramePlaceholder',
    'MythicPlaceholders': 'SVFramePlaceholders',
    'MythicLibVariablePlaceholder': 'SVFrameLibVariablePlaceholder',
    'PAPIPlaceholder': 'RegisteredPlaceholder',
    'PAPI_PLACEHOLDER_PATTERN': 'PLACEHOLDER_PATTERN',
    'MythicLibSkillHandler': 'SVFrameLibSkillHandler',
    'MythicLibSkillResult': 'SVFrameLibSkillResult',
    'MythicPlayerSessionRuntime': 'SVFramePlayerSessionRuntime',
    'SpigotPlugin': 'UpdateChecker',
}
REPLACEMENTS = [
    ('vn.svframe.mythiclibfabric', 'vn.svframe.svframelib.fabric'),
    ('vn.svframe.compat.YamlLite', 'vn.svframe.svframelib.config.YamlLite'),
    ('vn.svframe.compat', 'vn.svframe.svframelib.audit'),
    ('vn.svframe.svframelib.metrics.bukkit', 'vn.svframe.svframelib.metrics'),
]
for old, new in sorted(CLASS_RENAMES.items(), key=lambda kv: -len(kv[0])):
    REPLACEMENTS.append((old, new))
REPLACEMENTS += [
    ('getMythicIdentifier', 'getSVFrameIdentifier'),
    ('getMainMythicInventory', 'getMainSVFrameInventory'),
    ('getResultMythicInventory', 'getResultSVFrameInventory'),
    ('getSideMythicInventories', 'getSideSVFrameInventories'),
    ('getSideMythicInventory', 'getSideSVFrameInventory'),
    ('MYTHICLIB_RECONSTRUCTED_RUNTIME', 'SVFRAMELIB_NATIVE_RUNTIME'),
    ('MYTHICLIB_NATIVE', 'SVFRAMELIB_NATIVE'),
    ('MYTHICLIB_CRAFTING_CACHE', 'SVFRAMELIB_CRAFTING_CACHE'),
    ('MMOITEMS_', 'SVFRAMEITEMS_'),
    ('MMOItems', 'SVFrameItems'), ('mmoitems', 'svframeitems'),
    ('MMOItem', 'SVFrameItem'), ('mmoitem', 'svframeitem'),
    ('MMOCore', 'SVFrameMMO'), ('mmocore', 'svframemmo'), ('MMOCORE', 'SVFRAMEMMO'),
    ('MYTHICLIB', 'SVFRAMELIB'), ('mythiclib', 'svframelib'),
]

def replace_text(text: str) -> str:
    # Rename platform-specific API identifiers before generic brand scrubbing so
    # Java identifiers stay syntactically valid and public names are Fabric-native.
    text = text.replace('isPaper', 'isFabric')
    text = text.replace('getCraftBukkitVersion', 'getLoaderPlatform')
    text = text.replace('getBukkitVersion', 'getGameVersion')
    text = text.replace('getLastBukkitOpened', 'getLastPlatformOpened')
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    text = text.replace('PlaceholderAPI-backed', 'provider-registry-backed')
    text = text.replace('Bukkit/PAPI', 'server-plugin placeholder APIs')
    text = text.replace('PAPI/int semantics', 'placeholder/int semantics')
    text = re.sub(r'\bPAPI_PLACEHOLDER_PATTERN\b', 'PLACEHOLDER_PATTERN', text)
    text = re.sub(r'\bpapi\b', 'placeholders', text)
    text = text.replace('"papi"', '"placeholders"').replace("'papi'", "'placeholders'")
    text = text.replace('bukkitBootstrap', 'fabricBootstrap')
    text = text.replace('Map<String, Object> bukkit', 'Map<String, Object> sources')
    text = text.replace('damageTypes.get("bukkit")', 'damageTypes.get("sources")')
    text = text.replace('bukkit.entrySet()', 'sources.entrySet()')
    text = text.replace('Bukkit', 'server-plugin platform')
    text = text.replace('bukkit', 'server_plugin_platform')
    text = text.replace('Spigot', 'Fabric')
    text = text.replace('spigot', 'fabric')
    text = text.replace('Paper', 'server-plugin platform')
    text = text.replace('paper', 'server_plugin_platform')
    text = text.replace('Fabled', 'external skill provider')
    text = text.replace('fabled', 'external_skill_provider')
    text = text.replace('WorldGuard', 'external region provider')
    text = text.replace('worldguard', 'external_region_provider')
    text = text.replace('PAPI', 'registered placeholder')
    return text

files = [(p.relative_to(java).as_posix(), p.read_text(encoding='utf-8')) for p in java.rglob('*.java')]
shutil.rmtree(java)
java.mkdir(parents=True)
for rel, text in files:
    if rel in DELETE_JAVA:
        continue
    new_rel = rel
    if new_rel.startswith('vn/svframe/mythiclibfabric/'):
        new_rel = 'vn/svframe/svframelib/fabric/' + new_rel[len('vn/svframe/mythiclibfabric/'):]
    elif new_rel.startswith('vn/svframe/compat/'):
        name = new_rel.rsplit('/', 1)[-1]
        new_rel = ('vn/svframe/svframelib/config/' if name == 'YamlLite.java' else 'vn/svframe/svframelib/audit/') + name
    elif new_rel == 'vn/svframe/svframelib/metrics/bukkit/Metrics.java':
        new_rel = 'vn/svframe/svframelib/metrics/Metrics.java'
    filename = Path(new_rel).name
    for old, new in sorted(CLASS_RENAMES.items(), key=lambda kv: -len(kv[0])):
        filename = filename.replace(old, new)
    new_rel = str(Path(new_rel).with_name(filename)).replace('\\', '/')
    text = replace_text(text)
    if new_rel == 'vn/svframe/svframelib/config/YamlLite.java':
        text = text.replace('package vn.svframe.svframelib.audit;', 'package vn.svframe.svframelib.config;')
    out = java / new_rel
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding='utf-8')

parts = sorted(res.glob('mythiclib-1.7.1-defaults.zip.part*'))
if parts:
    payload = b''.join(p.read_bytes() for p in parts)
    with zipfile.ZipFile(io.BytesIO(payload)) as zf:
        for member in zf.infolist():
            if member.is_dir() or not member.filename.endswith('.yml'):
                continue
            rel = member.filename.lstrip('/')
            rel = rel.replace('mmoitems_scripts.yml', 'svframeitems_scripts.yml').replace('mmocore_scripts.yml', 'svframemmo_scripts.yml')
            text = replace_text(zf.read(member).decode('utf-8'))
            text = text.replace('  server_plugin_platform:\n', '  sources:\n')
            text = re.sub(r'^.*external skill provider.*\n?', '', text, flags=re.M|re.I)
            text = re.sub(r'^.*external region provider.*\n?', '', text, flags=re.M|re.I)
            text = re.sub(r'^.*FABLED.*\n?', '', text, flags=re.M)
            text = re.sub(r'^.*mythic[.]html.*\n?', '', text, flags=re.M|re.I)
            out = res / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(text, encoding='utf-8')
    for p in parts:
        p.unlink()

old_mix = res / 'mythiclibfabric.mixins.json'
if old_mix.exists():
    text = replace_text(old_mix.read_text(encoding='utf-8'))
    text = text.replace('MythicLib-refmap.json', 'SVFrameLib-refmap.json')
    (res / 'svframelibfabric.mixins.json').write_text(text, encoding='utf-8')
    old_mix.unlink()
fm = res / 'fabric.mod.json'
if fm.exists():
    text = replace_text(fm.read_text(encoding='utf-8'))
    text = text.replace('mythiclibfabric.mixins.json', 'svframelibfabric.mixins.json')
    fm.write_text(text, encoding='utf-8')
old_refmap = res / 'MythicLib-refmap.json'
if old_refmap.exists(): old_refmap.rename(res / 'SVFrameLib-refmap.json')

for rel in ('build.gradle', 'gradle.properties', 'settings.gradle', 'README.md', 'SOURCE-LIST.txt'):
    p = root / rel
    if p.exists():
        text = replace_text(p.read_text(encoding='utf-8'))
        text = text.replace('mythiclibfabric.mixins.json', 'svframelibfabric.mixins.json')
        text = text.replace('MythicLib-refmap.json', 'SVFrameLib-refmap.json')
        p.write_text(text, encoding='utf-8')

source_list = root / 'SOURCE-LIST.txt'
public_paths = [p.relative_to(java).with_suffix('').as_posix() for p in java.rglob('*.java') if p.relative_to(java).as_posix().startswith('vn/svframe/svframelib/')]
source_list.write_text('\n'.join(sorted(public_paths))+'\n', encoding='utf-8')
