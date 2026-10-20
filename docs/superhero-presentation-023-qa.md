# Local acceptance evidence: heroes 0.2.3

Local Java 21 build completed with 163 passing tests. The regressions cover hero icon model selection, RPG weapon durability defaults, Batman glide onset/exit policy, cape priority, retained effect phases and the real Mjolnir model's hierarchy/per-face UV decoding. Existing startup geometry isolation and Stormbreaker grip tests remain green. Framework native smoke checks pass.

The server accepted all 42 abilities in the native matrix. The matrix caught Web Swing and Thunderous Slam landing past their target; both now record a real hit. Mjolnir records outbound and return hits. Non-damaging utility skills are not judged by hit count. Observed windows and complete raw reports are included in `qa/native-42-skills.json`; this does not claim every portal traversal or every PvP/region combination was re-tested.

Native rendering was checked on an actor and an independent spectator with Better Combat 2.4.0, EMF 3.3.12, ETF 7.2.6, GeckoLib 4.9.3 and AzureLib 3.1.21. Functional Back used Accessories 1.1.0-beta.54 with Trinkets 3.10.0 present. Batman's new cape is also registered and tagged for standalone Trinkets through the same optional API path; this release does not claim a separate standalone-Trinkets game launch for the new cape.

Keyboard flight checks used one active software-rendered client to avoid competing renderers delaying input polling. Two simultaneous software clients can miss short keyboard gesture windows; that stress run is not recorded as passing. These tests are separate from the two-client skill presentation checks. The native postchecks confirm:

- Batman normal jump apex exceeds one block (before the fix, about 0.42 blocks); native glide starts during descent and ends on Back removal.
- Thor needs the full unmasked three-piece set and Back cape; removing a set piece revokes flight. Strange flies with its Back cape and revokes flight when it is removed.
- Hero armor and Stormbreaker remain unbreakable with zero added damage after 20 native attacks and incoming-hit attempts.
- With a real equipped weapon, EMF player parts are active, the model pose changes during walking, the animated hero-suit render counter advances, and the external-pose protection counter stays constant. The diagnostic hash of the first custom child alone is not used as evidence of motion.
- The actual item renderer resolves `fantasyhub:item/heroes/*` sprites; all seven skill slots are rendered after entering the skill bar.
- Removing the physical Stormbreaker leaves the hands empty.

A six-class native recording and screenshots show the actual geometric effects, particle bursts, suits and intact player rig. They are not AI-generated previews. The X11 capture records video only; it does not contain the native game sound track. The visual references guide the design rather than promising Marvel Future Fight's cinematic camera choreography or pixel-identical animation.

The offline QA accounts do not own live Mojang capes. Personal-cape precedence is verified by selection policy tests and the Batman CapeFeatureRenderer cancellation path; no live-account cape screenshot is claimed.

The user's entire Windows pack of 289 mods was not available here. Startup/world loading was checked with the specific geometry libraries that caused the reported failure, and the separate EMF/ETF loaders required by Detailed Animations. Custom non-Bedrock models stay outside shared `geo/` scans.
