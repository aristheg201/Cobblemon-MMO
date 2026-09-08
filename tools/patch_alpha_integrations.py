from pathlib import Path

p=Path('src/main/java/dev/aristheg/alphaencounter/config/AlphaEncounterConfigManager.java')
s=p.read_text(encoding='utf-8')
repls={
'''        tier.bossBar = bool(old, "bossBar", true);\n        tier.bossBarRange = number(old, "bossBarRange", 72.0);\n        tier.bossBarColor = string(old, "bossBarColor", "YELLOW");''':'''        tier.bossBarProfile = id;''',
'''        godzilla.catchHealthPercent = 0.10f;\n        godzilla.spawnMessage = "[Alpha] {name} has emerged in Mount Yeager.";\n        godzilla.defeatMessage = "[Alpha] {name} has been defeated.";\n        godzilla.catchMessage = "[Alpha] {name} is vulnerable to capture for a short time.";''':'''        godzilla.catchHealthPercent = 0.10f;\n        godzilla.messageProfile = "apex";\n        godzilla.bossBarProfile = "apex";''',
'''        t.behaviour = "passive";\n        t.bossBarRange = 72;\n        t.bossBarColor = "YELLOW";''':'''        t.behaviour = "passive";\n        t.bossBarProfile = "regional";''',
'''        t.behaviour = "aggressive";\n        t.bossBarRange = 88;\n        t.bossBarColor = "BLUE";''':'''        t.behaviour = "aggressive";\n        t.bossBarProfile = "signature";''',
'''        t.behaviour = "apex_hunter";\n        t.bossBarRange = 112;\n        t.bossBarColor = "PURPLE";''':'''        t.behaviour = "apex_hunter";\n        t.bossBarProfile = "apex";'''
}
for old,new in repls.items():
    if old not in s: raise RuntimeError('missing expected legacy block: '+old[:80])
    s=s.replace(old,new)
p.write_text(s,encoding='utf-8')
print('Fixed legacy Alpha-Encounter config fields.')
