# DifficultyEngine

A Paper Minecraft plugin that lets **each player choose their own personal difficulty level** — completely independent of the server's global difficulty setting.

---

## Commands

| Command | Description |
|---|---|
| `/difficulty` | Shows your current difficulty |
| `/difficulty peaceful` | Switch to Peaceful mode |
| `/difficulty easy` | Switch to Easy mode (default) |
| `/difficulty hard` | Switch to Hard mode |
| `/difficulty nightmare` | Switch to Nightmare mode |

**Aliases:** `/diff`, `/setdifficulty`

---

## Difficulty Levels

### ☮ Peaceful
- Hostile mobs **will not target you**
- Mob stats around you are slightly reduced (0.75× HP & damage)
- If a Nightmare player is in your group, their aggro pulls mobs away from you
- You are protected even in mixed groups — **unless a Nightmare player overrides all aggro**

### ✦ Easy *(Default)*
- **Fully vanilla experience** — no changes to mob stats or behaviour
- All players start here by default
- Your difficulty choice is saved and persists through server restarts

### ⚔ Hard
- Mobs spawning near you get **+25% Max HP**
- **+15% Attack Damage**
- **+5% Movement Speed**
- Extended follow range: **32 blocks** (vanilla is ~16–20)

### ☠ Nightmare
- Mobs spawning near you get **+50% Max HP**
- **+25% Attack Damage**
- **+15% Movement Speed**
- Massive follow range: **64 blocks** — they will not give up
- **Increased spawn rate:** ~3 bonus hostile mobs spawn near you every 30 seconds
- **Higher aggro preference:** Mobs in the area have a **35% chance** to re-lock onto you even if they are already targeting another player
- In groups, you become the primary target — you carry the team's danger

---

## How Mob Scaling Works

When a hostile mob spawns, the plugin:

1. Scans all players within **64 blocks**
2. Finds the **highest difficulty player** in range
3. Applies that difficulty's stat multipliers to the mob

**Example:** One Nightmare player + three Easy players nearby → all mobs that spawn will have Nightmare-tier stats (1.5× HP, 1.25× damage, 1.15× speed, 64-block follow range).

> This means a Nightmare player makes their surrounding area genuinely more dangerous for **everyone nearby** — not just themselves.

---

## Group / Multiplayer Behaviour

| Situation | Result |
|---|---|
| Solo Nightmare player | Full Nightmare buffs + bonus spawns + high aggro |
| Nightmare + Easy group | Mobs scale to Nightmare stats; 35% chance mobs retarget the Nightmare player |
| Nightmare + Peaceful group | Nightmare player absorbs nearly all aggro; Peaceful players are rarely targeted |
| All Easy players | Pure vanilla — no changes |
| All Peaceful players | Mobs do not attack anyone (peaceful protection active for all) |

---

## Nightmare Bonus Spawns

Every **30 seconds**, the plugin spawns **3 extra hostile mobs** within 8–24 blocks of each Nightmare player. Mob types are drawn from a weighted pool:

- Zombie (most common)
- Skeleton
- Creeper
- Spider
- Zombie Villager

Bonus mobs spawn on the surface at valid air locations — they will not spawn inside walls or buildings.

---

## Drops & Loot

This plugin does **not** modify any loot tables, drop rates, or XP values. All mob drops are **100% vanilla**.

---

## Data Persistence

Player difficulty choices are saved to:
```
plugins/DifficultyEngine/player_data.yml
```

Your setting **persists through server restarts and reconnects**. You only need to set it once.

---

## Installation

1. Place `DifficultyEngine-1.0.jar` in your server's `plugins/` folder
2. Restart the server
3. Players use `/difficulty` to choose their mode immediately

---

## Requirements

- **Paper 1.21+**
- **Java 21+**
- No other dependencies required

---

## Stat Reference Table

| Mode | HP Mult | Damage Mult | Speed Mult | Follow Range | Bonus Spawns |
|---|---|---|---|---|---|
| Peaceful | 0.75× | 0.75× | 1.0× | 16 blocks | ✗ |
| Easy | 1.0× | 1.0× | 1.0× | 20 blocks | ✗ |
| Hard | 1.25× | 1.15× | 1.05× | 32 blocks | ✗ |
| Nightmare | 1.50× | 1.25× | 1.15× | 64 blocks | ✓ (+3 every 30s) |

---

*DifficultyEngine — because some players want to suffer more than others.*
