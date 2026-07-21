# DifficultyEngine — Minecraft Plugin

A comprehensive skill & combat enhancement plugin for Paper/Spigot servers.

---

## 🆕 Latest Changes (July 2026)

### ⚡ Lightning Catching Block (Automated & Redesigned)
- Place a Catching Block (Lodestone) and right-click it to open its GUI.
- **Automatic Hopper Support:** Place hoppers above the block to push Empty Magic Bottles in, and hoppers underneath to pull Charged Magic Bottles out!
- **Stacked Layout:** GUI has been redesigned to support stacked inputs (Slot 2 for Empty Bottles, Slot 6 for Charged Bottles) with arrow flow pointers.
- When a lightning strikes a nearby Lightning Rod during rain, an empty bottle converts to a **Charged Magic Bottle** (4 casts).
- **Charged Magic Bottle Drinking:** Drink a Charged Magic Bottle to store **Lightning Charges** (if Magic Lv 99+), or receive a 5-minute surge of lightning power (+50% damage against monsters, +30% against players) if under Magic Lv 99!
- Fire Staff right-click lightning strike now requires and consumes 1 absorbed Lightning Charge per cast (Admins get infinite, toggleable).

### 🔥 World Boss Spawners & Teleports
- All boss spawners (`/spawnboss tempest` and `/spawnboss crimson`) now correctly spawn the bosses directly in the **`ancient_realm`** dimension instead of the Overworld.
- **Admin Command:** `/tpboss <tempest|crimson>` (aliases `/bosstp`) instantly teleports you to the boss arenas inside the `ancient_realm` for testing or engagement.

### 🌌 Ancient Debris Portal (Nether Portal Block + Aura)
- Striking a standard 4x5 portal frame made out of **Ancient Debris** with your Fire Staff's real Lightning Strike will ignite a purple **Nether Portal** block frame!
- Custom block physics listener protects these portal blocks from popping, and they are surrounded by a gorgeous swirling magical particle aura (lime-green, electric blue, and soul fire).
- Entering the portal triggers a glitch/nausea effect and teleports players to the Ancient Realm safely to `Y=77.0`.

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

### 🔮 Magic System, Elemental Combat & Dual-Click Blueprint

The magic system is built around a highly responsive, predictable dual-input layout with Frictionless Fallbacks and strategic synergies.

#### 🎮 Predictable Input Mapping & Controls

Every elemental staff features a clean split between **Left-Click (Offensive elemental blast)** and **Right-Click (Utility / Special / Capstone action)**:

| Staff | 🎯 Left-Click (Basic / Sustained) | ⚡ Right-Click (Utility / Special / Capstone) |
|:---|:---|:---|
| 🔥 **Fire** | **Fire Blast Projectile**<br>• *With Book & Runes (Lv10+):* Automatically applies **Burn** (`SCORCHED` status). Duration scales with Magic Level. Acts as the primary combo trigger.<br>• *Without Book:* Standard Fire Blast (deals damage, no status/burn). | **Lightning Strike** (Lv99 Capstone)<br>• Spawns a real vertical particle bolt (no screen flash).<br>• Inflicts **permanent burn** on players (requires target to cleanse using food or Water Staff magic). |
| 💧 **Water** | **Water Bolt Projectile**<br>• Applies `WET` status chain.<br>• Naturally extinguishes fire and clears permanent lightning burn effects on self & allies. | **Downpour Spell** (Lv10+ Special)<br>• Requires carrying **The Water Book** (inventory/Magic Bag).<br>• Takes **10 seconds to channel** (restricts movement & hand-held item).<br>• Spawns a localized water puddle (lasts 5s). Standing in it grants a non-refreshing **30s Support buff** operational window. |
| 🌿 **Earth** | **Fallback Dirt Bolt**<br>• Fast, lower-tier bolt scaling with magic level.<br>• Consumes **no blocks or pages**, ensuring players never dead-click in combat. | **Heavy Block-Throwing** (Tiers 1–8)<br>• Requires carrying the corresponding block & **Earth Magic Page**.<br>• **1st Hit:** Traps target (spawns block under target, applies heavy Slowness).<br>• **2nd Hit:** Crushes target with massive **Suffocate** damage.<br>• **Smart Downgrade Logic:** If you carrying multiple pages and run out of a higher-tier block, the system automatically checks for the next highest available tiered page/block combo and drops down seamlessly. |
| 💨 **Air** | **Quick Wind Gust**<br>• Immediate high knockback and spacing.<br>• Primarily used to trigger instant-kill combos on frozen/statue targets. | **Air Hover / Flight**<br>• Drains Air Runes while hovering (scales with level and Mage Gear worn).<br>• Below Lv99: Floating slow-fall boost.<br>• At Lv99: True continuous levitation/flight. |

---

#### 🪄 Support Staff System (Full Specs)

* **Crafting Recipe (Shapeless):** `Book + Nether Star + Blaze Rod + Prismarine Crystals + Emerald + Feather`
* **Cost per use:** `1x Support Rune + (1x Cooked Mutton OR Baked Potato)` (Runes crafted from `4x Phantom Membrane → 8x Support Runes`).
* **Dual-Mode Mechanics:**
  * **Party Buff Mode (Combo Gate):** 
    1. **Left-click** a party member while holding the Support Staff to target/mark them.
    2. **Right-click** within 5 seconds to apply full buffs based on active Support Pages in your inventory.
    3. **Water Synergy:** If you are within the Water Downpour's 30s Support Window, healing is **doubled** and potion durations are **increased by 50%**!
  * **Splash Mode (Raw / No Combo):** 
    * Right-clicking without a targeted party member fires an **8-radius AoE burst** instantly healing party members (+2❤ + Regen) or damaging non-party entities.

##### Support Page Buffs (Carry in inventory/Magic Bag to unlock):
* **Healing (Vitality Surge):** `+3❤` instant heal (increases to `+6❤` inside Water Support window).
* **Faster Speed (Zephyr's Momentum):** Speed II (20 seconds).
* **Defence (Aegis Ward):** Resistance II (15 seconds).
* **Combat Boost (Berserker's Resonance):** Strength I (15 seconds).
* **Strength (Aetheric Shielding):** Strength II (10 seconds) - overrides Combat Boost.
* **Crit Attack (Fortune's Aura):** Luck II (20 seconds) - boosts critical strike chance.
* **Prayer Pierce (Veil of Silence):** Haste I + temporarily strips target's active Resistance.

---

#### 📚 All In-Game Spell Books & Tomes Explained

Players never have to guess. Every book and tome functions as both a tactical key and an in-game manual:

1. **b 📜 The Water Book:**
   * Gated for Magic level 10+. Carry to unlock the Water Staff's Downpour Spell.
   * Explains how to channel the Downpour, spawn puddle zones, and leverage the non-refreshing 30s Support operational window to double Support healing and extend buff times.
2. **2 📜 The Earth Book:**
   * Gated for Magic level 10+. Carry to throw heavy blocks from inventory.
   * Details block trap & suffocate chains, block tiers, and the Frictionless Fallback / Smart Downgrading hierarchy.
3. **5 ✦ Spell Combo Book:**
   * Carry in inventory or Magic Bag to activate HUD action-bar combo hints during combat.
   * Written pages document all advanced status chains:
     * `WET → MUDDY → STATUE → ☠ CRUMBLED (Air)`
     * `WET → CHILLED → FROZEN → ☠ SHATTERED (Air)`
     * `SCORCHED → BLAZING → INFERNO BLAST (Air)`
     * `FIRE + FROZEN → THAW EXPLOSION` (Massive AoE steam burst)
     * `WATER + FROZEN → SLUSH` (Slowness III + Blindness 3s)
4. **c ⚠ Ancient Kill Tome:**
   * Carry in inventory to unlock instant-death combo HUD prompts in combat.
   * Details the lethal inputs for **Frozen Shatter** and **Statue Crumble** instant kills.
   * Unlocked exclusively as a rare drop from Double Boss Events.
5. **5 ✦ Arcane Tome:**
   * Open the Favorites GUI (star/unstar combos to customize combat hints) and click "Read Full Tome" to read discovered pages.
   * Discovered pages are unlocked by absorbing **Spell Pages** dropped at 4% from monsters.
6. **5 ✦ Mage Gear Guide:**
   * Gifted to players on their first-ever spell cast.
   * Explains Apprentice (Lv1), Mage (Lv30), Alch (Lv60), and Master (Lv90) gear sets, spell cooldown reduction bonuses, and fanning speed multipliers.

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
