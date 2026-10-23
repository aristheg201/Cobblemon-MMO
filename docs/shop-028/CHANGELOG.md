# FantasyHub 0.2.8

- Fix all item/bundle purchases being rolled back by old MMO preference limits: prepare immutable, integrity-checked mailbox blobs before payment and commit a 72-character reference alongside the receipt.
- Preserve inline mailbox compatibility, restart recovery, rollback references and per-item delivery IDs. Committed purchases with delayed delivery report pending mail rather than falsely reporting a rejected purchase.
- Resolve BeastCoin's actual registered currency ID for balances, purchases and class bypass. Support legacy `secondary` wallets without changing currency on insufficient funds. No HunterCoin bypass.
- Explain unavailable BeastCoin when hovering the disabled bypass control.
- Preserve F8 → full Fantasy HUD (Profile, Attributes, Skills, Settings); Shop is a nested screen with Back/Escape to its originating HUD.
- Display Mana/Stamina allocation against their 50-point cap, disable allocation when capped, and show effective values in tooltips.
- Retain the Rider belt/weapon bundles, native item previews, translations and existing server-authoritative purchase rules from 0.2.7.
- Add Profile → Equipment Upgrade using the existing SVFrameItems upgrade engine: real inventory models/tooltips, current/max level, chance, destruction warning, costs, catalyst count and confirmation.
- Bind upgrade confirmations to the exact item/slot/configuration; reject stale/duplicate requests and preserve recovery snapshots for interrupted attempts.
- Add default Flash water walking on source/flowing water and one-block auto-step, with server class validation, client prediction, sneak-to-dive and temporary attribute cleanup.
