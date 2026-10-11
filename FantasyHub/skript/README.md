# SVFrameSkript — Fabric 1.21.1

Native execution of the supplied class/dash scripts; this is not a claim of complete Skript 2.13.2 or addon compatibility. Requires Java 21, Fabric Loader >=0.18.4, Fabric API and the matching SVFrameLib/SVFrameMMO builds from this delivery. The current Lib has new typed compatibility hooks; an older JAR with the same version label is not interchangeable.

Copy original `.sk` files unchanged into `config/SVFrameSkript/scripts/`, including subdirectories. A file or directory beginning with `-` is disabled. Restart or use operator-level-2 `/skript reload`; `/skript diagnostics` and `config/SVFrameSkript/compatibility.json` show actual parse/runtime failures. Unsupported syntax must be resolved, not renamed into a different DSL. Persistent variables live in `config/SVFrameSkript/variables.json`; back this up with character and economy data.

The user's required scope is class and dash behavior. The three supplied files compile unchanged: `config2/sv_class_switch.sk`, `config2/sv_dash_mmocore.sk`, and `amk/sv_anti_mage_knight.sk`. Item/shop and other unrelated scripts are excluded. The Config artifact includes the original three files for the owner's internal use; they are not published in Git.

The source class command owns fees, cooldowns and source-valid class IDs. Its DeluxeMenus command alias opens the native class screen. Original AMK commands own the seven AMK behaviors while MMO retains costs/cooldowns/level checks; native and script combat execution do not run twice. Dash receives real movement/jump events, spends authoritative stamina and cancels real damage only during its source dodge window. Source imports identifying Bukkit classes are mapped to explicit typed native APIs; no Bukkit server is required.

Supported runtime families include options/import mappings, expressions, conditionals, functions/returns, commands/arguments/permissions, events/cancellation, variables and sparse lists, loops, delayed execution, native vectors/locations/items/particles/sounds, and the required RPG adapters. Execution, scheduling and entity access are bounded. Delayed tasks resume on the server thread; variable writes are atomic and coalesced off-thread. Synchronous command failures undo journalled changes/withdrawals. Cross-provider process-crash transaction recovery is unfinished.

Local build, after building matching Lib/MMO JARs:

```sh
JAVA_HOME=/path/to/java21 /path/to/gradle-8.8/bin/gradle :skript:build \
  -PsuppliedScripts=/path/to/config2/Skript/scripts --console=plain --no-daemon
```

See [runtime evidence and limits](../../docs/SKRIPT-IMPLEMENTATION-QA.md). Production must leave `SVFRAME_RUNTIME_QA` unset. Do not install Bukkit Skript, SkBee, skript-reflect, or Paper plugin JARs on the Fabric server.
