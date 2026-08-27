#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'SVFrameLib').resolve()
java = root / 'src/main/java'

# Remove the legacy server-plugin marketplace updater entirely; it has no role in a native Fabric mod.
update_checker = java / 'vn/svframe/svframelib/version/UpdateChecker.java'
if update_checker.exists():
    update_checker.unlink()

# These were legacy platform-named compatibility methods. Rename them to native SVFrame/Fabric API names
# after the deterministic text migration, before Java compilation.
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
