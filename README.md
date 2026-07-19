# DifficultyEngine

A feature-rich **Paper 1.21** plugin that layers an OSRS-inspired skill system, dynamic difficulty scaling, elemental magic, custom gear (Mage / Melee / Ranged), special weapons, skill capes, a Magic Bag, an Arcane Tome, and more onto vanilla Minecraft.

> **GitHub**: https://github.com/unleashbuildwithai/DifficultyEngine

---

## Table of Contents
1. [Commands](#commands)
2. [Difficulty System](#difficulty-system)
3. [Skill System](#skill-system)
4. [Skill Capes](#skill-capes)
5. [Magic Bag](#magic-bag)
6. [Arcane Tome & Spell Pages](#arcane-tome--spell-pages)
7. [Elemental Magic](#elemental-magic)
8. [Mage Gear (4 Tiers)](#mage-gear-4-tiers)
9. [Melee Gear (4 Tiers)](#melee-gear-4-tiers)
10. [Ranged Gear (4 Tiers) + Arrow Speed](#ranged-gear-4-tiers--arrow-speed)
11. [GunZ Sword](#gunz-sword)
12. [Dark Bow & Dragon Arrows](#dark-bow--dragon-arrows)
13. [Boss Events](#boss-events)
14. [Gold Currency](#gold-currency)
15. [Quest System](#quest-system)
16. [Party System](#party-system)
17. [VIP Shop & Cosmetics](#vip-shop--cosmetics)
18. [Miscellaneous Items](#miscellaneous-items)
19. [Resource Pack (Cape Icons)](#resource-pack-cape-icons)
20. [Permissions](#permissions)
21. [Data Files](#data-files)
22. [Building](#building)

---

## Commands

| Command | Description |
|---|---|
| `/difficulty [level]` | View or set personal difficulty (peaceful / easy / medium / hard / nightmare) |
| `/hpbar` | Toggle live HP display above mobs |
| `/sit [on\|off]` | Toggle right-click-to-sit on slabs & stairs |
| `/registry` | Open the 2-page Item Registry GUI |
| `/skills [player]` | View skill levels |
| `/mystats` / `/stats` | Open personal skill GUI |
| `/cape` / `/mycape` | Open Cape Wardrobe GUI |
| `/magicbag` | Open Magic Bag GUI |
| `/givebag [player]` | Give a Magic Bag **(Admin)** |
| `/gold` | Check gold coin balance |
| `/questbook` | Open Quest Journal |
| `/party [invite\|leave\|kick\|info]` | Manage party |
| `/trade [player]` | Open trade session |
| `/spellbook` | Read Arcane Tome **(Admin shortcut — players craft the tome)** |
| `/spellpage [player]` | Give a Spell Page **(Admin)** |
| `/gear [player]` | Give max netherite gear **(Admin)** |
| `/curecosmetic [player]` | Remove cosmetic effects **(Admin)** |
| `/adminlight` | Toggle personal admin light **(Admin)** |
| `/vipshop spawn` | Spawn VIP Shop Keeper NPC **(Admin)** |

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

Eight OSRS-inspired skills, each reaching Level 99:

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

Skill capes are **ELYTRA** items stored in the **Cape Wardrobe** (separate from the chestplate slot — wear both!).

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
| 🐟 Fishing | Water cascade + **dual orbit rings** | See below |
| 🛒 Farming | Gold-brown **minecart** | COMPOSTER |
| ☠ Boss Cape | Soul-flame cloud | SOUL particles |
| ★ Max Cape | Firework burst | END_ROD |

**Fishing Cape — Dual-Ring Orbit**:
- **6 Axolotls** orbit in a vertical great-circle ring (like longitude lines, head-to-toe) rotating around the player's Y-axis — one of each of the 5 vanilla variants
- **8 Tropical Fish** orbit in a horizontal ring at waist height (equator), rotating opposite direction with all colour/pattern variants cycling
- All orbit entities are persistent (no drift/flop), teleported to precise positions each tick, despawned instantly on cape removal
- Water cascade + UNDERWATER + SPLASH particles frame the effect

### Hologram Fix
All cape armour stands now spawn with `setMarker(true)` applied atomically via the Paper `world.spawn(Consumer)` API — the crosshair **never** shows "Armour Stand" at any point.

### Resource Pack (Cape Icons)
Run `python gen_resourcepack.py` to generate `DifficultyEngine-RP.zip`. Replaces the elytra inventory icon with flat skill-coloured cape silhouettes. Add to `server.properties`:
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
| ⚗ Staffs & Gear | Blue | Elemental Staffs, all Mage/Melee/Ranged Gear tiers, GunZ Sword, Dark Bow, Dragon Arrows |
| 📜 Spell Books | Cyan | Spell Combo Book, Ancient Kill Tome, Arcane Tome, Spell Pages, Earth Pages |
| 🌿 Ingredients | Green | Enchanted Shards, Soulfur Potions |

### Auto-Collect
While carrying the Magic Bag, magic items redirect automatically:
- **Shift-click FROM a chest** → goes to bag (not inventory)
- **Shift-click FROM inventory while a chest is open** → goes to bag (not the chest)

### Inside the GUI
- **Shift-click** an item → returns it to your inventory
- **⟳ Sort button** (HOPPER) → sorts each section by stack quantity (desc)
- Items placed manually are validated against the section they belong to

---

## Arcane Tome & Spell Pages

The **Arcane Tome** is a craftable grimoire containing 41 pages. All pages start hidden as `???` — players unlock them by right-clicking **Spell Pages**.

### Crafting the Arcane Tome
```
Book + Amethyst Shard + Purple Dye  →  Arcane Tome (all 41 pages locked)
```

### Unlocking Pages
- **Spell Pages** drop from any hostile mob (4% chance)
- **Right-click** a Spell Page → unlocks 1 random locked page in your Arcane Tome
- **Right-click** the Arcane Tome → opens your personal book view

### What the 41 Pages Contain

| Pages | Content |
|---|---|
| 1–7 | Introduction, four elements, staff descriptions, status effect overview |
| 8–16 | Wet, Muddy, Chilled, Frozen, Statue, Scorched, Blazing, Mind Bomb, Fallen |
| 17–37 | All major spell combos (Blazing → Steam → Inferno → STATUE → Freeze chain → instant kills) |
| 38–41 | **Visual Mage Gear craft guides** — one per tier (Apprentice/Mage/Alch/Master) showing ingredient grid + resulting 4 pieces |

The Arcane Tome is purely a discovery system — `/spellbook` is admin-only.

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
- **Freeze chain**: Water→Wet → Air→Chilled → Air→**FROZEN** (5 s) → Air→☠ SHATTERED
- **Statue trap**: Water→Wet → Earth→Muddy → Fire→**STATUE** (8 s) → Air→☠ CRUMBLED
- **Thaw Explosion**: Fire on FROZEN → massive AoE burst
- **Inferno Blast**: Fire + Fire → BLAZING → Air → devastating fire knockback
- **Steam Explosion**: Fire + Fire → BLAZING → Water → AoE knockback + burst

### Earth Block Throwing (Magic Lv 10+)
Carry an Earth Magic Page + a throwable block. First Earth hit: traps target. Second: suffocates. Higher-tier blocks deal more damage.

### Sandstorm
Casting Air on quicksand (wet dirt/sand) triggers a regional sandstorm effect.

---

## Mage Gear (4 Tiers)

Leather armour sets that reduce spell cooldowns and amplify Air gust power. **Craft at a crafting table (shapeless).**

| Tier | Level | Ingredients | Cooldown / Piece | Air Power |
|---|---|---|---|---|
| 🟦 Apprentice | Lv 1 | Leather piece + Purple Dye + **String** | −100 ms | ×0.75 |
| 🟣 Mage | Lv 30 | Leather piece + Purple Dye + **Blaze Powder** | −250 ms | ×1.25 |
| 🔵 Alch | Lv 60 | Leather piece + Blue Dye + Blaze Powder + **Eye of Ender** | −350 ms | ×1.625 |
| ⚫ Master | Lv 90 | Leather piece + Black Dye + Blaze Powder + **Enchanted Shard** + **Dragon Breath** | −500 ms | ×2.0 |

Full 4-piece Master set: **−2000 ms cooldown**, **×2.0 air knockback**.

**Mind Bomb** (2+ pieces): 5% chance on combo hits to inflict Nausea + Blindness 5 s, 30% to knock down the target (press SPACE to recover).

**Cooldown formula**: `base(3000ms) − (level/99 × 2000ms) − (gear_bonus)`, minimum 500 ms. Level 99 + full Master set = **500 ms** (2 casts/second).

---

## Melee Gear (4 Tiers)

Iron/Diamond/Netherite armour sets gated by **Melee level**. Each tier gives a defence bonus per piece and a damage multiplier while wearing a full set.

| Tier | Level | Recipe Ingredient | Defence Bonus/Piece | Damage Bonus |
|---|---|---|---|---|
| ⚔ Iron | Lv 1 | Iron armour piece + **Iron Ingot** | ×1.05 | ×1.05 |
| 💎 Diamond | Lv 40 | Diamond armour piece + **Diamond** | ×1.20 | ×1.20 |
| 🌑 Netherite | Lv 70 | Netherite armour piece + **Netherite Ingot** | ×1.40 | ×1.40 |
| 🐉 Dragon | Lv 99 | Netherite armour piece + **Nether Star** + **Dragon Breath** | ×1.75 | ×1.75 |

Dragon Melee Gear has **Protection IV, Thorns III, Unbreaking III, Mending** pre-enchanted + enchantment glow.

---

## Ranged Gear (4 Tiers) + Arrow Speed

Leather/Chainmail/Netherite armour sets gated by **Ranged level**. Each tier gives a ranged damage bonus and contributes to arrow speed.

| Tier | Level | Recipe Ingredient | Ranged Damage | Arrow Speed Bonus/Piece |
|---|---|---|---|---|
| 🏹 Leather | Lv 1 | Leather piece + **String** | ×1.00 | 0 ms |
| ⛓ Chainmail | Lv 40 | Chainmail piece + **Feather** + **Lapis Lazuli** | ×1.15 | +50 ms |
| 🌑 Netherite | Lv 70 | Netherite piece + **Netherite Ingot** + **Feather** | ×1.35 | +100 ms |
| 🐉 Dragon | Lv 99 | Netherite piece + **Nether Star** + **Arrow** | ×1.75 | +200 ms |

### Arrow Speed Scaling (RangedSpeedListener)

Arrow velocity is multiplied based on **Ranged level + gear worn**, mirroring the mage cooldown system:

```
velocity_multiplier = 0.70 + (rangedLevel / 99.0 × 0.50) + (total_gear_drawMs / 2000)
```

| Ranged Level | No Gear | Leather Set | Netherite Set | Dragon Set |
|---|---|---|---|---|
| Lv 1 | 0.71× (slow) | 0.73× | 0.91× | 1.11× |
| Lv 50 | 0.95× | 0.97× | 1.15× | 1.35× |
| Lv 99 | 1.20× | 1.22× | 1.40× | 1.60× |

- **Level 1 with no gear**: arrows are ~30% slower than vanilla (noticeably sluggish)
- **Level 99 + full Dragon Ranged gear**: arrows fly ~60% faster than vanilla — devastating DPS
- Ranged gear also applies the tier's `rangedBonus` as a direct **arrow damage multiplier** (Dragon = ×1.75 damage)
- Dragon Ranged Gear has **Projectile Protection IV, Unbreaking III, Mending** + enchantment glow

---

## GunZ Sword

Admin-spawnable Netherite Sword (Lv 99 Melee required). Equipped from `/gear` or `/registry`.

### Double-Tap Dashing
Hold the GunZ Sword and **double-tap** any movement key:

| Double-tap | Dash Direction |
|---|---|
| **W W** | Forward |
| **S S** | Backward |
| **A A** | Left |
| **D D** | Right |

- Detection: per-tick position sampler (1-tick / 50 ms) tracks direction changes
- A double-tap = same direction pressed → released (≥60 ms hold) → pressed again within 320 ms
- **800 ms cooldown** between dashes
- CRIT + ENCHANTED_HIT particles + sweep sound on trigger

---

## Dark Bow & Dragon Arrows

**Dark Bow** — 1% drop from Warden (Lv 70 Ranged required):
- Normal shot: single arrow (with Dragon Arrow: purple trail + glow effect)
- **Special** (sneak + right-click): fires 2 homing arrows at −35% damage each. Costs 2 Dragon Arrows, 3 s cooldown.

**Dragon Arrow Tip** — drops from Ender Dragon (8–16 per kill)  
**Dragon Arrow** — craft: 4× Dragon Arrow Tips → 4× Dragon Arrows

---

## Boss Events

~1% chance on any mob spawn for a **Double Boss** event:
- Two boss variants spawn with augmented stats
- Players who defeat both **without anyone dying** earn the **Boss Cape**
- Boss mobs also drop the **Ancient Kill Tome** (reveals Air + Frozen/Statue instant-death combos)

---

## Gold Currency

Gold coins drop from mobs (quantity scales with difficulty). Check balance with `/gold`.

Gold is used in the VIP Shop and trade system.

---

## Quest System

Open with `/questbook`. Kill-based quests award gold and skill XP. Quest progress saves automatically.

---

## Party System

```
/party invite <player>  — invite a player
/party leave            — leave your party
```

Parties share a scoreboard HUD showing member difficulty and HP.

Party members within 50 blocks with Nightmare difficulty trigger the **Group Nightmare** ×10 buff.

---

## VIP Shop & Cosmetics

A custom villager NPC that sells cosmetics for gold coins. Spawn the keeper: `/vipshop spawn`

| Item | Price | Effect |
|---|---|---|
| 🦄 Unicorn Slippers | 5,000 gp | Hot-pink leather boots — leaves a rainbow particle trail at feet |
| 🌈 Rainbow Axolotl | 5,000 gp | Legendary cosmetic bucket item — all 5 axolotl variants, enchantment glow |

---

## Miscellaneous Items

| Item | How to Get | Effect |
|---|---|---|
| Enchanted Shard | ~5% drop from any mob | Crafts staffs & Master Mage Gear |
| Soulfur Potion | `/registry` | Causes Nausea + Drunken Sway, repeated doses darken vision |
| Turbo Minecart | `/registry` | 3× faster rail cart |
| Spell Combo Book | 8% drop from mob killed by staff | Passive: shows combo hints in action bar |
| Spell Page | 4% drop from any mob | Right-click to unlock a random Arcane Tome page |
| Arcane Tome | Craft: Book + Amethyst Shard + Purple Dye | 41-page discoverable spell book |
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
| `difficultyengine.cape.admin` | op | Bypass cape/gear level requirements + `/spellbook` admin access |

---

## Data Files

All data is stored under `plugins/DifficultyEngine/`:

| File/Folder | Contents |
|---|---|
| `difficulty/<uuid>.yml` | Per-player difficulty level |
| `skills/<uuid>.yml` | Per-player skill XP/levels |
| `capes/<uuid>.yml` | Equipped cape per player |
| `bags/<uuid>.yml` | Magic Bag contents per player |
| `spellbook_data.yml` | Unlocked Arcane Tome pages (all players) |
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

---

## Changelog (Latest)

### Skills & Gear Systems
- **Melee Gear** — 4 tiers (Iron/Diamond/Netherite/Dragon), gated by Melee level. PDC-tagged, level-enforced equip, damage + defence bonuses. Dragon tier: pre-enchanted with Protection/Thorns/Mending/Unbreaking.
- **Ranged Gear** — 4 tiers (Leather/Chain/Netherite/Dragon), gated by Ranged level. Arrow velocity AND damage now scale with Ranged level + gear tier — level 1 fires ~30% slower arrows, level 99 + Dragon gear fires ~60% faster with ×1.75 damage.
- **Arrow Speed Scaling** — `RangedSpeedListener` applies `EntityShootBowEvent` velocity multiplier: `0.70 + (level/99 × 0.50) + (gearDrawMs/2000)`.

### Arcane Tome Rework
- **Craftable**: Book + Amethyst Shard + Purple Dye → Arcane Tome (all 41 pages start as `???`)
- **41 pages** (expanded from 37): pages 38–41 are visual Mage Gear craft guides with ingredient grids per tier
- `/spellbook` is now **admin-only** — players interact with their crafted tome directly

### Cape System Fixes
- **Armour stand atomic spawn** — `world.spawn(loc, ArmorStand.class, Consumer)` sets all flags before the client packet fires; crosshair never shows "Armour Stand"
- **Fishing Cape dual orbit rings** — 6 axolotls (vertical great-circle) + 8 tropical fish (horizontal equator), persistent and precisely teleported each tick

### Cosmetics
- **Unicorn Slippers** — re-coloured to vivid hot-pink `RGB(255, 105, 180)`
- **Rainbow Axolotl** — new legendary `AXOLOTL_BUCKET` collectible, rainbow enchantment glow, in VIP Shop + registry
