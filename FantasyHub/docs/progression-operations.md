# Class progression operations

`config/FantasyHub/progression.json` is created on first server start. All fields are required; reload with `/fantasyprogress reload` (operator level 2).

```json
{
  "defaultUnlockCost": 60,
  "classUnlockCosts": { "THOR": 60, "FLASH": 60 },
  "learnCost": 1,
  "initialSkillPoints": 1,
  "migrationPointsPerLevel": 1,
  "switchFeesEnabled": true,
  "switchCooldownEnabled": true
}
```

Class unlock costs are separate from the established switch fees in `config/FantasyHub/class-switch.yml`. Turning off switch fees leaves unlock prices unchanged. The first choice ignores the economy service and old/new switch cooldowns, consumes the UUID's starter entitlement, and permanently owns that class in one native profile snapshot. It does not auto-learn skills. DEFAULT classes cannot be selected.

Class Sigil is an earned material banked on the character. Boss rewards credit this native balance; named paper, copied item stacks, and `/give fantasyhub:class_sigil` do not credit it. The GUI explicitly labels the banked balance. Inventory capacity, death drops, and vanilla player inventory saves cannot lose or duplicate this balance. No physical receipts are minted or dropped, because vanilla inventory and the native MMO profile cannot atomically commit together.

The six hero classes require explicit learning. Native class EXP tables continue to award level-up points (`config/SVFrameMMO/exp-tables/default.yml`, `class_exp_table`); progression does not award a second point in a tick loop. The initial/migration floor is applied once per hero class and accounts for explicitly learned skills and upgraded points. Existing class levels and manual skill unlocks remain native; old `unlocked-by-default` settings alone do not become learned ownership. Hero skill requirements are 1/5/10/15/20/25/30, plus the eighth Thor skill at 12. Seven native combat slots can bind any learned eligible hero skill.

Ordinary-player commands use the same authoritative transaction methods as the GUI:

- `/classes` opens class search, Hero filter, ownership, free choice, and unlock confirmation.
- `/fantasyhub skills` opens learning and binding.
- `/fantasyprogress status` reports class, level, learned skills, points, banked materials, ownership, free entitlement, last transaction, and pending economy recovery.
- `/fantasyprogress unlock FLASH` spends the configured banked price and owns Flash without changing the current class.
- `/fantasyprogress learn THOR_STORMBREAKER_THROW` learns only if current class, native level, ownership, and points allow it.
- `/svclass switch FLASH` uses the normal starter/ownership, fee, and cooldown checks.

Operator level 2 commands:

- `/fantasyprogress sigils <player> <positive amount>` grants banked material durably.
- `/fantasyprogress grant <player> <class>` and `revoke` change permanent ownership. Existing configured permission entitlements continue to work independently of banked ownership.
- `/fantasyprogress starter-reset <player>` explicitly gives one new free selection, even if that UUID has already chosen a class. `starter-revoke` consumes that entitlement without selecting a class.
- `/fantasyprogress recovery-status <player>` displays an unresolved paid switch.
- `/fantasyprogress recovery-refund <player>` explicitly refunds the recorded external currency amount and clears the journal.
- `/fantasyprogress recovery-dismiss <player>` clears a reviewed journal without refunding.

Material unlocks, learning, starter consumption, and boss settlement markers are committed in the same canonical native profile as their respective debit/grant. Short progression writes wait on the independent native writer before another server tick can observe speculative state. Failed writes restore the snapshot and persist the rollback. If rollback persistence also fails, further progression is blocked until a server restart reloads canonical storage; logs include the UUID.

Paid class switches additionally keep `<world>/fantasyhub-transactions/<UUID>.json`. A native committed transaction ID automatically clears a leftover journal. A crash between the external economy debit and profile commit leaves an uncertain two-store transaction, which is blocked from charging again and must be reviewed using the recorded transaction ID, source/target, currency, fee, and prior balance. The external economy API has no transactional/idempotency key; automatic refund after an uncertain crash could duplicate currency. Check the backend history before explicitly choosing refund or dismiss, especially if a crash also interrupted an earlier refund.

## Native QA procedure

These are acceptance checks to run on the rebuilt native client/server; automated policy/journal tests alone do not establish a runtime pass.

1. Join with a new ordinary survival account, zero money, and no class permission. `/fantasyprogress status` must show `starter:true`. Open `/classes`, find Flash, confirm a free selection, and inspect `starter:false`, Flash ownership, zero cooldown, and at least one unspent point. Reconnect and restart; the free choice must remain consumed. Another locked class must require Sigils, regardless of client config deletion.
2. Before learning, attempt the native key, item/provider cast, and `cast_id` route for an eligible hero skill; all must reject without damage/resource consumption. Learn through the Skills UI, verify exact point debit and learned label, bind to a slot, then cast. Try learning twice and try a higher-level skill without its level; no extra point debit. At Thor level 12, learn the eighth skill and replace any of the seven bindings with it.
3. Grant 59 Sigils using an operator, then return to the ordinary account. A 60-Sigil unlock must reject. Grant one more, confirm the unlock, then verify a zero balance and permanent ownership while the current class stays unchanged. Spam the confirmation/command; only one debit occurs. Confirm that paper renamed Class Sigil and the registered item granted with `/give` do not change the banked balance.
4. Switch away and back, die, reconnect, and restart. Check ownership, banked balance, learned skills, and per-class points/bindings remain. With fees enabled, later switches retain the configured original fee/cooldown; disable fees independently and verify unlock price remains 60.
5. Boss integration: replay the same settlement ID for a player before and after reconnect/restart. `awardSigilsOnce(player, settlementId, amount)` must complete `true` once and `false` thereafter; balance changes only once. Fill inventory first; rewards remain banked without item drops. WorldBoss keeps an offline mailbox until the grant future succeeds.
6. Inject a profile save failure and verify rollback/no newly usable skill or ownership. Inject a failure of rollback persistence too and verify further progression is blocked. For a paid-switch crash with a prepared journal, verify no second charge after restart and review the recovery commands using a disposable account.

The Windows 289-mod pack, ordinary-player GUI rendering, death/restart behavior, and injected runtime persistence failures remain unverified until these steps are run with saved evidence.
