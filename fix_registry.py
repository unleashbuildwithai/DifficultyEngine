"""
fix_registry.py — Fix NpcQuestRegistry.java compilation errors:
1. DRAGONS_BREATH -> Material.DRAGON_BREATH (wrong name + wrong prefix)
2. Ambiguous Material/EntityType names in collect() calls -> Material.NAME
3. Ambiguous names in hidden() calls -> Material.NAME
"""
import re

path = r'c:\Users\Owner\Desktop\123\src\main\java\com\yourname\difficulty\quests\NpcQuestRegistry.java'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

original = content

# ── Fix 1: DRAGONS_BREATH -> Material.DRAGON_BREATH ──────────────────────────
# The actual Paper 1.21 material name is DRAGON_BREATH (no S)
content = content.replace('DRAGONS_BREATH', 'Material.DRAGON_BREATH')

# ── Fix 2: Qualify ambiguous names that exist in BOTH Material and EntityType ─
# These appear in collect() calls (5th argument) and hidden() calls (1st arg)
# Pattern: the name appears between OW/NT/EN comma and a number, or after hidden(
ambiguous = [
    'COD', 'SALMON', 'PUFFERFISH', 'EGG', 'ENDER_PEARL',
    'FIREWORK_ROCKET', 'SPECTRAL_ARROW', 'END_CRYSTAL',
    'SNOWBALL', 'TROPICAL_FISH'
]

# Match pattern: comma + spaces + NAME + comma (the name as 5th arg to collect)
# and also hidden(NAME, for hidden trigger first arg
for name in ambiguous:
    # Replace in collect argument position: ", NAME," (with surrounding spaces)
    # Use word boundary approach
    content = re.sub(
        r',\s+(' + re.escape(name) + r'),',
        lambda m: m.group(0).replace(name, 'Material.' + name),
        content
    )
    # Also replace in hidden( position: "hidden(NAME,"
    content = re.sub(
        r'\.hidden\(' + re.escape(name) + r',',
        '.hidden(Material.' + name + ',',
        content
    )

# ── Verify ────────────────────────────────────────────────────────────────────
changes = 0
for line in content.splitlines():
    if 'Material.' in line and ('COD' in line or 'SALMON' in line or 'DRAGON_BREATH' in line):
        changes += 1

print(f"Changes made: content changed = {content != original}")
print(f"DRAGONS_BREATH remaining: {content.count('DRAGONS_BREATH')}")
print(f"Material.DRAGON_BREATH occurrences: {content.count('Material.DRAGON_BREATH')}")
print(f"Material.ENDER_PEARL occurrences: {content.count('Material.ENDER_PEARL')}")
print(f"Material.COD occurrences: {content.count('Material.COD')}")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("File saved successfully.")
