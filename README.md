# DifficultyEngine — Minecraft Plugin

A comprehensive skill & combat enhancement plugin for Paper/Spigot servers.

---

## 🆕 Latest Changes (July 2026)

### ⚡ Lightning Catching Block
- Place a Catching Block (Lodestone) and right-click it to open its GUI.
- Store Empty Magic Bottles in the block.
- When a lightning strikes a nearby Lightning Rod during rain, an empty bottle converts to a **Charged Magic Bottle** (4 casts).
- Drink a Charged Magic Bottle to store **Lightning Charges** (displayed over food bar), or use it directly to cast lightning and retain 3 charges!

### 🔥 Crimson Boss Mechanics
- The Infernal Blazefiend now spawns via a dramatic sequence: Warden emerge sound, 5 random enderman-style teleports, a custom 6-bolt lightning cluster, and a global sky flash in a 200-block radius.
- **Phase 2:** At ≤ 30% HP, changes to `SOUL_FIRE_FLAME` particles, moves faster, and its physical damage is doubled.

### 🌌 Ancient Debris Portal
- Striking a standard 4x5 nether portal frame made out of **Ancient Debris** will ignite it with special water blocks.
- Entering the portal triggers a glitch/nausea effect and teleport players to the Ancient Realm safely to `Y=77.0`.

### 🎢 Turbo Minecart Slope Booster
- Turbo Minecarts now pre-read the track up to 5 blocks ahead.
- If a slope or vertical transition is detected, the cart injects a **2.5x velocity multiplier** to override vanilla friction and prevent the cart from losing momentum and stalling when going up or down!

### 🎇 Staff Particle Fix
- Elemental staff glow particles now render at **back-right of hand** using horizontal-only yaw math
- Looking straight down no longer brings particles into your first-person view
- All 4 elements (Fire, Water, Earth, Air) affected

### 🌿 Earth Staff — No-Book Fallback
- Without an Earth Magic Page or block in inventory, the earth staff now fires a **plain dirt bolt** (scales with your magic level) instead of refunding the rune and stopping
- Earth staff at Lv10+ no longer shows the confusing "click support staff" message

### 🪄 Support Staff — Full System

**Usage:** `Book + Nether Star + Blaze Rod + Prismarine Crystals + Emerald + Feather` (shapeless)

**Cost per use:** 1× Support Rune + 1× Cooked Mutton OR Baked Potato

**Combo gate (party buff mode):**
1. Left-click a party member while holding the Support Staff
2. Right-click the staff within 5 seconds
3. Applies full support buffs to that party member based on which **Support Pages** you carry

**Without combo (splash mode):**
- Heals party members within 8 blocks
- Damages non-party players/mobs within 8 blocks

**Support Rune:** Craft 4× Phantom Membrane → 8× Support Runes

**Support Pages (carry in inventory to unlock that buff):**
| Page | Effect |
|------|--------|
| Healing | +3 ❤ instant heal |
| Faster Speed | Speed II (20s) |
| Defence | Resistance II (15s) |
| Combat Boost | Strength I (15s) |
| Strength | Strength II (10s) |
| Crit Attack | Luck II (20s) |
| Prayer Pierce | Haste I + removes Resistance |

All support items available in **Registry Page 9** (`/registry`)

### ⚔️ GunZ Sword — Dash Overhaul
- **100% reliable double-tap detection** using ring-buffer keystroke system
- Every keypress is registered the instant direction goes from inactive→active (no hysteresis delay)
- `WW` = Forward dash | `AA` = Left | `DD` = Right | `SS` = Back
- **Slash Cancel (Animation Cancel):** Left-click within 350ms of a dash to redirect velocity in your current facing direction — change direction mid-dash!
- Dash cooldown: 750ms

### 🐟 Fishing Cape Axolotl
- Swim distance increased: 0.65 → **3.2 blocks** behind player
- Vertical amplitude increased: 0.25 → **1.2 blocks** (full up-down swimming range)
- Side-to-side amplitude increased: 0.80 → **1.8 blocks** (wider sweeping path)

### 🗺️ Boss Room Quests (IDs 301–306)
Six new secret quests themed to each boss room, with NPCs placed far from the dungeon entrances:

| Quest | Boss Room | Theme |
|-------|-----------|-------|
| Depths of the Abyss | Abyssal Chamber | Kill 50 Guardians |
| Embers of the Pit | Crimson Pit | Kill 30 Wither Skeletons |
| Verdant Curse | Verdant Shrine | Kill 60 Cave Spiders |
| Storm Wrath | Tempest Sanctum | Kill 40 Phantoms |
| The Void Stirs | Void Sanctum | Kill 100 Endermen |
| Gold of the Sanctum | Gilded Sanctum | Collect 5 Gold Blocks |

**Boss Room Coordinates:**
- Abyssal Chamber: `-21, -39, -69` (Water/Ocean — Dark Prismarine, Sea Lanterns)
- Crimson Pit: `-108, -26, -14`
- Verdant Shrine: `60, -43, 100`
- Tempest Sanctum: `115, -38, -47`
- Void Sanctum: `-16, -57, 99`
- Gilded Sanctum: `-14, -42, 267`

---

## 📦 Systems Overview

### 🎯 Skills (8 Skills)
- **Melee** — Combat XP from melee hits. Gear: Iron/Diamond/Netherite/Dragon sets
- **Ranged** — Ranged XP from bow hits. Gear: Leather/Chain/Netherite/Dragon sets
- **Defence** — Passive XP while taking hits
- **Prayer** — Reduces incoming magic damage
- **Magic** — Cast elemental spells. Gear: Apprentice/Mage/Alch/Master sets
- **Woodcutting** — XP from chopping trees
- **Fishing** — XP from catching fish
- **Farming** — XP from harvesting crops

### 🔮 Magic Staff System

**4 Elemental Staffs:**
- 🔥 **Fire** — Fireballs (SCORCHED → BLAZING → INFERNO VORTEX chain)
  - Lv99: Right-click = Lightning Strike (permanent burn until food/water magic)
- 💧 **Water** — Bolts (WET → MUDDY/CHILLED chains)
- 🌿 **Earth** — Block throwing Lv10+ (TRAP → SUFFOCATE system, 8 block tiers)
- 💨 **Air** — Gust (knockback + FROZEN/SHATTER instant-kill chain), hover in air

**Earth Magic Pages** (8 tiers, Registry Page 6):
Each page unlocks a block type for throwing. Carry page + blocks → Earth Staff throws them.

**Status Effect Chains:**
```
WET → MUDDY → STATUE → ☠ CRUMBLED (Air)
WET → CHILLED → FROZEN → ☠ SHATTERED (Air)
SCORCHED → BLAZING → INFERNO BLAST (Air)
```

**Support Staff** — See above section

### ⚔️ GunZ Sword
Admin-only Netherite Sword with GunZ: The Duel dashing mechanics.
Double-tap WASD to dash. Slash (left-click) mid-dash to redirect.

### 🏹 Dark Bow
Level 70 Ranged weapon (1% Warden drop).
- Normal shot: single arrow
- With Dragon Arrows: purple trail
- SNEAK + Right-click: fires 2 homing arrows instantly (costs 2 Dragon Arrows)

### 🎒 Magic Bag
Portable 4-section storage for magic items. Death-proof. Right-click to open.
Craft: Chest + Ender Pearl + Amethyst Shard + Purple Dye + String

### 🎪 Party System
`/party invite <player>` | `/party accept` | `/party leave` | `/party kick <player>`
- Party members share XP area bonuses
- Nightmare mode: party of 5 → 10× mob difficulty
- Support Staff detects party members for combo-gate and splash healing

### 🏆 Quest System (306 Quests)
- 150 Main quests (ids 1–150) — required for Quest Skill Cape
- 150 Secret quests (ids 151–300) — required for Boss Quest Cape
- 6 Boss Room quests (ids 301–306) — secret bonus content

### 👑 Capes (Skill Capes)
Each skill has a cape unlocked at max level (Lv99).
Special capes:
- **Boss Quest Cape** — Complete all 300+ secret quests
- **Max Cape** — All skills at 99

### 💰 Gold Currency
Drops from all mobs. Used in VIP Shop. `/gold` to check balance. `/inventory` to open vault.

### 🌑 Nightmare Mode
`/difficulty nightmare` — 10× mob health, 3× damage, party of 5 required.
Hardcore variant available via `/hardcore`.

---

## 🛠️ Commands

| Command | Description |
|---------|-------------|
| `/skills` or `/mystats` | Open skill GUI |
| `/registry` | Browse all custom items (9 pages) |
| `/party` | Manage party |
| `/questbook` | Open quest journal |
| `/magicbag` | Open Magic Bag |
| `/inventory` | Open Gold Vault |
| `/gold` | Check gold balance |
| `/cape` | Open Cape Wardrobe |
| `/spellbook` | Read Arcane Tome (admin) |
| `/spawnboss tempest\|crimson` | Spawn dungeon boss |
| `/spawnmob <id>` | Spawn custom monster |
| `/skilllvl <player> <skill> <level>` | Admin: set skill level |
| `/hardcore` | Activate Nightmare Hardcore |
| `/commands` | Show help message |

---

## 🔗 Links
- [GitHub](https://github.com/unleashbuildwithai/DifficultyEngine)
- [Discord](https://discord.gg/SreKERPhNB)
