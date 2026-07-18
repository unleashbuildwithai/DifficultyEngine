# ⚔ DifficultyEngine

A **Paper 1.21** plugin that transforms vanilla Minecraft into a deep RPG experience with dynamic mob difficulty scaling, a full elemental magic system, OSRS-style skills, capes, quests, parties, and more.

---

## 📋 Table of Contents

1. [Installation](#-installation)
2. [Difficulty System](#-difficulty-system)
3. [Skills & Levelling](#-skills--levelling)
4. [🔮 Magic System — Full Guide](#-magic-system--full-guide)
   - [Getting Started with Magic](#getting-started-with-magic)
   - [Elemental Staffs](#elemental-staffs)
   - [Runes — What They Are and How to Get Them](#runes--what-they-are-and-how-to-get-them)
   - [Why Magic Level Matters](#why-magic-level-matters)
   - [Spell Books & Pages — The Advanced Combo System](#spell-books--pages--the-advanced-combo-system)
   - [Full Combo Table](#full-combo-table)
   - [New Element × Block Interactions](#new-element--block-interactions)
   - [Sandstorm System](#sandstorm-system)
5. [Cape Wardrobe](#-cape-wardrobe)
6. [Gold Currency](#-gold-currency)
7. [Quest System](#-quest-system)
8. [Party System](#-party-system)
9. [Commands Reference](#-commands-reference)
10. [Admin Commands](#-admin-commands)

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

1. **Craft an Elemental Staff** at any crafting table:
   - `Enchanted Shard` (Amethyst Shard with magic PDC) + `Element Ingredient` + `Stick`
   - See the recipe book (green book in crafting table) for exact ingredients.

2. **Craft or find Runes** for your staff element.
   - Runes are consumed one per cast.
   - `4× base material → 8 runes` (see recipe book).

3. **Hold the staff** and **right-click** to cast. The bolt travels in the direction you are **looking** — aim with your crosshair.

4. **Gain Magic XP** from casting, hitting targets, and triggering combos.

---

### Elemental Staffs

| Element | Staff Ingredient | Rune Ingredient | Bolt Effect |
|---|---|---|---|
| 🔥 **Fire** | Blaze Rod | 4× Blaze Powder | Firebomb — SCORCHED (3s) |
| 💧 **Water** | Prismarine Shard | 4× Clay Ball | Water bolt — WET (5-10s) |
| 🌿 **Earth** | Dirt | 4× Sand | Earth bolt — slow + dirt block |
| 💨 **Air** | Feather | 4× String | Air bolt — massive knockback |

---

### Rune Dust — The Crafting Material

**Rune Dust** is the crafting ingredient for all Elemental Runes. Kill specific mobs or craft it at a Magic Cauldron.

```
4× Rune Dust → 8 Runes   (at any crafting table)
```

Keep a stack of Rune Dust in your inventory as a reserve. One stack of 64 dust = 128 runes = 128 casts!

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
| **Wither** | **100%** | **20–30 🔥** |

---

### 💧 Water Rune Dust — Mob Loot Table

| Mob | Drop Chance | Dust Amount |
|---|---|---|
| Drowned | 20% | 1–2 |
| Guardian | 40% | 2–5 |
| Squid | 8% | 1 |
| Glow Squid | 15% | 1–2 |
| Axolotl | 10% | 1 |
| **Elder Guardian** | **100%** | **15–25 💧** |

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
| **Warden** | **100%** | **25–35 🌿** |

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

> ⚗️ The Cauldron acts as your "magic cauldron" — place it in the crafting grid along with the specific element ingredients. The cauldron is consumed in the process.

#### Basic Recipe (no diamond) → **16 Rune Dust**

| Element | Ingredients |
|---|---|
| 🔥 Fire | `Cauldron` + `Lava Bucket` + `4× Netherrack` |
| 💧 Water | `Cauldron` + `2× Water Bucket` + `4× Prismarine Shard` |
| 🌿 Earth | `Cauldron` + `Water Bucket` + `4× Dirt` |
| 💨 Air | `Cauldron` + `Pufferfish` + `Water Bucket` |

#### Premium Recipe (add 1 Diamond) → **80 Rune Dust** *(5× more!)*

Same as basic, but add `1× Diamond` to the crafting grid.

> 💡 **Math tip:** 80 Rune Dust → 160 Runes → 160 casts! Diamonds are valuable, but this is worth it for serious mages.
>
> 💡 **Note:** When using a Lava or Water Bucket in a recipe, you get back the Empty Bucket automatically. You only lose the contents (lava/water), not the bucket itself.

---

### Rune Dust → Runes Conversion

Once you have Rune Dust, convert it at any crafting table:

```
4× [Element] Rune Dust → 8× [Element] Runes
```

This is the vanilla shapeless recipe — visible in the **recipe book** (green book in crafting table). Search for "rune".

---

### Why Magic Level Matters

> **Higher Magic level = faster casting = more powerful combos.**

This is the single most important reason to grind Magic. The cooldown formula is:

```
Cooldown = 3000ms − (level ÷ 99 × 2000ms) − (mage gear pieces × 250ms)
Minimum cooldown: 500ms
```

In plain numbers:

| Magic Level | Base Cooldown | With Full Mage Gear (4 pieces) |
|---|---|---|
| Lv 1 | 3.0 seconds | 2.0 seconds |
| Lv 20 | 2.6 seconds | 1.6 seconds |
| Lv 50 | 2.0 seconds | 1.0 seconds |
| Lv 75 | 1.5 seconds | 0.5 seconds ← minimum! |
| **Lv 99** | **1.0 second** | **0.5 seconds ← minimum!** |

**Why this matters for combos:**

Elemental combos work because status effects expire. For example:
- **SCORCHED** lasts only **3 seconds** — you need to cast Fire again within 3s to trigger **BLAZING**.
- **CHILLED** lasts only **2.5 seconds** — you need to cast Air within 2.5s to trigger **FROZEN**.

At low Magic levels you physically **cannot chain these combos** because your cooldown is longer than the status window. At Level 99 with Mage Gear you are firing every **0.5 seconds** — well within every combo window.

**Mage Gear** is craftable leather armour (LEATHER_PIECE + PURPLE_DYE + BLAZE_POWDER) that reduces your cooldown by 250ms per piece worn.

---

### Spell Books & Pages — The Advanced Combo System

> Spell Books teach you the **advanced block-interaction combos** that are too powerful to know by default. Find the pages by exploring and fighting.

#### The Arcane Tome

Use `/spellbook` to open your personal **Arcane Tome**. It shows all the spell pages you have unlocked so far (out of 37 total). Each unlocked page reveals one advanced spell combo in readable book form.

At first your tome is empty — you know nothing beyond the basic 4 elements. As you find pages and unlock them, your tome fills with powerful knowledge.

#### Spell Pages

**How to get Spell Pages:**
- Mobs have a **4% drop chance** for a Spell Page when killed.
- Admins can give pages with `/spellpage [player]`.
- Pages may also be found in chests (server-dependent loot).
- Trading with other players (use `/trade`).

**How to use a Spell Page:**
1. Get a Spell Page item (looks like a written book).
2. **Right-click** it.
3. A random unlocked combo is revealed in your Arcane Tome.
4. The page is consumed.

**How many pages are there?**
There are **37 total pages** covering all the advanced combos. `/spellbook` shows your count as `X / 37 pages unlocked`.

#### What Do Pages Unlock?

Pages unlock knowledge of **advanced block-interaction combos** including:
- **Fire evaporates Water blocks** (fire bolt → placed water = steam burst)
- **Earth on Water = Quicksand** (earth bolt → water block = soul sand that slows enemies)
- **Air on Quicksand = Sandstorm** (the most powerful environmental combo — see below)
- **Earth × Earth suffocation** (2 earth bolts on same target within 10s = buried alive)
- **Higher-level elemental chains** that aren't active until learned

> Think of pages like the OSRS Quest unlock system — you learn the recipe by reading it. Until you've read the page, you won't know the combo even exists.

---

### Full Combo Table

**Status effects on entities:**

| Combo | Trigger | Result |
|---|---|---|
| Fire → dry | Normal hit | **SCORCHED** (3s window) |
| Fire → SCORCHED | Hit scorched target | **BLAZING** (5s, intense burn) |
| Fire → WET | Hit wet target | Extinguish (fire cancelled, steam) |
| Fire → MUDDY | Hit muddy target | **STATUE** (8s, total immobile) |
| Fire → CHILLED | Hit chilled target | Thaw (steam burst, small damage) |
| Fire → FROZEN | Hit frozen target | Thaw Explosion (huge AoE damage) |
| Water → dry | Normal hit | **WET** (5-10s) |
| Water → SCORCHED | Hit scorched target | Steam Burst (bonus damage) |
| Water → BLAZING | Hit blazing target | **STEAM EXPLOSION** (AoE knockback + damage) |
| Earth → dry | Normal hit | Slowness (brief) + **dirt block at feet** |
| Earth × 2 | 2nd earth hit within 10s | **SUFFOCATE** (buried in dirt 5s, Slowness 255) |
| Earth → WET | Hit wet target | **MUDDY** (15-30s, Slowness IV) |
| Earth → CHILLED | Hit chilled target | Cracked Ice (Blindness + heavy Slow + damage) |
| Earth → STATUE | Hit statue target | Crumble (bonus damage, removes statue early) |
| Air → dry | Normal hit | Heavy knockback |
| Air → WET | Hit wet target | **CHILLED** (2.5s — *short window, act fast!*) |
| Air → CHILLED | Hit chilled target | **FROZEN** (5s — Air = instant death!) |
| Air → FROZEN | Hit frozen target | 💀 **INSTANT DEATH** (shattered) |
| Air → STATUE | Hit statue target | 💀 **INSTANT DEATH** (crumbled) |
| Air → MUDDY | Hit muddy target | **MUD LAUNCH** (massive upward catapult) |
| Air → BLAZING | Hit blazing target | **INFERNO BLAST** (fire + huge knockback) |
| Air → SCORCHED | Hit scorched target | **FANNED FLAMES** (extended fire) |

**Mage Gear bonus (2+ pieces):**
- 5% chance on any hit → **MIND BOMB** (Nausea + Blindness 5s)
- 30% chance from Mind Bomb → **FALLEN** (crawl pose — press SPACE to get up)

---

### New Element × Block Interactions

These combos interact with **placed blocks in the world** rather than entities:

| Combo | How | Result |
|---|---|---|
| Fire bolt → Water block | Shoot a water block | Block evaporates (steam particles, fire extinguish sound) |
| Earth bolt → Water block | Shoot a water block | Block becomes **Quicksand** (Soul Sand) |
| Air bolt → Quicksand block | Shoot the soul sand | Triggers **SANDSTORM** |
| Earth bolt → any solid block | Shoots a wall/floor | Places a temp Dirt block on the hit face (auto-removes 30s) |

> 💡 To create a water block to interact with, use the Water Staff: right-click a block with a Water Bucket in your inventory to place a 5-block water stream.

---

### Sandstorm System

The **most powerful environmental spell** in the game. Chain: Water Staff → Earth bolt → Air bolt to unleash it.

**How to trigger:**
1. Use **Water Staff** (right-click a block with a Water Bucket) to place water.
2. Shoot it with an **Earth bolt** → water becomes Quicksand (Soul Sand).
3. Shoot the quicksand with an **Air bolt** → **SANDSTORM TRIGGERED!**

**What it does:**
- Covers a **200-block radius** around the quicksand block.
- Fills the area with swirling **sandy particles** (sand, gravel, soul sand BLOCK particles) every 5 ticks.
- Deals **0.5 ♥ damage every 2 seconds** to every player inside the radius.
- All players in the storm see a **💧 Hydration BossBar** (8 levels).

**Dehydration:**
- Inside the storm, your Hydration drains **1 level every 2 seconds**.
- At 0 Hydration: **Weakness I** effect applies.
- **Drink a Water Bottle** to instantly restore full hydration.
- The bar hides when you leave the storm area.

**Duration rules:**
| Cast | Duration |
|---|---|
| First trigger | 15 seconds |
| Cast again on same storm | Doubles (30s) |
| Cast again | Doubles (1 min) |
| Cast again | Doubles (2 min) |
| … | … |
| Cap | **15 minutes maximum** |

Re-casting after the storm expires starts it fresh at 15 seconds.

---

## 🎭 Cape Wardrobe

Use `/mycape` or `/cape` to open the **Cape Wardrobe** GUI.

The wardrobe has **two independent slots**:

| Slot | What it does |
|---|---|
| **Left (Armour slot)** | Your chestplate armour — any chestplate, fully independent |
| **Right (Cape slot)** | Your skill cape — stored separately, particles + hologram shown on your back |

> ✨ You can now wear **both armour and a cape simultaneously**.

**Capes require Level 99** in their skill:

| Cape | Requirement | Visual Effect |
|---|---|---|
| Melee Cape | 99 Melee | Red crit sparks |
| Ranged Cape | 99 Ranged | Green enchant sparks |
| Defence Cape | 99 Defence | Blue-white End Rod particles |
| Prayer Cape | 99 Prayer | Floating enchant letters |
| Magic Cape | 99 Magic | **Rainbow cycling dust sparkles** |
| Woodcutting Cape | 99 Woodcutting | Happy Villager particles |
| Fishing Cape | 99 Fishing | Water cascade + **tropical fish** + **axolotl cameos**! |
| Farming Cape | 99 Farming | Composter + Happy Villager particles |
| Max Cape | 99 in ALL skills | Firework + End Rod bursts |
| Boss Cape | Defeat a Double Boss event | Soul Fire Flame + Soul particles |

Capes are saved per-player and persist through restarts.

---

## 💰 Gold Currency

A custom in-game currency separate from vanilla items.

- Mobs drop Gold based on their difficulty and type.
- Check balance: `/gold`
- Gold can be traded between players using the Trade Stone system (`/trade`).

---

## 📜 Quest System

Use `/questbook` to open the Quest Book.

- Quests involve killing specific mobs, gathering items, or achieving milestones.
- Rewards: Gold, XP, special items.
- Multiple quest types with progression tracking.

---

## 👥 Party System

Group up with friends to share difficulty bonuses and rewards.

- `/party create` — form a party
- `/party invite <player>` — invite someone
- `/party join <player>` — accept an invitation
- `/party leave` — leave your party
- Party HUD shows in the sidebar scoreboard.
- Group Nightmare bonus triggers when 4+ Nightmare players are within 50 blocks.

---

## 📖 Commands Reference

| Command | Description |
|---|---|
| `/difficulty` | Change your difficulty level |
| `/hpbar` | Toggle mob HP bars |
| `/sit` | Sit down |
| `/skills` | Open the Skill Tree GUI |
| `/mystats` or `/stats` | View your skill levels and XP |
| `/cape` or `/mycape` | Open the Cape Wardrobe |
| `/gold` | Check your gold balance |
| `/questbook` | Open the Quest Book |
| `/party` | Party management |
| `/trade` | Open a trade with a nearby player |
| `/spellbook` | Read your Arcane Tome (unlocked spell combos) |
| `/registry` | Open the Item Registry (all craftable items) |

---

## 🛡 Admin Commands

| Command | Description |
|---|---|
| `/gear` | Spawn gear for testing |
| `/curecosmetic` | Remove cosmetic effects from yourself |
| `/adminlight` | Toggle admin lighting |
| `/spellpage [player]` | Give a Spell Page to a player (or yourself) |

---

## 🏗 Building from Source

```bash
git clone https://github.com/unleashbuildwithai/DifficultyEngine.git
cd DifficultyEngine
mvn clean package
```

The shaded JAR will be in `target/DifficultyEngine-1.0.jar`.

The `seperate/` folder contains the **SeparatePlug** — a completely independent PvP spirit plugin. Build it separately:

```bash
cd seperate
mvn clean package
```

Both JARs can run on the same server with zero conflicts.

---

## 📄 License

MIT — free to use, modify, and distribute.
