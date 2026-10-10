#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$TASK_ROOT"
if ! command -v java >/dev/null; then echo 'Java 21 JDK is required.' >&2; exit 1; fi
./gradlew --no-daemon --max-workers=3 -p SVFrameLib build
if [[ -d SVFrameMMO ]]; then ./gradlew --no-daemon --max-workers=3 -p SVFrameMMO build nativeCoreSmoke; fi
if [[ -d SVFrameItems ]]; then ./gradlew --no-daemon --max-workers=3 -p SVFrameItems build nativeCoreSmoke; fi
if [[ -d SVFrameMMOCobblemon ]]; then
    ./SVFrameMMOCobblemon/gradlew --no-daemon --max-workers=3 -p SVFrameMMOCobblemon build nativeIntegrationSmoke
fi

if [[ -d SVFrameMobs ]]; then ./gradlew --no-daemon --max-workers=3 -p SVFrameMobs build; fi
python3 SVFrameLib/tools/build_visual_pack.py
python3 scripts/verify-artifacts.py
