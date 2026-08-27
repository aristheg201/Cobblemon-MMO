#!/usr/bin/env python3
from pathlib import Path
import hashlib
import io
import re
import sys
import zipfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'SVFrameLib').resolve()
java = root / 'src/main/java'
res = root / 'src/main/resources'

# Remove the legacy server-plugin marketplace updater entirely; it has no role in a native Fabric mod.
update_checker = java / 'vn/svframe/svframelib/version/UpdateChecker.java'
if update_checker.exists():
    update_checker.unlink()

# Normalize legacy platform-named public methods to native SVFrame/Fabric names.
renames = {
    'isserver-plugin platform': 'isFabric',
    'getCraftserver-plugin platformVersion': 'getServerImplementationVersion',
    'getserver-plugin platformVersion': 'getMinecraftVersion',
    'getLastserver-plugin platformOpened': 'getLastNativeOpened',
}
for path in java.rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    changed = text
    for old, new in renames.items():
        changed = changed.replace(old, new)
    if changed != text:
        path.write_text(changed, encoding='utf-8')

# YamlLite moved out of the old shared audit package into the native config package.
for name in ('YamlCorpusSmoke.java', 'YamlSkillCountSmoke.java'):
    path = java / 'vn/svframe/svframelib/audit' / name
    if not path.exists():
        continue
    text = path.read_text(encoding='utf-8')
    imp = 'import vn.svframe.svframelib.config.YamlLite;\n'
    if imp not in text:
        package_end = text.find('\n', text.find('package '))
        text = text[:package_end + 1] + imp + text[package_end + 1:]
        path.write_text(text, encoding='utf-8')

# Runtime identity is the final Fabric mod id, not the historical bootstrap id.
fabric_mod = java / 'vn/svframe/svframelib/fabric/SVFrameLibFabricMod.java'
if fabric_mod.exists():
    text = fabric_mod.read_text(encoding='utf-8')
    text = text.replace('public static final String ID = "svframelibfabric";', 'public static final String ID = "svframelib";')
    fabric_mod.write_text(text, encoding='utf-8')

# SVFrameLib owns exactly the 90 built-ins from the library source. AMBERS,
# NEPTUNE_GIFT and SNEAKY_PICKY are registered later by SVFrameMMO and must not
# be executable through the library runtime. Also make the exact native status
# implementation authoritative even for direct NativeDefaultSkillRuntime calls.
default_runtime = java / 'vn/svframe/svframelib/fabric/NativeDefaultSkillRuntime.java'
if default_runtime.exists():
    text = default_runtime.read_text(encoding='utf-8')
    text = text.replace('"AMBERS",', '')
    text = text.replace(',"NEPTUNE_GIFT"', '')
    text = text.replace(',"SNEAKY_PICKY"', '')
    text = text.replace('            case "AMBERS" -> ambers(ctx);\n', '')
    text = text.replace('            case "NEPTUNE_GIFT", "SNEAKY_PICKY" -> true;\n', '')
    text = re.sub(r'^    private static boolean ambers\(ScriptContext c\) \{.*?\}\n', '', text, flags=re.M)
    route = '        if (NativeTargetStatusSkillRuntime.supports(key)) return NativeTargetStatusSkillRuntime.cast(key, ctx);\n'
    marker = '        String key = norm(id);\n'
    if route not in text:
        text = text.replace(marker, marker + route, 1)
    for line in (
        '            case "BLIND" -> status(ctx, "minecraft:blindness", sec(ctx,"duration",5), 0, "minecraft:entity.warden.heartbeat");\n',
        '            case "BURN" -> burn(ctx);\n',
        '            case "POISON" -> status(ctx,"minecraft:poison",sec(ctx,"duration",5),level(ctx,"amplifier",0),"minecraft:entity.spider.hurt");\n',
        '            case "SLOW" -> status(ctx,"minecraft:slowness",sec(ctx,"duration",4),level(ctx,"amplifier",0),"minecraft:block.glass.break");\n',
    ):
        text = text.replace(line, '')
    default_runtime.write_text(text, encoding='utf-8')

# Repackage the migrated native defaults so the runtime installer can load the
# exact same audited YAML corpus from the mod JAR. Keep the loose YAML files too
# because CI validates them directly before boot.
yaml_files = sorted(p for p in res.rglob('*.yml') if p.is_file())
if yaml_files:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in yaml_files:
            arcname = path.relative_to(res).as_posix()
            info = zipfile.ZipInfo(arcname, date_time=(2022, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            zf.writestr(info, path.read_bytes())
    archive = buffer.getvalue()
    digest = hashlib.sha256(archive).hexdigest()

    # Remove stale migrated archive parts, then split into exactly four parts.
    for stale in res.glob('svframelib-1.7.1-defaults.zip.part*'):
        stale.unlink()
    base = len(archive) // 4
    extra = len(archive) % 4
    offset = 0
    for index in range(1, 5):
        size = base + (1 if index <= extra else 0)
        chunk = archive[offset:offset + size]
        offset += size
        (res / f'svframelib-1.7.1-defaults.zip.part{index}').write_bytes(chunk)

    installer = java / 'vn/svframe/svframelib/fabric/SVFrameLibDefaultFiles.java'
    if not installer.exists():
        raise SystemExit(f'Missing migrated default installer: {installer}')
    text = installer.read_text(encoding='utf-8')
    text, count = re.subn(
        r'private static final String ARCHIVE_SHA256 = "[0-9a-f]{64}";',
        f'private static final String ARCHIVE_SHA256 = "{digest}";',
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit('Could not update SVFrameLib defaults archive checksum')
    installer.write_text(text, encoding='utf-8')
