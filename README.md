# ⚔ DifficultyEngine

A **Paper 1.21** plugin that transforms vanilla Minecraft into a deep RPG experience with dynamic mob difficulty scaling, a full elemental magic system, OSRS-style skills, capes, quests, parties, and more.

---

## 🆕 Latest Update — Build `July 2026` (Restart Required)

> **Server admin:** Replace `DifficultyEngine.jar` in `plugins/` and restart.

### What's New This Session

#### ⚔ GunZ Sword — Level 99 Melee Weapon (NEW!)
- Admin-spawnable netherite sword with **GunZ: The Duel**-style dashing
- **Requires Melee Level 99** to wield
- **Double-tap WASD** while holding to dash in any direction:
  - `WW` → Forward dash
  - `AA` → Left dash
  - `DD` → Right dash
  - `SS` → Backward dash
- 0.8-second dash cooldown — weave through combat at blinding speed
- Comes fully enchanted (Sharpness, Knockback, Fire Aspect, Unbreaking, Looting)

#### 🏹 Dark Bow + Dragon Arrows (NEW!)
- **Dark Bow** — Level 70 Ranged weapon with a **1% drop from the Warden**
- Normal shot: single arrow (with Dragon Arrows: **purple particle trail**)
- **Special shot** (SNEAK + Right-click): fires **2 homing arrows** simultaneously, each at −35% damage but dual-hit — costs 2 Dragon Arrows, 3 s cooldown
- **Dragon Arrow** — crafted from Dragon Arrow Tips (dropped by the Ender Dragon)
  - 8–16 Dragon Arrow Tips drop per Ender Dragon kill
  - Craft: `4× Dragon Arrow Tips → 4 Dragon Arrows` (shapeless at any crafting table)
- Tips display with an enchantment glint and purple trail when fired from the Dark Bow

#### 🌿 Earth Block Throwing System
- Earth magic completely reworked at **Magic Level 10+**
- Carry a **throwable block** + matching **Earth Magic Page** in your inventory
- **1st hit → TRAP**: Block placed under target's feet + trap damage + Slowness III
- **2nd hit while trapped → SUFFOCATE**: Blocks over head + heavy damage
- **8 block tiers** — higher tier = more damage:
  | Block | Magic Lv | Trap Damage | Suffocate Damage |
  |-------|----------|------------|-----------------|
  | Dirt | 10 | 2 ❤ | 4 ❤ |
  | Cobblestone | 15 | 3 ❤ | 6 ❤ |
  | Stone | 25 | 5 ❤ | 8 ❤ |
  | Iron Block | 30 | 7 ❤ | 11 ❤ |
  | Gold Block | 50 | 9 ❤ | 14 ❤ |
  | Obsidian | 60 | 12 ❤ | 18 ❤ |
  | Nether Bricks | 75 | 15 ❤ | 22 ❤ |
  | Ancient Debris | 90 | 18 ❤ | 27 ❤ |
- Earth Magic Pages available in `/registry` (page 2) — one per block tier
- All existing Earth combos (WET→MUDDY, BLAZING→SMOTHERED, etc.) still work and take priority
- Block is consumed from inventory on each throw — real resource cost!

#### 🎣 Fishing Cape Improvements
- Fish and axolotl no longer show health bars or floating names above them
- Fish/axolotl are now **passthrough** — you and mobs can walk right through them
- Fixed **double-cape rendering** when looking steeply downward
- Cape animals are silent (no splashing sounds)

---

## 📋 Table of Contents

1. [Installation](#-installation)
2. [Difficulty System](#-difficulty-system)
3. [Skills & Levelling](#-skills--levelling)
4. [🔮 Magic System — Full Guide](#-magic-system--full-guide)
   - [Getting Started with Magic](#getting-started-with-magic)
   - [Elemental Staffs](#elemental-staffs)
   - [Runes — What They Are and How to Get Them](#runes--what-they-are-and-how-to-get-them)
   - [Earth Block Throwing System](#-earth-block-throwing-system)
   - [Why Magic Level Matters](#why-magic-level-matters)
   - [Spell Books & Pages — The Advanced Combo System](#spell-books--pages--the-advanced-combo-system)
   - [Full Combo Table](#full-combo-table)
   - [Sandstorm System](#sandstorm-system)
5. [Mage Gear](#-mage-gear)
6. [Cape Wardrobe](#-cape-wardrobe)
7. [Gold Currency](#-gold-currency)
8. [VIP Shop](#-vip-shop)
9. [Quest System](#-quest-system)
10. [Party System](#-party-system)
11. [Commands Reference](#-commands-reference--player)
12. [Admin Commands](#-admin-commands)

---

## 🔧 Installation

1. Drop `DifficultyEngine.jar` into your server's `plugins/` folder.
2. Requires **Paper 1.21** (or compatible fork).
3. No other dependencies.
4. Start the server — data folders are created automatically.

---

## ⚡ Difficulty System

Mobs scale in difficulty based on each player's settings:

| Difficulty | Mob HP | Mob Damage | Drops |
|---|---|---|---|
| Easy | ×1 | ×1 | Normal |
| Normal | ×1.5 | ×1.25 | +25% |
| Hard | ×2 | ×1.5 | +50% |
| Nightmare | ×3 | ×2 | +100% |

- **Group Nightmare Bonus**: 4+ Nightmare players within 50 blocks → mobs become ×10 difficulty with massive drop bonuses.
- Use `/difficulty` to change your difficulty at any time.
- Use `/hpbar` to toggle HP bars above mobs.

---

## 📈 Skills & Levelling

All skills go from **Level 1 → 99**. XP is earned naturally through gameplay.

| Skill | How to Level |
|---|---|
| **Melee** | Deal melee damage |
| **Ranged** | Deal bow/crossbow damage |
| **Defence** | Take damage from mobs |
| **Prayer** | Right-click bones on dirt to bury |
| **Magic** | Cast spells, land hits, trigger combos |
| **Woodcutting** | Chop trees |
| **Fishing** | Fish |
| **Farming** | Harvest crops |

Use `/skills` or `/mystats` to see your current levels and XP.

**Level 99 in any skill** unlocks that skill's **Cape** (see Cape Wardrobe below).

---

## 🔮 Magic System — Full Guide

The magic system is the most deep and rewarding part of DifficultyEngine. It rewards **skill, timing, and knowledge of combos**. A low-level mage is slow and basic. A high-level mage is a devastating spell-chain machine.

---

### Getting Started with Magic

1. **Get an Enchanted Shard** — drops ~5% from any hostile mob (it's a special Amethyst Shard with a magic tag).

2. **Craft an Elemental Staff** at any crafting table:
   - `Enchanted Shard` + `Element Ingredient` + `Stick`
   - Search the recipe book (green book icon in crafting table) for the exact recipe.

3. **Craft Runes** for your staff element:
   - `4× base material → 8 runes` (see recipe book, search "rune")
   - Runes are consumed one per cast.

4. **Hold the staff** and **right-click** to cast. The bolt travels in the direction you are **looking** — aim with your crosshair.

5. **Gain Magic XP** from casting, hitting targets, and triggering combos.

---

### Elemental Staffs

| Element | Shard + Ingredient + Stick | Rune from | Bolt Effect |
|---|---|---|---|
| 🔥 **Fire** | Enchanted Shard + Blaze Rod + Stick | 4× Nether Brick → 8 runes | Fireball → SCORCHED |
| 💧 **Water** | Enchanted Shard + Prismarine Shard + Stick | 4× Ice → 8 runes | Water bolt → WET |
| 🌿 **Earth** | Enchanted Shard + Emerald + Stick | 4× Clay Ball → 8 runes | Block bolt → TRAP (Lv10+) |
| 💨 **Air** | Enchanted Shard + Phantom Membrane + Stick | 4× Feather → 8 runes | Air bolt → Knockback |

---

### Rune Dust — The Crafting Material

**Rune Dust** is the crafting ingredient for all Elemental Runes. Kill specific mobs or craft it at a Magic Cauldron.

```
4× Rune Dust → 8 Runes   (at any crafting table)
```

One stack of 64 dust = 128 runes = 128 casts!

**No rune = no cast.** You'll see a message in your action bar telling you what to craft.

---

### 🔥 Fire Rune Dust — Mob Loot Table

| Mob | Drop Chance | Dust Amount |
|---|---|---|
| Blaze | 40% | 2–5 |
| Magma Cube (Large) | 30% | 2–4 |
| Magma Cube (Medium) | 20% | 1–2 |
| Magma Cube (Small) | 10% | 1 |
| Ghast | 35% | 3–6 |
| Wither Skeleton | 25% | 1–3 |
| Piglin Brute | 20% | 2–4 |
| Strider | 10% | 1 |
| **Wither (Boss)** | **100%** | **20–30 🔥** |

---

### 💧 Water Rune Dust — Mob Loot Table

| Mob | Drop Chance | Dust Amount |
|---|---|---|
| Drowned | 20% | 1–2 |
| Guardian | 40% | 2–5 |
| Squid | 8% | 1 |
| Glow Squid | 15% | 1–2 |
| Axolotl | 10% | 1 |
| **Elder Guardian (Boss)** | **100%** | **15–25 💧** |

---

### 🌿 Earth Rune Dust — Mob Loot Table

| Mob | Drop Chance | Dust Amount |
|---|---|---|
| Zombie | 15% | 1 |
| Husk | 20% | 1–2 |
| Zombie Villager | 12% | 1 |
| Spider | 12% | 1 |
| Cave Spider | 18% | 1–2 |
| Creeper | 20% | 2–3 |
| Pillager | 25% | 1–2 |
| Vindicator | 25% | 2–4 |
| Ravager | 65% | 6–12 |
| **Warden (Boss)** | **100%** | **25–35 🌿** |

---

### 💨 Air Rune Dust — Mob Loot Table

| Mob | Drop Chance | Dust Amount |
|---|---|---|
| Bat | 5% | 1 |
| Phantom | 35% | 2–4 |
| Vex | 30% | 2–3 |
| Breeze | 60% | 4–8 |
| **Evoker** | **70%** | **5–10 💨** |

---

### 🪣 Magic Cauldron — Bulk Crafting

Use a **Cauldron** as an ingredient at the crafting table to brew large batches of Rune Dust.

> ⚗️ The Cauldron is consumed in the process. Use it as a crafting ingredient alongside the element materials.

#### Basic Recipe (no diamond) → **16 Rune Dust**

| Element | Ingredients |
|---|---|
| 🔥 Fire | `Cauldron` + `Lava Bucket` + `4× Netherrack` |
| 💧 Water | `Cauldron` + `2× Water Bucket` + `4× Prismarine Shard` |
| 🌿 Earth | `Cauldron` + `Water Bucket` + `4× Dirt` |
| 💨 Air | `Cauldron` + `Pufferfish` + `Water Bucket` |

#### Premium Recipe (add 1 Diamond) → **80 Rune Dust** *(5× more!)*

Same as basic, but add `1× Diamond` to the crafting grid.

> 💡 80 Rune Dust → 160 Runes → 160 casts!
> 💡 Water/Lava Buckets return as empty buckets — you only lose the contents.

---

### 🌿 Earth Block Throwing System

At **Magic Level 10+**, the Earth staff transforms into a **block-throwing weapon**.

**Requirements to use:**
1. Magic Level ≥ the block tier's minimum
2. The matching block in your inventory (1 consumed per throw)
3. The matching **Earth Magic Page** in your inventory (permanent, not consumed)

**How it works:**
- **1st earth hit on a player** → Block placed under their feet (TRAP) + trap damage + Slowness III
- **2nd earth hit while they're trapped** → Blocks placed over their head (SUFFOCATE) + heavy damage

**Earth Magic Pages** are found in `/registry` (page 2). Each page unlocks a specific block tier.

**Block Tier Table:**
| Block | Magic Level Needed | Trap Damage | Suffocate Damage |
|---|---|---|---|
| 🟫 Dirt | Lv 10 | 2 ❤ | 4 ❤ |
| 🪨 Cobblestone | Lv 15 | 3 ❤ | 6 ❤ |
| ⬜ Stone | Lv 25 | 5 ❤ | 8 ❤ |
| ⚙️ Iron Block | Lv 30 | 7 ❤ | 11 ❤ |
| 🟡 Gold Block | Lv 50 | 9 ❤ | 14 ❤ |
| 🌑 Obsidian | Lv 60 | 12 ❤ | 18 ❤ |
| 🔴 Nether Bricks | Lv 75 | 15 ❤ | 22 ❤ |
| 🟤 Ancient Debris | Lv 90 | 18 ❤ | 27 ❤ |

**Important notes:**
- Without the Earth Magic Page in your **inventory** (not in a chest), the block won't throw
- The plugin always uses the **highest valid tier** available in your inventory
- Earth combos (WET→MUDDY, BLAZING→SMOTHERED, STATUE→CRUMBLE) still activate first before the trap system
- Below Level 10: old dirt bolt (slowness only) still works

---

### Why Magic Level Matters

> **Higher Magic level = faster casting = more powerful combos.**

Cooldown formula:
```
Cooldown = 3000ms − (level ÷ 99 × 2000ms) − (mage gear bonus)
Minimum cooldown: 500ms
```

| Magic Level | Base Cooldown | With Full Mage Set (4 pieces) |
|---|---|---|
| Lv 1 | 3.0 seconds | 2.0 seconds |
| Lv 20 | 2.6 seconds | 1.6 seconds |
| Lv 50 | 2.0 seconds | 1.0 seconds |
| Lv 75 | 1.5 seconds | 0.5 seconds ← minimum! |
| **Lv 99** | **1.0 second** | **0.5 seconds ← minimum!** |

**Why this matters for combos:**
- **SCORCHED** lasts only **3 seconds** — you need to cast Fire again within 3s to trigger **BLAZING**.
- **CHILLED** lasts only **2.5 seconds** — you need to cast Air within 2.5s to trigger **FROZEN**.

At low levels you physically **cannot chain these combos** because your cooldown is longer than the status window. At Level 99 with Mage Gear you fire every **0.5 seconds** — well within every combo window.

---

### Spell Books & Pages — The Advanced Combo System

Use `/spellbook` to open your personal **Arcane Tome**. It shows all spell pages you've unlocked (out of 37 total).

**How to get Spell Pages:**
- Mobs have a **4% drop chance** when killed
- Admins can give pages with `/spellpage [player]`
- Trade with other players using `/trade`

**How to use:**
1. Get a Spell Page item (looks like a written book)
2. **Right-click** it to consume and unlock a random combo in your tome
3. Type `/spellbook` to read your unlocked combos

---

### Full Combo Table

| First Hit Status | Second Element | Result |
|---|---|---|
| Normal | 🔥 Fire | **SCORCHED** (3s window) |
| SCORCHED | 🔥 Fire | **BLAZING** (5s intense burn) |
| WET | 🔥 Fire | Extinguish — fire cancelled, steam |
| MUDDY | 🔥 Fire | **STATUE** (8s, total immobility) |
| CHILLED | 🔥 Fire | Thaw (steam burst, small dmg) |
| FROZEN | 🔥 Fire | **Thaw EXPLOSION** (AoE damage) |
| Normal | 💧 Water | **WET** (5–10s) |
| SCORCHED | 💧 Water | **Steam Burst** (bonus damage) |
| BLAZING | 💧 Water | **STEAM EXPLOSION** (AoE knockback) |
| FROZEN | 💧 Water | **SLUSH** (Slowness III + Blindness) |
| MUDDY | 💧 Water | **FLOOD WASH** (mud cleared, WET again) |
| Normal | 🌿 Earth | Slowness + dirt at feet |
| TRAPPED | 🌿 Earth | **SUFFOCATE** (tier damage) |
| WET | 🌿 Earth | **MUDDY** (15–30s, Slowness IV) |
| CHILLED | 🌿 Earth | **CRACKED ICE** (Blindness + heavy slow) |
| BLAZING | 🌿 Earth | **SMOTHERED** (extinguish + heavy dmg) |
| STATUE | 🌿 Earth | **CRUMBLE** (bonus dmg, breaks statue) |
| Normal | 💨 Air | Heavy knockback |
| WET | 💨 Air | **CHILLED** (2.5s — act fast!) |
| CHILLED | 💨 Air | **FROZEN** (5s — Air = instant death!) |
| FROZEN | 💨 Air | 💀 **INSTANT DEATH** (shattered) |
| STATUE | 💨 Air | 💀 **INSTANT DEATH** (crumbled) |
| MUDDY | 💨 Air | **MUD LAUNCH** (massive upward catapult) |
| BLAZING | 💨 Air | **INFERNO BLAST** (fire + huge knockback) |
| SCORCHED | 💨 Air | **FANNED FLAMES** (extended fire) |

**Mage Gear bonus (wear 2+ pieces):**
- 5% chance on any combo hit → **MIND BOMB** (Nausea + Blindness 5s)
- 30% chance from Mind Bomb → **FALLEN** (crawl — press SPACE to get up)

---

### Sandstorm System

**Chain:** Water Staff → Earth bolt → Air bolt to unleash it.

1. Use **Water Staff** (right-click a block with Water Bucket) to place water
2. Shoot water with **Earth bolt** → becomes Quicksand (Soul Sand)
3. Shoot quicksand with **Air bolt** → **SANDSTORM!**

- 200-block radius, sandy particles every 5 ticks
- 0.5 ♥ damage every 2 seconds inside the storm
- **💧 Hydration BossBar** — drains in storm, drink Water Bottle to refill
- Each re-cast **doubles** duration, capped at 15 minutes

---

## 🛡 Mage Gear

Craftable leather armour that reduces spell cooldown and boosts air power.

| Tier | Magic Level | Cooldown/piece | Craft Ingredients |
|---|---|---|---|
| Apprentice | Lv 1 | −100ms | Leather piece + Purple Dye + String |
| Mage | Lv 30 | −250ms | Leather piece + Purple Dye + Blaze Powder |
| Alch Mage | Lv 60 | −350ms | Leather piece + Blue Dye + Blaze Powder + Eye of Ender |
| Master Mage | Lv 90 | −500ms | Leather piece + Black Dye + Blaze Powder + Enchanted Shard + Dragon Breath |

- Full 4-piece Master set: **−2000ms cooldown** (brings Lv99 cooldown to 500ms minimum)
- Each piece also boosts Air gust power (full Master = 2× Air knockback)
- Requires the matching Magic level to equip

---

## 🎭 Cape Wardrobe

Use `/mycape` or `/cape` to open the **Cape Wardrobe** GUI.

Two independent slots — **wear armour AND a cape at the same time!**

| Cape | Requirement | Visual Effect |
|---|---|---|
| Melee Cape | 99 Melee | Red crit sparks |
| Ranged Cape | 99 Ranged | Green enchant sparks |
| Defence Cape | 99 Defence | Blue-white End Rod particles |
| Prayer Cape | 99 Prayer | Floating enchant letters |
| Magic Cape | 99 Magic | Rainbow cycling dust sparkles |
| Woodcutting Cape | 99 Woodcutting | Happy Villager particles |
| Fishing Cape | 99 Fishing | Water cascade + tropical fish + axolotl |
| Farming Cape | 99 Farming | Composter + Happy Villager particles |
| Max Cape | 99 in ALL skills | Firework + End Rod bursts |
| Boss Cape | Defeat a Double Boss event | Soul Fire Flame + Soul particles |

Capes are saved per-player and persist through restarts.

---

## 💰 Gold Currency

A custom in-game currency.

- Mobs drop Gold based on difficulty and type
- Check your balance: `/gold`
- Trade gold with players: `/trade`

---

## 🛍 VIP Shop

The **VIP Shop Keeper** is a villager NPC selling cosmetic items for Gold.

| Item | Price | What it does |
|---|---|---|
| 🦄 Unicorn Slippers | 5,000 gp | Creates a rainbow particle trail at your feet while worn |

Admins spawn the VIP keeper with `/vipshop spawn`.

---

## 📜 Quest System

Use `/questbook` to open the Quest Book.

- Quests: kill mobs, gather items, achieve milestones
- Rewards: Gold, XP, special items
- Multiple quest types with progression tracking

---

## 👥 Party System

Group up with friends to share difficulty bonuses and rewards.

- `/party create` — form a party
- `/party invite <player>` — invite someone
- `/party join <player>` — accept an invitation
- `/party leave` — leave your party
- Party HUD shows in the sidebar scoreboard
- Group Nightmare bonus triggers with 4+ Nightmare players within 50 blocks

---

## 📖 Commands Reference — Player

| Command | What it does |
|---|---|
| `/difficulty` | Change your difficulty (Easy / Normal / Hard / Nightmare) |
| `/hpbar` | Toggle mob HP bars on/off above their heads |
| `/sit` | Toggle right-click-to-sit on stairs and slabs |
| `/skills` | Open the Skills GUI — see all your levels and XP |
| `/mystats` or `/stats` | Same as `/skills` |
| `/cape` or `/mycape` | Open the Cape Wardrobe (wear armour + cape at the same time!) |
| `/gold` | Check your gold coin balance |
| `/questbook` | Open your Quest Journal |
| `/party invite <player>` | Invite a player to your party |
| `/party join <player>` | Join someone's party |
| `/party leave` | Leave your current party |
| `/party info` | See current party members |
| `/trade <player>` | Open a trade window with a nearby player |
| `/spellbook` | Read your Arcane Tome — shows all unlocked spell combos |
| `/registry` | Open the Item Registry — browse all custom items |

---

## 🛡 Admin Commands

| Command | What it does |
|---|---|
| `/gear` | Give yourself max-enchanted netherite god gear (testing) |
| `/curecosmetic` | Remove all cosmetic effects from yourself |
| `/adminlight` | Toggle a personal light source that follows you |
| `/spellpage [player]` | Give a Spell Page to yourself or another player |
| `/vipshop spawn` | Spawn the VIP Shop Keeper villager at your location |
| `/registry` | Browse and spawn any custom item (requires op) |

---

## ⚔ Special Weapons

### GunZ Sword

An admin-spawnable Level 99 Melee netherite sword with **GunZ: The Duel**-style momentum dashing.

| Stat | Value |
|---|---|
| Required Level | Melee 99 |
| How to get | Admin spawn via `/registry` |
| Dash cooldown | 0.8 seconds |

**Controls:**
- **Double-tap W** → Forward dash
- **Double-tap A** → Left dash
- **Double-tap D** → Right dash
- **Double-tap S** → Backward dash

Each dash flings you in that direction — chain them in combat to dodge and strike from unexpected angles.

---

### 🏹 Dark Bow

A Level 70 Ranged bow that drops from the **Warden** (1% chance).

| Stat | Value |
|---|---|
| Required Level | Ranged 70 |
| Drop source | Warden — 1% |
| Special cooldown | 3 seconds |

**How to use:**
- **Normal draw & release**: fires a single arrow. With Dragon Arrows loaded: adds a **purple particle trail** and applies a Glowing effect on hit.
- **SNEAK + Right-click**: fires **2 homing arrows** instantly at −35% damage each (but both can hit — equal or greater total damage). Costs **2 Dragon Arrows**.

---

### 🐉 Dragon Arrows

Crafted from **Dragon Arrow Tips**, which drop from the **Ender Dragon** (8–16 tips per kill).

| Item | Craft / Drop |
|---|---|
| Dragon Arrow Tip | Ender Dragon — 8–16 per kill |
| Dragon Arrow (4×) | `4× Dragon Arrow Tips` → shapeless craft |

Dragon Arrows are used as ammunition for the Dark Bow. Load them like normal arrows.

---

##  Other Drops & Items

| Item | How to get | What it does |
|---|---|---|
| Enchanted Shard | ~5% from any hostile mob | Crafting ingredient for staffs & Master Mage Gear |
| Spell Page | 4% from any mob | Right-click to unlock a combo in your Arcane Tome |
| Spell Combo Book | 8% from mobs killed by staff | Carry it for combo hints in action bar |
| Ancient Kill Tome | Double Boss event reward | Reveals instant-death combo hints |
| Earth Magic Page (×8) | `/registry` page 2 | Required to throw each block tier with Earth staff |
| GunZ Sword | Admin spawn (`/registry`) | Lv 99 Melee — GunZ-style WASD dashing |
| Dark Bow | 1% drop from Warden | Lv 70 Ranged — homing special shot |
| Dragon Arrow Tip | Ender Dragon (8–16/kill) | Craft into Dragon Arrows |
| Dragon Arrow (4×) | Craft: 4× Tips → 4 Arrows | Used with Dark Bow for purple trail + homing |
| Unicorn Slippers | VIP Shop — 5,000 gp | Rainbow trail at your feet |
| Soulfur Potion | Craftable | Causes Nausea + Drunken Sway |
| Turbo Minecart | `/registry` (admin only) | 3× faster minecart |

---

## 🏗 Building from Source

```bash
git clone https://github.com/unleashbuildwithai/DifficultyEngine.git
cd DifficultyEngine
mvn clean package
```

The shaded JAR will be in `target/DifficultyEngine-1.0.jar`.

The `seperate/` folder contains the **SeparatePlug** — an independent PvP spirit plugin. Build separately:

```bash
cd seperate
mvn clean package
```

---

## 📄 License

MIT — free to use, modify, and distribute.
