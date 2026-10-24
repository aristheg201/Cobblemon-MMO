# Native load fixture

Use a disposable copy of the server world/configuration/player data. The controller changes classes, funds test balances, spawns Pokémon and Thanos, changes equipment and performs purchases. Never run it against production.

Install the exact compatible framework/BEconomy/WorldBoss mod set, Spark and the QA load probe. Build the probe separately with Java 21:

```sh
bash ../../gradlew -p ../loadprobe build
npm ci
```

The probe requires `SVFRAME_RUNTIME_QA=1`. Bind RCON to a local-only QA interface and set a password. The controller reads credentials from the supplied server.properties; it does not embed or print them. Advertise matching custom channels and use the tested Velocity configuration. Prepare a safe floor at the scheduled arena, which must have feet Y=71 above a Y=70 floor, and reserve a separate personal arena. Test actors require the existing native QA commands.

```sh
FANTASY_RUNTIME=/path/to/server FANTASY_EVIDENCE=/path/to/evidence \
  python3 run_matrix.py baseline
# Restore the identical world/player/config fixture, replace only the candidate JAR, restart.
FANTASY_RUNTIME=/path/to/server FANTASY_EVIDENCE=/path/to/evidence \
  python3 run_matrix.py optimized
python3 report.py /path/to/evidence
```

Set `FANTASY_COUNTS=1,5,10,20,40` to select phases. The local proxy endpoint defaults to 127.0.0.1:25577 in bots.cjs. Queue/clock/cancellation unit tests are in Core/Client Gradle tests.

Write the running server process ID to EVIDENCE/PHASE/server.pid before starting the controller. Keep all graphical clients disconnected during measured phases.

Each measured phase requires all clients connected and validates counts in every sample. The CPU-window tick probe holds at most 12,000 samples; overflow invalidates a result. A separate 20-player Spark allocation window follows. The full actor-session custom-payload totals include setup/warmup and other mod channels. Cast/purchase counters are attempted inputs, not assertions that every cooldown-gated cast succeeded. Native server logs and regression transactions provide the gameplay cross-check.

These are headless protocol actors connected to an actual Minecraft server. They acknowledge configured mod channels, intentionally skip unsupported modded entity metadata and do not implement full client physics/rendering. They are not screenshots, 40 graphical Minecraft clients, or a human WorldBoss playtest. Keep that limitation with any published benchmark.

SparkReport.java reads the exact bundled Spark protobuf schema without exporting server configuration. Compile it with the installed Spark JAR on the classpath. JfrReport.java uses Java 21's streaming JFR reader; pass a JFR file and the relevant ticks.json files. Inspect and redact Spark server configuration metadata before sharing.
