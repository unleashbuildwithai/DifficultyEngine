# DifficultyEngine

A feature-rich Paper 1.21 plugin that layers an OSRS-inspired skill system, dynamic difficulty scaling, elemental magic, custom weapons, skill capes, a Magic Bag, and more onto vanilla Minecraft.

---

## Table of Contents
1. [Commands](#commands)
2. [Difficulty System](#difficulty-system)
3. [Skill System](#skill-system)
4. [Skill Capes](#skill-capes)
5. [Magic Bag](#magic-bag)
6. [Elemental Magic](#elemental-magic)
7. [Mage Gear](#mage-gear)
8. [GunZ Sword](#gunz-sword)
9. [Dark Bow & Dragon Arrows](#dark-bow--dragon-arrows)
10. [Boss Events](#boss-events)
11. [Gold Currency](#gold-currency)
12. [Quest System](#quest-system)
13. [Party System](#party-system)
14. [VIP Shop](#vip-shop)
15. [Miscellaneous Items](#miscellaneous-items)
16. [Resource Pack (Cape Icons)](#resource-pack-cape-icons)
17. [Permissions](#permissions)
18. [Data Files](#data-files)

---

## Commands

| Command | Alias | Description |
|---|---|---|
| `/difficulty [level]` | `/diff` | View or set personal difficulty (peaceful/easy/medium/hard/nightmare) |
| `/hpbar` | — | Toggle live HP display above mobs |
| `/sit [on\|off]` | — | Toggle right-click-to-sit on slabs & stairs |
| `/registry` | — | Open the 2-page Item Registry GUI |
| `/skills [player]` | — | View skill levels |
| `/mystats` | `/stats` | Open personal skill GUI |
| `/cape` | `/mycape` | Open Cape Wardrobe GUI |
| `/magicbag` | `/bag`, `/mbag` | Open Magic Bag GUI |
| `/givebag [player]` | — | Give a Magic Bag **(Admin)** |
| `/gold` | — | Check gold coin balance |
| `/questbook` | — | Open Quest Journal |
| `/party [invite\|leave\|kick\|info]` | — | Manage party |
| `/trade [player]` | — | Open trade session |
| `/spellbook` | — | Read Arcane Tome (unlocked spell combos) |
| `/spellpage [player]` | — | Give a Spell Page **(Admin)** |
| `/gear [player]` | — | Give max netherite gear **(Admin)** |
| `/curecosmetic [player]` | — | Remove cosmetic effects **(Admin)** |
| `/adminlight` | — | Toggle personal admin light **(Admin)** |
| `/vipshop spawn` | — | Spawn VIP Shop Keeper NPC **(Admin)** |

---

## Difficulty System

Players choose a personal difficulty that scales mob stats (health, damage, speed) and affects gold/XP rewards.

| Level | Multiplier | Tag |
|---|---|---|
| Peaceful | 0.5× | Gray |
| Easy | 0.75× | Green |
| Medium | 1.0× | Yellow |
| Hard | 1.5× | Orange |
| Nightmare | 3.0× | Red |

**Group Nightmare**: If 4+ Nightmare players are within 50 blocks, all mobs scale to **×10** difficulty and drop bonus rewards.

---

## Skill System

Eight OSRS-inspired skills each reaching Level 99:

| Skill | XP Source |
|---|---|
| Melee | Dealing melee damage |
| Ranged | Dealing ranged damage |
| Defence | Taking damage / blocking |
| Prayer | Burying bones on dirt (right-click) |
| Magic | Casting elemental spells |
| Woodcutting | Chopping trees |
| Fishing | Catching fish |
| Farming | Harvesting crops |

- Use `/mystats` to track progress
- Level 99 in a skill awards the corresponding **Skill Cape**
- Level 99 in **all** skills awards the **Max Cape**

---

## Skill Capes

Skill capes are ELYTRA items stored in the **Cape Wardrobe** (separate from the chestplate slot — wear both!).

### Opening / Equipping
- `/cape` → opens the Cape Wardrobe GUI
- Drag a cape into the slot, or shift-click it to equip
- Old cape is returned to your inventory on swap

### Visual Effects (ambient particles every 0.5 s)
| Cape | Particle Symbol | Extra |
|---|---|---|
| ⚔ Melee | Crimson **sword** shape | CRIT sparks |
| 🏹 Ranged | Green **bow + arrow** | ENCHANTED_HIT sparks |
| 🛡 Defence | Blue **kite shield** | END_ROD glow |
| ✟ Prayer | Warm-white **latin cross** | ENCHANT letters |
| ✦ Magic | **Six-pointed star** (rainbow cycling) | Rainbow DUST |
| 🪓 Woodcutting | Forest-green **axe** | HAPPY_VILLAGER |
| 🐟 Fishing | Water cascade + tropical fish + axolotl cameo | — |
| 🛒 Farming | Gold-brown **minecart** | COMPOSTER |
| ☠ Boss Cape | Soul-flame cloud | SOUL particles |
| ★ Max Cape | Firework burst | END_ROD |

**Fishing Cape — Axolotl Cameo**: Every 10 s, either a pixel-art axolotl appears (pink DUST particles) **or** a real axolotl entity floats inside an invisible water-bubble (FALLING_WATER + UNDERWATER + SPLASH particles), 50/50 chance.

### Swap Bug Fix
Hologram name tags and health-bar indicators are instantly removed when swapping capes — no more ghost labels floating in the world.

### Resource Pack (Cape Icons)
Run `python gen_resourcepack.py` to generate `DifficultyEngine-RP.zip` which replaces the elytra-wings inventory icon with flat skill-coloured cape silhouettes. Add to `server.properties`:
```
resource-pack=<hosted URL to DifficultyEngine-RP.zip>
resource-pack-sha1=a909a9117c6fc1d4e0350f4a6861a68540d3985e
resource-pack-required=false
```

---

## Magic Bag

A portable 32-slot arcane storage item that auto-sorts magic items into 4 colour-coded sections.

### Getting It
- `/registry` → Page 2 → click **✦ Magic Bag**
- Admin: `/givebag [player]`

### Opening It
- **Right-click** the chest-shaped bag item → opens the 4-section GUI

### Sections
| Section | Colour | Stores |
|---|---|---|
| 🔮 Runes & Dust | Purple | Fire/Water/Earth/Air Runes + Rune Dust |
| ⚗ Staffs & Gear | Blue | Elemental Staffs, all Mage Gear tiers, GunZ Sword, Dark Bow, Dragon Arrows |
| 📜 Spell Books | Cyan | Spell Combo Book, Ancient Kill Tome, Arcane Tome, Spell Pages, Earth Pages |
| 🌿 Ingredients | Green | Enchanted Shards, Soulfur Potions |

### Auto-Collect (both directions)
While carrying the Magic Bag, magic items are automatically redirected:
- **Shift-click FROM a chest** → goes to bag (not inventory)
- **Shift-click FROM inventory while a chest is open** → goes to bag (not the chest)

Non-magic items are never touched.

### Inside the GUI
- **Shift-click** an item → returns it to your inventory
- **⟳ Sort button** (HOPPER) → sorts each section by stack quantity (desc)
- Items placed manually are validated against the section they belong to

---

## Elemental Magic

Four elemental staffs cast spells that apply status effects and unlock powerful **combos**.

### Staffs

| Staff | Key Ingredient | Cast |
|---|---|---|
| 🔥 Fire Staff | Blaze Rod + Shard + Stick | Fireball → **Scorched** |
| 💧 Water Staff | Water Bucket + Shard + Stick | Water bolt → **Wet** |
| 🌍 Earth Staff | Clay Ball + Shard + Stick | Slow + Trap (block) |
| 💨 Air Staff | Feather + Shard + Stick | Knockback gust / Hover (hold in air) |

Each cast consumes 1 Rune. Craft runes: 4× ingredient → 8 runes.

### Key Combos
- **Freeze chain**: Water→Wet → Air→Chilled → Air→**FROZEN** (5 s) → ??? (see Ancient Kill Tome)
- **Statue trap**: Water→Wet → Earth→Muddy → Fire→**STATUE** (8 s) → ???
- **Thaw Explosion**: Fire on FROZEN → massive AoE burst
- **Inferno Vortex**: Fire on BLAZING → double damage fire ticks

### Earth Block Throwing (Magic Lv 10+)
Carry an Earth Magic Page + a throwable block. First Earth hit: traps target. Second: suffocates. Higher-tier blocks deal more damage.

---

## Mage Gear

Leather armour sets that reduce spell cooldowns and amplify Air gust power. 4 tiers:

| Tier | Level | Ingredients | Cooldown / Piece | Air Power |
|---|---|---|---|---|
| Apprentice | Lv 1 | Leather + Purple Dye + String | −100 ms | ×0.75 |
| Mage | Lv 30 | Leather + Purple Dye + Blaze Powder | −250 ms | ×1.25 |
| Alch | Lv 60 | Leather + Blue Dye + Blaze Powder + Eye of Ender | −350 ms | ×1.625 |
| Master | Lv 90 | Leather + Black Dye + Blaze Powder + Enchanted Shard + Dragon Breath | −500 ms | ×2.0 |

Full 4-piece Master set: **−2000 ms cooldown**, **×2.0 air knockback**.

**Mind Bomb** (2+ pieces): 5% chance on combo hits to inflict Nausea + Blindness 5 s, 30% to knock down the target (press SPACE to recover).

---

## GunZ Sword

Admin-spawnable Netherite Sword (Lv 99 Melee required). Equipped from `/gear` or `/registry`.

### Double-Tap Dashing
Hold the GunZ Sword in hand and **double-tap** any movement key:

| Double-tap | Dash Direction |
|---|---|
| **W W** | Forward |
| **S S** | Backward |
| **A A** | Left |
| **D D** | Right |

**Detection**: A per-tick position sampler (every 1 tick / 50 ms) tracks direction changes. A double-tap is detected when the same direction is pressed, released (≥60 ms hold), and pressed again within 320 ms. Sprint events are NOT used — no false triggers from normal sprinting.

- **800 ms cooldown** between dashes
- Dash propels ~3.6 blocks with a small upward arc
- CRIT + ENCHANTED_HIT particles + sweep sound on trigger

---

## Dark Bow & Dragon Arrows

**Dark Bow** — 1% drop from Warden (Lv 70 Ranged required):
- Normal shot: single arrow (with Dragon Arrow: purple trail + glow)
- **Special** (sneak + right-click): fires 2 homing arrows at −35% damage each. Costs 2 Dragon Arrows, 3 s cooldown.

**Dragon Arrow Tip** — drops from Ender Dragon (8–16 per kill)  
**Dragon Arrow** — craft: 4× Dragon Arrow Tips → 4× Dragon Arrows

---

## Boss Events

~1% chance on any mob spawn for a **Double Boss** event:
- Two boss variants spawn with augmented stats
- Players who defeat both **without anyone dying** earn the **Boss Cape**
- Boss mobs also drop the **Ancient Kill Tome** (reveals instant-death combos)

---

## Gold Currency

Gold coins drop from mobs (quantity scales with difficulty). Check balance with `/gold`.

Gold is used in the VIP Shop and trade system.

---

## Quest System

Open with `/questbook`. Kill-based quests award gold and skill XP. Quest progress saves automatically.

---

## Party System

`/party invite <player>` — invite a player  
`/party leave` — leave your party  
Parties share a scoreboard HUD showing member difficulty and HP.

Party members within 50 blocks with Nightmare difficulty trigger the **Group Nightmare** ×10 buff.

---

## VIP Shop

A custom villager NPC that sells cosmetics for gold coins:
- **Unicorn Slippers** — 5,000 gp. Leaves a rainbow particle trail at feet when worn.
- Spawn the keeper: `/vipshop spawn`

---

## Miscellaneous Items

| Item | How to Get | Effect |
|---|---|---|
| Enchanted Shard | ~5% drop from any mob | Crafts staffs & Master Mage Gear |
| Soulfur Potion | `/registry` | Causes Nausea + Drunken Sway, repeated doses darken vision |
| Turbo Minecart | `/registry` | 3× faster rail cart |
| Spell Combo Book | 8% drop from mob killed by staff | Passive: shows combo hints in action bar |
| Spell Page | 4% drop from any mob | Right-click to unlock a random Arcane Tome page |
| Earth Magic Page (tiers) | `/registry` | Carry to unlock Earth block throwing |

---

## Resource Pack (Cape Icons)

The plugin ships with a resource pack generator (`gen_resourcepack.py`) that creates `DifficultyEngine-RP.zip`.

The pack replaces the elytra item icon in inventory with flat, skill-coloured **cape silhouettes** (16×16 px, gold clasp) using `custom_model_data` overrides for IDs 1001–1010. No wings in the inventory — just capes.

**To regenerate**:
```bash
PYTHONIOENCODING=utf-8 python gen_resourcepack.py
```

**To serve**: host the ZIP and add to `server.properties` (see above).

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `difficultyengine.use` | true | Standard player commands |
| `difficultyengine.gear` | op | `/gear` on self |
| `difficultyengine.gear.others` | op | `/gear` on others |
| `difficultyengine.registry` | op | `/registry` |
| `difficultyengine.turbocart` | op | Obtain Turbo Minecart from registry |
| `difficultyengine.cape.admin` | op | Bypass cape level requirements + set skills to 99 |

---

## Data Files

All data is stored under `plugins/DifficultyEngine/`:

| File/Folder | Contents |
|---|---|
| `difficulty/<uuid>.yml` | Per-player difficulty level |
| `skills/<uuid>.yml` | Per-player skill XP/levels |
| `capes/<uuid>.yml` | Equipped cape per player |
| `bags/<uuid>.yml` | Magic Bag contents per player |
| `spellbook/<uuid>.yml` | Unlocked Arcane Tome pages |
| `gold/<uuid>.yml` | Gold coin balances |
| `quests/<uuid>.yml` | Quest progress |

---

## Building

Requires Maven and Java 21+.

```bash
# Windows (Maven on Desktop)
"C:\Users\Owner\Desktop\maven\apache-maven-3.9.9\bin\mvn.cmd" clean package

# Copy to server
copy target\DifficultyEngine-1.0.jar "C:\...\server\plugins\"
```

GitHub: https://github.com/unleashbuildwithai/DifficultyEngine
