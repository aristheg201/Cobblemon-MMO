# Native verification record

The addon delivery includes exact command observations, encounter journals, attack
stage/damage audit rows, screenshots and native client recordings under `qa/`.
The audit is opt-in (`SVFRAME_RUNTIME_QA=1`), and is off in normal installations.
Videos contain no audio. QA accounts, arena coordinates and test state are not
production defaults.

Completed class-boss probes:

- Added a new Batman boss only through JSON. Its configured BATARANG rotation
  changed to CAPE_COMBO below 50% HP.
- Three-second cyan particle warnings preceded actual damage by at least 60 server
  ticks. Keyboard movement out of the second warning avoided that attack.
- Actual boss death granted the configured 20 Sigils with a full inventory; repeated
  claims granted none. An actual shortened encounter timeout granted 5.
- An unknown skill was rejected on reload; the previous working configuration stayed
  active. Automated validation also covers duplicate IDs, malformed phases, unknown
  class/species/skills, timing, sample defaults and partial actor creation cleanup.

See the final delivery validation report for Pokemon-health, scheduling and restart
results. These are native Fabric client/server checks, not a claim that the user's
exact 289-mod Windows installation was reproduced. Tests of deliberately injected
native adapter failures and comparative phase-damage coefficients were not run.
