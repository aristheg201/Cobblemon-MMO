from pathlib import Path

p = Path('src/main/java/dev/aristheg/alphaencounter/AlphaEncounterMod.java')
s = p.read_text()

old_transition = '''                    if (active.hp <= 0.5f || CobblemonBridge.currentHealth(pokemon) <= 0) {
                        defeatEncounter(server, active, false);
                    } else {
                        active.state = EncounterState.HUNT;
                        active.targetPlayer = active.lastBattlePlayer;
                        active.nextReengageTick = tick + Math.max(10, tier(active.tierId).reengageCooldownTicks);
                        stateDirty = true;
                    }'''

new_transition = '''                    if (active.hp <= 0.5f) {
                        defeatEncounter(server, active, false);
                    } else {
                        if (CobblemonBridge.currentHealth(pokemon) <= 0) {
                            restorePokemonForNextPhase(active, pokemon);
                        }
                        active.state = EncounterState.HUNT;
                        active.targetPlayer = active.lastBattlePlayer;
                        active.nextReengageTick = tick + Math.max(10, tier(active.tierId).reengageCooldownTicks);
                        stateDirty = true;
                    }'''

old_prepare = '''        private void preparePokemonHealthForBattle(ActiveEncounter active, PokemonEntity pokemon) {
            initializeHealth(active, pokemon);
            int max = Math.max(1, CobblemonBridge.maxHealth(pokemon));
            float ratio = Math.max(0.01f, Math.min(1f, active.hp / active.maxHp));
            int desired = Math.max(1, Math.min(max, Math.round(max * ratio)));
            CobblemonBridge.setCurrentHealth(pokemon, desired);
            active.lastPokemonHealth = desired;
        }

        private void syncBattleHealth(ActiveEncounter active, PokemonEntity pokemon) {
            initializeHealth(active, pokemon);
            int max = Math.max(1, CobblemonBridge.maxHealth(pokemon));
            int current = Math.max(0, CobblemonBridge.currentHealth(pokemon));
            float next = active.maxHp * (current / (float) max);
            if (Math.abs(next - active.hp) > 0.01f) {
                active.hp = Math.max(0f, Math.min(active.maxHp, next));
                stateDirty = true;
            }
            active.lastPokemonHealth = current;
        }'''

new_prepare = '''        private void preparePokemonHealthForBattle(ActiveEncounter active, PokemonEntity pokemon) {
            initializeHealth(active, pokemon);
            int max = Math.max(1, CobblemonBridge.maxHealth(pokemon));
            int desired = Math.max(1, Math.min(max, (int) Math.ceil(active.hp)));
            CobblemonBridge.setCurrentHealth(pokemon, desired);
            active.lastPokemonHealth = desired;
        }

        private void syncBattleHealth(ActiveEncounter active, PokemonEntity pokemon) {
            initializeHealth(active, pokemon);
            int current = Math.max(0, CobblemonBridge.currentHealth(pokemon));
            if (active.lastPokemonHealth < 0) {
                active.lastPokemonHealth = current;
                return;
            }

            int delta = active.lastPokemonHealth - current;
            if (delta > 0) {
                active.hp = Math.max(0f, active.hp - delta);
                stateDirty = true;
            } else if (delta < 0) {
                active.hp = Math.min(active.maxHp, active.hp + (-delta));
                stateDirty = true;
            }
            active.lastPokemonHealth = current;
        }

        private void restorePokemonForNextPhase(ActiveEncounter active, PokemonEntity pokemon) {
            int max = Math.max(1, CobblemonBridge.maxHealth(pokemon));
            int desired = Math.max(1, Math.min(max, (int) Math.ceil(active.hp)));
            CobblemonBridge.setCurrentHealth(pokemon, desired);
            active.lastPokemonHealth = desired;
        }'''

for old, new, label in [
    (old_transition, new_transition, 'battle transition'),
    (old_prepare, new_prepare, 'shared HP sync'),
]:
    if old not in s:
        raise SystemExit(f'Expected {label} block not found; refusing blind patch')
    s = s.replace(old, new, 1)

p.write_text(s)
print('Patched shared HP to battle-delta multi-phase semantics.')
